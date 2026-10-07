package com.liuyue.autoTradeCycling.client.manager;

import com.liuyue.autoTradeCycling.common.TradeTargets;
import com.liuyue.autoTradeCycling.common.TradeTargets.EnchantRequirement;
import com.liuyue.autoTradeCycling.common.TradeTargets.TargetEntry;
import com.liuyue.autoTradeCycling.net.SearchResultPayload;
import com.liuyue.autoTradeCycling.net.SearchTradesPayload;
import de.maxhenkel.tradecycling.net.CycleTradesPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AutoTradeManager {

    private static AutoTradeManager instance;

    public enum State { IDLE, WAITING_FOR_SCREEN, SEARCHING, CYCLING, CHECKING, DONE, PLAY_SOUND }
    public enum MatchMode { ALL, ANY }

    /** 服务端不再回传进度（或压根没响应）时的等待上限（tick），超时则放弃。
     *  正常搜索会每 20 tick 回传一次进度，只要服务端还在刷就不会超时。 */
    private static final int SEARCH_TIMEOUT_TICKS = 600;

    private State state = State.IDLE;
    private MatchMode matchMode = MatchMode.ALL;
    private final List<TargetEntry> targets = new ArrayList<>();
    private int tickCounter = 0;
    private int cycleCount = 0;
    private boolean offersUpdated = false;
    private long lastSoundTime = 0;
    private int soundCount = 0;

    public void onOffersUpdated(Minecraft client) {
        this.offersUpdated = true;
        tryTransitionToChecking();
    }

    private void tryTransitionToChecking() {
        if (state == State.CYCLING && offersUpdated) {
            state = State.CHECKING;
            tickCounter = 0;
            offersUpdated = false;
        }
    }

    public static AutoTradeManager getInstance() {
        if (instance == null) instance = new AutoTradeManager();
        return instance;
    }

    public void setMatchMode(MatchMode mode) { this.matchMode = mode; }
    public MatchMode getMatchMode() { return matchMode; }

    public boolean addTarget(ResourceLocation id, List<EnchantRequirement> enchants, int minCount, int maxPrice) {
        var entry = new TargetEntry(id, enchants, minCount, maxPrice);
        if (targets.contains(entry)) return false;
        targets.add(entry);
        return true;
    }

    public int getCycleCount() { return cycleCount; }

    public boolean addEnchantToItem(ResourceLocation itemId, EnchantRequirement req) {
        for (TargetEntry t : targets) {
            if (!t.isEnchantedBook() && t.id().equals(itemId)) {
                t.enchants().add(req);
                return true;
            }
        }
        return false;
    }
    /** 从某个物品目标上移除一条附魔要求。 */
    public boolean removeEnchantFromItem(ResourceLocation itemId, ResourceLocation enchantId) {
        for (TargetEntry t : targets) {
            if (!t.isEnchantedBook() && t.id().equals(itemId)) {
                return t.enchants().removeIf(req -> req.id().equals(enchantId));
            }
        }
        return false;
    }

    public void removeTarget(ResourceLocation id) { targets.removeIf(t -> t.id().equals(id)); }
    public void removeEntry(TargetEntry entry) { targets.remove(entry); }
    public void clearTargets() { targets.clear(); }
    public List<TargetEntry> getTargets() { return targets; }

    /** 查找某个物品（非附魔书）的目标。 */
    public TargetEntry findItemTarget(ResourceLocation itemId) {
        for (TargetEntry t : targets) {
            if (!t.isEnchantedBook() && t.id().equals(itemId)) return t;
        }
        return null;
    }

    /** 查找包含指定附魔的附魔书目标（本 mod 创建的附魔书目标每条只带一个附魔）。 */
    public TargetEntry findBookTarget(ResourceLocation enchantId) {
        for (TargetEntry t : targets) {
            if (!t.isEnchantedBook()) continue;
            for (EnchantRequirement req : t.enchants()) {
                if (req.id().equals(enchantId)) return t;
            }
        }
        return null;
    }

    /**
     * 原地更新某条目标的数量/价格（保持列表顺序，不产生重复项）。
     * {@link TargetEntry} 是不可变记录，只能整条替换；替换时复用原来的 enchants 列表实例，
     * 界面上持有的旧引用仍能通过 {@link #sameEntry} 定位到新记录。
     */
    public boolean updateEntry(TargetEntry entry, int minCount, int maxPrice) {
        for (int i = 0; i < targets.size(); i++) {
            TargetEntry current = targets.get(i);
            if (!sameEntry(current, entry)) continue;
            if (current.minCount() == minCount && current.maxPrice() == maxPrice) return false;
            targets.set(i, new TargetEntry(current.id(), current.enchants(), minCount, maxPrice));
            return true;
        }
        return false;
    }

    /** 调整某条目标上一条附魔要求的等级（原地替换列表元素，不换列表实例）。 */
    public boolean setEnchantLevel(TargetEntry entry, ResourceLocation enchantId, int minLevel) {
        for (TargetEntry current : targets) {
            if (!sameEntry(current, entry)) continue;
            for (int i = 0; i < current.enchants().size(); i++) {
                if (!current.enchants().get(i).id().equals(enchantId)) continue;
                if (current.enchants().get(i).minLevel() == minLevel) return false;
                current.enchants().set(i, new EnchantRequirement(enchantId, minLevel));
                return true;
            }
            return false;
        }
        return false;
    }

    /** 从某条目标（物品或附魔书）上移除一条附魔要求。 */
    public boolean removeEnchant(TargetEntry entry, ResourceLocation enchantId) {
        for (TargetEntry current : targets) {
            if (!sameEntry(current, entry)) continue;
            return current.enchants().removeIf(req -> req.id().equals(enchantId));
        }
        return false;
    }

    /** 拿某条目标的最新记录；{@link #updateEntry} 等替换记录后旧引用会过期，用它重新定位。 */
    public TargetEntry latest(TargetEntry entry) {
        for (TargetEntry current : targets) {
            if (sameEntry(current, entry)) return current;
        }
        return null;
    }

    /**
     * 界面用来识别“同一条目标”：引用相等，或 id 相同且 enchants 列表是同一实例。
     * 界面上的卡片在 {@link #updateEntry} 等替换记录后仍持有旧实例，
     * 靠列表实例不变这一约定重新定位到最新记录。
     */
    private static boolean sameEntry(TargetEntry current, TargetEntry entry) {
        return current == entry || (current.id().equals(entry.id()) && current.enchants() == entry.enchants());
    }

    public void start() {
        if (targets.isEmpty()) return;
        excludeImpossibleTargets();
        if (targets.isEmpty()) return;
        this.state = State.WAITING_FOR_SCREEN;
        this.tickCounter = 0;
        this.cycleCount = 0;
    }

    /**
     * 开始前剔除"永远刷不出来"的目标：附魔村民根本不卖、要求等级超过附魔上限、
     * 或者该附魔无法附到目标物品上。这些目标会让 ALL 模式永远匹配不上。
     */
    private void excludeImpossibleTargets() {
        Minecraft client = Minecraft.getInstance();
        for (TargetEntry entry : new ArrayList<>(targets)) {
            String reason = impossibilityReason(entry);
            if (reason == null) continue;
            targets.remove(entry);
            chat(client, "§c已排除不可能刷出的目标: §f" + targetDisplayName(entry) + " §7(" + reason + ")");
        }
    }

    private String impossibilityReason(TargetEntry entry) {
        boolean book = entry.isEnchantedBook();
        for (EnchantRequirement req : entry.enchants()) {
            int maxLevel = VillagerTradeData.enchantMaxLevel(req.id());
            if (maxLevel > 0 && req.minLevel() > maxLevel) return "该附魔最高只有 " + maxLevel + " 级";
            if (book) {
                // 附魔书走 TRADEABLE
                if (!VillagerTradeData.enchantInBooks(req.id())) return "村民的附魔书不出售该附魔";
            } else {
                // 附魔装备走 ON_TRADED_EQUIPMENT，比附魔书的范围窄得多
                if (!VillagerTradeData.canApplyTo(req.id(), entry.id())) return "该附魔无法附在此物品上";
                if (!VillagerTradeData.enchantOnTradedEquipment(req.id())) return "村民卖的附魔装备不会带该附魔";
            }
        }
        return null;
    }

    public void cancel() {
        boolean wasActive = isActive();
        this.state = State.IDLE;
        this.cycleCount = 0;
        if (wasActive) chat(Minecraft.getInstance(), "§c已停止自动刷新。");
    }
    public boolean isActive() { return state != State.IDLE && state != State.DONE; }
    public State getState() { return state; }

    public void onClientTick(Minecraft client) {
        if (state == State.IDLE || state == State.DONE) return;
        tickCounter++;
        switch (state) {
            case WAITING_FOR_SCREEN -> tickWaitingForScreen(client);
            case SEARCHING          -> tickSearching(client);
            case CYCLING            -> tickCycling(client);
            case CHECKING           -> tickChecking(client);
            case PLAY_SOUND         -> tickPlaySound(client);
            default                 -> {}
        }
    }

    private void tickWaitingForScreen(Minecraft client) {
        if (tickCounter % 40 == 1)
            chat(client, "§e请右键打开 1 级村民的交易界面...");

        if (client.screen instanceof MerchantScreen) {
            MerchantMenu menu = getMerchantMenu(client);
            if (menu != null && !menu.getOffers().isEmpty()) {
                if (menu.getTraderLevel() != 1) {
                    chat(client, "§c村民不是 1 级（当前 " + menu.getTraderLevel() + " 级），请用未交易过的 1 级村民！");
                    state = State.DONE;
                    return;
                }
                if (menu.getTraderXp() > 0) {
                    chat(client, "§c该村民已被交易过，请用未交易过的 1 级村民！");
                    state = State.DONE;
                    return;
                }
                chat(client, "§a开始自动刷新（1级村民），目标: §e" + formatTargetNames());
                tickCounter = 0;

                // 服务端装了本 mod 时让它在服务端连续重掷，直到命中或玩家手动停止（关界面/开配置界面），
                // 省掉逐轮网络往返。
                // 装了 VT 也走这条路：服务端用 VT 的合并列表（1 级 + 2-5 级锁定交易）做匹配，
                // 结束时也发同一份合并列表，与 VT 自己 hook openTradingScreen 的行为一致。
                if (ClientPlayNetworking.canSend(SearchTradesPayload.TYPE)) {
                    chat(client, "§7服务端支持批量搜索，正在刷新（找到目标前不会停，可用 G 键打开配置界面点停止）...");
                    state = State.SEARCHING;
                    ClientPlayNetworking.send(new SearchTradesPayload(new ArrayList<>(targets),
                            matchMode == MatchMode.ANY));
                } else {
                    state = State.CYCLING;
                    doCycling();
                }
            }
        }
    }

    private void tickSearching(Minecraft client) {
        if (!(client.screen instanceof MerchantScreen)) {
            finish(client, "§c交易界面已关闭，已停止搜索。");
            return;
        }
        if (tickCounter > SEARCH_TIMEOUT_TICKS) {
            finish(client, "§e服务端一直没有回传搜索进度，已停止。可再次点击开始。");
        }
    }

    /** 服务端批量搜索的状态回调：进度、结果、拒绝都从这里进来。 */
    public void onSearchResult(SearchResultPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (state != State.SEARCHING) return;
        // 计数会显示在交易界面的 "交易 (N)" 标题上，进度回传时就是靠它涨的
        cycleCount = payload.attempts();

        int status = payload.status();
        if (status == SearchResultPayload.STATUS_PROGRESS) {
            // 服务端还在刷，重置超时计时；搜索会一直进行到命中或玩家停止
            tickCounter = 0;
            return;
        }
        if (status == SearchResultPayload.STATUS_REJECTED) {
            finish(client, "§c服务端拒绝了搜索：请确认打开的是未交易过的 1 级村民的交易界面。");
            return;
        }

        chat(client, "§a" + modePrefix() + "目标已出现！共刷新 " + payload.attempts() + " 次");
        reportMatches(client, payload.matched());
        state = State.PLAY_SOUND;
        soundCount = 0;
        lastSoundTime = 0;
    }

    private void tickPlaySound(Minecraft client) {
        long now = System.currentTimeMillis();
        if (soundCount == 0 || now - lastSoundTime >= 200) {
            client.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.ARROW_HIT_PLAYER, 1.0F));
            lastSoundTime = now;
            soundCount++;
        }
        if (soundCount >= 3) {
            state = State.DONE;
            cycleCount = 0;
        }
    }

    private void tickCycling(Minecraft client) {
        if (!(client.screen instanceof MerchantScreen)) {
            finish(client, "§c交易界面已关闭，自动刷新停止。");
            return;
        }
        if (tickCounter > 20) {
            chat(client, "§7重试刷新...");
            doCycling();
            offersUpdated = false;
            tickCounter = 0;
        }
    }

    private void tickChecking(Minecraft client) {
        MerchantMenu menu = getMerchantMenu(client);
        if (menu == null) { finish(client, "§c交易界面已关闭。"); return; }

        // menu.getOffers() 同时含已解锁与被等级锁定的交易，一次匹配即可覆盖两者
        List<Integer> matched = TradeTargets.matchIndices(menu.getOffers(), targets);
        if (TradeTargets.isMatch(matched, targets.size(), matchMode == MatchMode.ANY)) {
            chat(client, "§a" + modePrefix() + "目标已出现！共刷新 " + cycleCount + " 次");
            reportMatches(client, matched);
            state = State.PLAY_SOUND;
            soundCount = 0;
            lastSoundTime = 0;
            return;
        }

        cycleCount++;
        doCycling();
        offersUpdated = false;
        state = State.CYCLING;
    }

    private String modePrefix() {
        return matchMode == MatchMode.ANY ? "[任一模式] " : "";
    }

    /** 逐条播报命中的目标及其成交价。 */
    private void reportMatches(Minecraft client, List<Integer> matchedIndices) {
        MerchantMenu menu = getMerchantMenu(client);
        if (menu == null) return;

        MerchantOffers currentOffers = VisibleTradersCompat.getUnlockedOffers(menu);
        MerchantOffers allOffers = menu.getOffers();
        for (int index : matchedIndices) {
            if (index < 0 || index >= targets.size()) continue;
            TargetEntry target = targets.get(index);
            String label = target.isEnchantedBook() ? "[附魔]" : "";
            String where = containsMatch(currentOffers, target) ? "当前交易(1级)" : "升级后交易(2-5级)";
            chat(client, "§a  §f" + label + targetDisplayName(target) + " §7- " + where);
            for (MerchantOffer offer : findAllMatchingOffers(allOffers, target)) {
                ItemStack costA = offer.getBaseCostA();
                ItemStack costB = offer.getCostB();
                String costStr = costA.getCount() + " " + costA.getHoverName().getString();
                if (!costB.isEmpty())
                    costStr += " + " + costB.getCount() + " " + costB.getHoverName().getString();
                chat(client, "§a    §7-> " + offer.getResult().getCount() + " 个 价格: " + costStr);
            }
        }
    }

    private static boolean containsMatch(MerchantOffers offers, TargetEntry target) {
        for (MerchantOffer offer : offers) {
            if (TradeTargets.matches(offer, target)) return true;
        }
        return false;
    }

    private List<MerchantOffer> findAllMatchingOffers(MerchantOffers offers, TargetEntry target) {
        List<MerchantOffer> result = new ArrayList<>();
        for (MerchantOffer offer : offers) {
            if (TradeTargets.matches(offer, target)) result.add(offer);
        }
        return result;
    }

    private void doCycling() {
        ClientPlayNetworking.send(new CycleTradesPacket());
    }

    private MerchantMenu getMerchantMenu(Minecraft client) {
        if (client.screen instanceof MerchantScreen screen) return screen.getMenu();
        return null;
    }

    private void finish(Minecraft client, String msg) { state = State.DONE; cycleCount = 0; chat(client, msg); }

    private void chat(Minecraft client, String msg) {
        if (client.player != null) client.player.displayClientMessage(Component.literal(msg), false);
    }

    private String formatTargetNames() {
        return targets.stream()
                .map(t -> targetDisplayName(t) + "(" + t.minCount() + "/" + t.maxPrice() + ")")
                .collect(Collectors.joining(", "));
    }

    private String targetDisplayName(TargetEntry target) {
        if (target.isEnchantedBook()) {
            Enchantment enchantment = getEnchantment(target.id());
            return enchantment != null ? enchantment.description().getString() : target.id().getPath();
        }
        var item = BuiltInRegistries.ITEM.get(target.id()).map(net.minecraft.core.Holder.Reference::value).orElse(null);
        return item != null ? item.getName(new ItemStack(item)).getString() : target.id().toString();
    }

    private static Enchantment getEnchantment(ResourceLocation id) {
        var client = Minecraft.getInstance();
        if (client.level == null) return null;
        var registry = client.level.registryAccess().lookupOrThrow(
                net.minecraft.core.registries.Registries.ENCHANTMENT);
        return registry.get(ResourceKey.create(
                net.minecraft.core.registries.Registries.ENCHANTMENT, id))
                .map(net.minecraft.core.Holder.Reference::value).orElse(null);
    }
}
