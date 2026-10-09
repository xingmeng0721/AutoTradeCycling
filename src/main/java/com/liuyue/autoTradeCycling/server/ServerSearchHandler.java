package com.liuyue.autoTradeCycling.server;

import com.liuyue.autoTradeCycling.common.SearchSpeed;
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
 * 服务端批量搜索：在收到请求后连续重掷村民交易，直到命中目标或玩家手动停止。
 * 重掷分 tick 进行，每 tick 刷多少次由客户端选的 {@link SearchSpeed} 决定。
 */
public final class ServerSearchHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("auto-trade-cycling");

    private static final int PROGRESS_INTERVAL_TICKS = 20;

    private static final Map<UUID, Search> ACTIVE = new HashMap<>();

    private ServerSearchHandler() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(SearchTradesPayload.TYPE, (payload, context) ->
                context.server().execute(() -> start(context.player(), payload)));
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
    }

    private static void start(ServerPlayer player, SearchTradesPayload payload) {
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
        if (villager.getVillagerData().level() != 1) { //#replace < 1.21.5 ? if (villager.getVillagerData().getLevel() != 1) {
            reject(player);
            return;
        }
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

        MerchantOffers visible = candidateOffers(villager);
        LOGGER.info("批量搜索开始: 档位 {}，候选人报价 {} 条 ({})",
                payload.speed().label(), visible.size(), describeOffers(visible));

        ACTIVE.put(player.getUUID(), new Search(player, villager, villagerAccessor, menu,
                menuAccessor.getTradeContainer(), targets, payload.matchAny(), payload.speed()));
    }

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

    private static void tick() {
        if (ACTIVE.isEmpty()) return;
        Iterator<Map.Entry<UUID, Search>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Search search = iterator.next().getValue();
            if (search.player.isRemoved() || search.player.containerMenu != search.menu) {
                iterator.remove();
                continue;
            }
            if (step(search)) {
                iterator.remove();
                continue;
            }
            if (++search.ticksSinceReport >= PROGRESS_INTERVAL_TICKS) {
                search.ticksSinceReport = 0;
                reply(search.player,
                        new SearchResultPayload(SearchResultPayload.STATUS_PROGRESS, search.attempts, List.of()));
            }
        }
    }

    private static boolean step(Search search) {
        SearchSpeed speed = search.speed;
        long deadline = speed.unlimitedTime() ? 0L : System.nanoTime() + speed.timeBudgetNanos();
        for (int i = 0; i < speed.maxAttemptsPerTick(); i++) {
            search.attempts++;
            reroll(search);
            MerchantOffers offers = candidateOffers(search.villager);
            List<Integer> matched = TradeTargets.matchIndices(offers, search.targets);
            if (TradeTargets.isMatch(matched, search.targets.size(), search.matchAny)) {
                finish(search, matched, offers);
                return true;
            }
            if (deadline != 0L && System.nanoTime() >= deadline) break;
        }
        return false;
    }

    private static MerchantOffers candidateOffers(Villager villager) {
        return VisibleTradersServer.combinedOffers(villager);
    }

    private static void reroll(Search search) {
        Villager villager = search.villager;
        villager.setOffers(null);
        villager.getOffers();
        search.villagerAccessor.invokeUpdateSpecialPrices(search.player);
        villager.setTradingPlayer(search.player);
        VisibleTradersServer.regenerateTrades(villager);
    }

    private static void finish(Search search, List<Integer> matched, MerchantOffers offers) {
        Villager villager = search.villager;
        search.player.sendMerchantOffers(search.menu.containerId, offers,
                VisibleTradersServer.shiftedLevel(villager), villager.getVillagerXp(),
                villager.showProgressBar(), villager.canRestock());
        search.menu.slotsChanged(search.container);

        reply(search.player, new SearchResultPayload(SearchResultPayload.STATUS_FOUND, search.attempts, matched));
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
        final SearchSpeed speed;
        int attempts;
        int ticksSinceReport;

        Search(ServerPlayer player, Villager villager, VillagerAccessor villagerAccessor, MerchantMenu menu,
               MerchantContainer container, List<TargetEntry> targets, boolean matchAny, SearchSpeed speed) {
            this.player = player;
            this.villager = villager;
            this.villagerAccessor = villagerAccessor;
            this.menu = menu;
            this.container = container;
            this.targets = targets;
            this.matchAny = matchAny;
            this.speed = speed;
        }
    }
}
