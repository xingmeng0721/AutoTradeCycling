package com.liuyue.autoTradeCycling.server;

import com.liuyue.autoTradeCycling.common.TradeTargets;
import com.liuyue.autoTradeCycling.common.TradeTargets.TargetEntry;
import com.liuyue.autoTradeCycling.mixin.MerchantMenuAccessor;
import com.liuyue.autoTradeCycling.mixin.VillagerAccessor;
import com.liuyue.autoTradeCycling.net.SearchResultPayload;
import com.liuyue.autoTradeCycling.net.SearchTradesPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 服务端批量搜索：在收到请求后连续重掷村民交易，直到命中或达到上限。
 * 相比"客户端每刷新一次发一个包"，省掉了每次的网络往返，是主要提速来源。
 *
 * <p>重掷分 tick 进行（每 tick {@link #ATTEMPTS_PER_TICK} 次），避免一次性长时间占用主线程。
 * 村民的准入条件与 Trade Cycling 保持一致，保证结果合法：必须在交易界面里、
 * 是未交易过的村民、且已绑定工作站。
 */
public final class ServerSearchHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("auto-trade-cycling");

    /** 每 tick 给单个搜索的时间预算，按实际耗时自适应，机器快就刷得多、慢就刷得少，
     *  既不会把 tick 占满，也不用猜"每 tick 多少次"这个数。 */
    private static final long TICK_TIME_BUDGET_NANOS = 10_000_000L;
    /** 每 tick 的硬上限，防止极快的机器上单次请求把 tick 拉长。 */
    private static final int MAX_ATTEMPTS_PER_TICK = 400;
    /** 单次请求的总尝试上限。 */
    private static final int MAX_TOTAL_ATTEMPTS = 20000;
    /** 每隔多少 tick 回传一次进度，让客户端能看到刷新计数在涨。 */
    private static final int PROGRESS_INTERVAL_TICKS = 20;

    private static final Map<UUID, Search> ACTIVE = new HashMap<>();

    private ServerSearchHandler() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(SearchTradesPayload.TYPE, (payload, context) ->
                context.server().execute(() -> start(context.player(), payload)));
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
    }

    // ---------------------------------------------------------------- 请求

    private static void start(ServerPlayer player, SearchTradesPayload payload) {
        // 同一玩家同时只保留一个搜索，新请求顶掉旧的
        ACTIVE.remove(player.getUUID());

        AbstractContainerMenu containerMenu = player.containerMenu;
        if (!(containerMenu instanceof MerchantMenuAccessor menuAccessor)
                || !(containerMenu instanceof MerchantMenu menu)) {
            reject(player);
            return;
        }
        Merchant trader = menuAccessor.getTrader();
        if (!(trader instanceof Villager villager) || !(trader instanceof VillagerAccessor villagerAccessor)) {
            reject(player);
            return;
        }
        // 已交易过的村民交易不会重掷，官方 Trade Cycling 也是这么拦的
        if (villager.getVillagerXp() > 0) {
            reject(player);
            return;
        }
        if (villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty()) {
            reject(player);
            return;
        }
        List<TargetEntry> targets = payload.targets();
        if (targets.isEmpty()) {
            reject(player);
            return;
        }

        int maxAttempts = Math.max(1, Math.min(payload.maxAttempts(), MAX_TOTAL_ATTEMPTS));

        // 记一笔本轮的候选人报价，便于事后排查（装了 VT 时这里是含 2-5 级的合并列表）
        MerchantOffers visible = candidateOffers(villager);
        LOGGER.info("批量搜索开始: 候选人报价 {} 条 ({})", visible.size(), describeOffers(visible));

        ACTIVE.put(player.getUUID(), new Search(player, villager, villagerAccessor, menu,
                menuAccessor.getTradeContainer(), targets, payload.matchAny(), maxAttempts));
    }

    /** 取前若干条报价的结果物品，用于诊断输出。 */
    private static String describeOffers(MerchantOffers offers) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (MerchantOffer offer : offers) {
            ids.add(BuiltInRegistries.ITEM.getKey(offer.getResult().getItem()));
            if (ids.size() >= 8) break;
        }
        StringBuilder builder = new StringBuilder();
        for (ResourceLocation id : ids) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(id.getPath());
        }
        return builder.toString();
    }

    // ---------------------------------------------------------------- 分 tick 推进

    private static void tick() {
        if (ACTIVE.isEmpty()) return;
        Iterator<Map.Entry<UUID, Search>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Search search = iterator.next().getValue();
            // 玩家关掉了交易界面就静默停止
            if (search.player.isRemoved() || search.player.containerMenu != search.menu) {
                iterator.remove();
                continue;
            }
            if (step(search)) {
                iterator.remove();
                continue;
            }
            // 搜索还在进行，定期把已尝试次数回传给客户端
            if (++search.ticksSinceReport >= PROGRESS_INTERVAL_TICKS) {
                search.ticksSinceReport = 0;
                reply(search.player,
                        new SearchResultPayload(SearchResultPayload.STATUS_PROGRESS, search.attempts, List.of()));
            }
        }
    }

    /** 推进一轮，返回 true 表示搜索已结束。 */
    private static boolean step(Search search) {
        int budget = Math.min(MAX_ATTEMPTS_PER_TICK, search.maxAttempts - search.attempts);
        long deadline = System.nanoTime() + TICK_TIME_BUDGET_NANOS;
        for (int i = 0; i < budget; i++) {
            search.attempts++;
            reroll(search);
            List<Integer> matched = TradeTargets.matchIndices(candidateOffers(search.villager), search.targets);
            if (TradeTargets.isMatch(matched, search.targets.size(), search.matchAny)) {
                finish(search, matched);
                return true;
            }
            if (System.nanoTime() >= deadline) break;
        }
        if (search.attempts >= search.maxAttempts) {
            finish(search, List.of());
            return true;
        }
        return false;
    }

    /** 本轮的候选人报价：VT 的合并列表（1 级 + 2-5 级锁定交易）。
     *  注意不能用 villager.getOffers()——VT 把 2-5 级从那里摘走了，只剩当前等级的 2 条。 */
    private static MerchantOffers candidateOffers(Villager villager) {
        return VisibleTradersServer.combinedOffers(villager);
    }

    /** 与 Trade Cycling 单轮刷新等价的一步：重掷报价、重算折扣，并重建 VT 的分级交易。
     *  VT 的重建必须放在这里，不能挪到 finish()，否则参与匹配的和最后发出去的就不是同一份。 */
    private static void reroll(Search search) {
        Villager villager = search.villager;
        villager.setOffers(null);
        villager.getOffers();
        search.villagerAccessor.invokeUpdateSpecialPrices(search.player);
        villager.setTradingPlayer(search.player);
        VisibleTradersServer.regenerateTrades(villager);
    }

    /** 把最终报价同步给客户端，并回传搜索结果。 */
    private static void finish(Search search, List<Integer> matched) {
        Villager villager = search.villager;
        // 必须发合并列表：VT 只 hook 了 openTradingScreen，我们直接调 sendMerchantOffers 绕过了它
        search.player.sendMerchantOffers(search.menu.containerId, candidateOffers(villager),
                villager.getVillagerData().level(), villager.getVillagerXp(),
                villager.showProgressBar(), villager.canRestock());
        search.menu.slotsChanged(search.container);

        int status = matched.isEmpty() ? SearchResultPayload.STATUS_NOT_FOUND : SearchResultPayload.STATUS_FOUND;
        reply(search.player, new SearchResultPayload(status, search.attempts, matched));
    }

    private static void reject(ServerPlayer player) {
        reply(player, new SearchResultPayload(SearchResultPayload.STATUS_REJECTED, 0, List.of()));
    }

    private static void reply(ServerPlayer player, SearchResultPayload payload) {
        if (!ServerPlayNetworking.canSend(player, SearchResultPayload.TYPE)) return;
        ServerPlayNetworking.send(player, payload);
    }

    private static final class Search {
        final ServerPlayer player;
        final Villager villager;
        final VillagerAccessor villagerAccessor;
        final MerchantMenu menu;
        final MerchantContainer container;
        final List<TargetEntry> targets;
        final boolean matchAny;
        final int maxAttempts;
        int attempts;
        int ticksSinceReport;

        Search(ServerPlayer player, Villager villager, VillagerAccessor villagerAccessor, MerchantMenu menu,
               MerchantContainer container, List<TargetEntry> targets, boolean matchAny, int maxAttempts) {
            this.player = player;
            this.villager = villager;
            this.villagerAccessor = villagerAccessor;
            this.menu = menu;
            this.container = container;
            this.targets = targets;
            this.matchAny = matchAny;
            this.maxAttempts = maxAttempts;
        }
    }
}