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

    /** 单次服务端批量搜索的最大尝试次数（服务端另有硬上限）。 */
    private static final int SERVER_SEARCH_ATTEMPTS = 20000;
    /** 等待服务端搜索结果的上限（tick），超时则放弃。 */
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

    public void cancel() { this.state = State.IDLE; this.cycleCount = 0; }
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

                // 服务端装了本 mod 时让它在服务端连续重掷，省掉逐轮网络往返。
                // 装了 VT 也走这条路：服务端用 VT 的合并列表（1 级 + 2-5 级锁定交易）做匹配，
                // 结束时也发同一份合并列表，与 VT 自己 hook openTradingScreen 的行为一致。
                if (ClientPlayNetworking.canSend(SearchTradesPayload.TYPE)) {
                    chat(client, "§7服务端支持批量搜索，正在刷新...");
                    state = State.SEARCHING;
                    ClientPlayNetworking.send(new SearchTradesPayload(new ArrayList<>(targets),
                            matchMode == MatchMode.ANY, SERVER_SEARCH_ATTEMPTS));
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
            finish(client, "§e等待服务端搜索结果超时，可再次点击开始。");
        }
    }

    /** 服务端批量搜索的状态回调：进度、结果、拒绝都从这里进来。 */
    public void onSearchResult(SearchResultPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (state != State.SEARCHING) return;
        // 计数会显示在交易界面的 "交易 (N)" 标题上，进度回传时就是靠它涨的
        cycleCount = payload.attempts();

        int status = payload.status();
        if (status == SearchResultPayload.STATUS_PROGRESS) return;
        if (status == SearchResultPayload.STATUS_REJECTED) {
            finish(client, "§c服务端拒绝了搜索：请确认打开的是未交易过的 1 级村民的交易界面。");
            return;
        }
        if (status == SearchResultPayload.STATUS_NOT_FOUND) {
            finish(client, "§e已刷新 " + payload.attempts() + " 次仍未找到目标，可再次点击开始。");
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
