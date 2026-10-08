package com.liuyue.autoTradeCycling.client.manager;

import com.liuyue.autoTradeCycling.client.ClientScreens;
import com.liuyue.autoTradeCycling.common.SearchSpeed;
import com.liuyue.autoTradeCycling.common.TradeTargets;
import com.liuyue.autoTradeCycling.common.TradeTargets.EnchantRequirement;
import com.liuyue.autoTradeCycling.common.TradeTargets.TargetEntry;
import com.liuyue.autoTradeCycling.mixin.MerchantMenuAccessor;
import com.liuyue.autoTradeCycling.net.SearchResultPayload;
import com.liuyue.autoTradeCycling.net.SearchTradesPayload;
import de.maxhenkel.tradecycling.net.CycleTradesPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 客户端自动刷新的状态机：维护目标列表与刷新档位，负责与服务端批量搜索交互并播报结果。
 */
public class AutoTradeManager {

    private static AutoTradeManager instance;

    public enum State { IDLE, WAITING_FOR_SCREEN, SEARCHING, CYCLING, CHECKING, DONE, PLAY_SOUND }
    public enum MatchMode { ALL, ANY }

    private static final int SEARCH_TIMEOUT_TICKS = 600;

    private State state = State.IDLE;
    private MatchMode matchMode = MatchMode.ALL;
    private SearchSpeed searchSpeed = SearchSpeed.DEFAULT_SPEED;
    private final List<TargetEntry> targets = new ArrayList<>();
    private List<TargetEntry> searchTargets = List.of();
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

    public void setSearchSpeed(SearchSpeed speed) {
        if (this.searchSpeed == speed) return;
        this.searchSpeed = speed;
        TargetStore.markDirty();
    }
    public SearchSpeed getSearchSpeed() { return searchSpeed; }

    public boolean addTarget(Identifier id, List<EnchantRequirement> enchants, int minCount, int maxPrice) {
        if (TradeTargets.isEnchantedBookId(id)) {
            for (EnchantRequirement requirement : enchants) {
                if (findBookTarget(requirement.id()) != null) return false;
            }
        }
        var entry = new TargetEntry(id, new ArrayList<>(enchants), minCount, maxPrice);
        if (targets.contains(entry)) return false;
        targets.add(entry);
        TargetStore.markDirty();
        return true;
    }

    public int getCycleCount() { return cycleCount; }

    public boolean addEnchantToItem(Identifier itemId, EnchantRequirement req) {
        for (TargetEntry t : targets) {
            if (!t.isEnchantedBook() && t.id().equals(itemId)) {
                t.enchants().add(req);
                TargetStore.markDirty();
                return true;
            }
        }
        return false;
    }

    public int removeTarget(Identifier id) {
        int before = targets.size();
        targets.removeIf(t -> !t.isEnchantedBook() && t.id().equals(id));
        if (targets.size() != before) TargetStore.markDirty();
        return before - targets.size();
    }

    public int removeBookTarget(Identifier enchantId) {
        int before = targets.size();
        targets.removeIf(t -> t.isEnchantedBook()
                && t.enchants().stream().anyMatch(req -> req.id().equals(enchantId)));
        if (targets.size() != before) TargetStore.markDirty();
        return before - targets.size();
    }
    public void removeEntry(TargetEntry entry) {
        if (targets.remove(entry)) TargetStore.markDirty();
    }
    public void clearTargets() {
        if (!targets.isEmpty()) TargetStore.markDirty();
        targets.clear();
    }
    public List<TargetEntry> getTargets() { return targets; }

    public void restoreTargets(List<TargetEntry> restored) {
        targets.clear();
        for (TargetEntry entry : restored) {
            targets.add(new TargetEntry(entry.id(), new ArrayList<>(entry.enchants()),
                    entry.minCount(), entry.maxPrice()));
        }
    }

    public TargetEntry findItemTarget(Identifier itemId) {
        for (TargetEntry t : targets) {
            if (!t.isEnchantedBook() && t.id().equals(itemId)) return t;
        }
        return null;
    }

    public TargetEntry findBookTarget(Identifier enchantId) {
        for (TargetEntry t : targets) {
            if (!t.isEnchantedBook()) continue;
            for (EnchantRequirement req : t.enchants()) {
                if (req.id().equals(enchantId)) return t;
            }
        }
        return null;
    }

    public boolean updateEntry(TargetEntry entry, int minCount, int maxPrice) {
        for (int i = 0; i < targets.size(); i++) {
            TargetEntry current = targets.get(i);
            if (!sameEntry(current, entry)) continue;
            if (current.minCount() == minCount && current.maxPrice() == maxPrice) return false;
            targets.set(i, new TargetEntry(current.id(), current.enchants(), minCount, maxPrice));
            TargetStore.markDirty();
            return true;
        }
        return false;
    }

    public boolean setEnchantLevel(TargetEntry entry, Identifier enchantId, int minLevel) {
        for (TargetEntry current : targets) {
            if (!sameEntry(current, entry)) continue;
            for (int i = 0; i < current.enchants().size(); i++) {
                if (!current.enchants().get(i).id().equals(enchantId)) continue;
                if (current.enchants().get(i).minLevel() == minLevel) return false;
                current.enchants().set(i, new EnchantRequirement(enchantId, minLevel));
                TargetStore.markDirty();
                return true;
            }
            return false;
        }
        return false;
    }

    public boolean removeEnchant(TargetEntry entry, Identifier enchantId) {
        for (int i = 0; i < targets.size(); i++) {
            TargetEntry current = targets.get(i);
            if (!sameEntry(current, entry)) continue;
            boolean removed = current.enchants().removeIf(req -> req.id().equals(enchantId));
            if (removed && current.isEnchantedBook() && current.enchants().isEmpty()) {
                targets.remove(i);
            }
            if (removed) TargetStore.markDirty();
            return removed;
        }
        return false;
    }

    public TargetEntry latest(TargetEntry entry) {
        for (TargetEntry current : targets) {
            if (sameEntry(current, entry)) return current;
        }
        return null;
    }

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

    private void excludeImpossibleTargets() {
        Minecraft client = Minecraft.getInstance();
        for (TargetEntry entry : new ArrayList<>(targets)) {
            String reason = impossibilityReason(entry);
            if (reason == null) continue;
            targets.remove(entry);
            TargetStore.markDirty();
            chat(client, "§c已排除不可能刷出的目标: §f" + targetDisplayName(entry) + " §7(" + reason + ")");
        }
    }

    private String impossibilityReason(TargetEntry entry) {
        boolean book = entry.isEnchantedBook();
        for (EnchantRequirement req : entry.enchants()) {
            int maxLevel = VillagerTradeData.enchantMaxLevel(req.id());
            if (maxLevel > 0 && req.minLevel() > maxLevel) return "该附魔最高只有 " + maxLevel + " 级";
            if (book) {
                if (!VillagerTradeData.enchantInBooks(req.id())) return "村民的附魔书不出售该附魔";
            } else {
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

            if (ClientPlayNetworking.canSend(SearchTradesPayload.TYPE)) {
                chat(client, "§7服务端支持批量搜索，正在刷新（档位: §e" + searchSpeed.label()
                        + "§7，找到目标前不会停，可用 G 键打开配置界面点停止）...");
                state = State.SEARCHING;
                searchTargets = new ArrayList<>(targets);
                ClientPlayNetworking.send(new SearchTradesPayload(searchTargets,
                        matchMode == MatchMode.ANY, searchSpeed));
            } else {
                state = State.CYCLING;
                doCycling();
            }
        }
    }

    private void tickSearching(Minecraft client) {
        if (getMerchantMenu(client) == null) {
            finish(client, "§c交易界面已关闭，已停止搜索。");
            return;
        }
        if (tickCounter > SEARCH_TIMEOUT_TICKS) {
            finish(client, "§e服务端一直没有回传搜索进度，已停止。可再次点击开始。");
        }
    }

    public void onSearchResult(SearchResultPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (state != State.SEARCHING) return;
        cycleCount = payload.attempts();

        int status = payload.status();
        if (status == SearchResultPayload.STATUS_PROGRESS) {
            tickCounter = 0;
            return;
        }
        if (status == SearchResultPayload.STATUS_REJECTED) {
            finish(client, "§c服务端拒绝了搜索：请确认打开的是未交易过的 1 级村民的交易界面。");
            return;
        }

        chat(client, "§a" + modePrefix() + "目标已出现！共刷新 " + payload.attempts() + " 次");
        reportMatches(client, payload.matched(), searchTargets);
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
        if (getMerchantMenu(client) == null) {
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

        List<Integer> matched = TradeTargets.matchIndices(menu.getOffers(), targets);
        if (TradeTargets.isMatch(matched, targets.size(), matchMode == MatchMode.ANY)) {
            chat(client, "§a" + modePrefix() + "目标已出现！共刷新 " + cycleCount + " 次");
            reportMatches(client, matched, targets);
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

    private void reportMatches(Minecraft client, List<Integer> matchedIndices, List<TargetEntry> pool) {
        MerchantMenu menu = getMerchantMenu(client);
        if (menu == null) return;

        MerchantMenuAccessor accessor = (MerchantMenuAccessor) menu;
        MerchantOffers currentOffers = accessor.getTrader().getOffers();
        MerchantOffers allOffers = menu.getOffers();
        for (int index : matchedIndices) {
            if (index < 0 || index >= pool.size()) continue;
            TargetEntry target = pool.get(index);
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
        if (ClientScreens.current() instanceof MerchantScreen screen) return screen.getMenu();
        return null;
    }

    private void finish(Minecraft client, String msg) { state = State.DONE; cycleCount = 0; chat(client, msg); }

    private void chat(Minecraft client, String msg) {
        if (client.player != null) client.player.sendSystemMessage(Component.literal(msg));
    }

    private String formatTargetNames() {
        return targets.stream()
                .map(t -> targetDisplayName(t) + "(" + t.minCount() + "/" + t.maxPrice() + ")")
                .collect(Collectors.joining(", "));
    }

    private String targetDisplayName(TargetEntry target) {
        if (target.isEnchantedBook()) {
            if (target.enchants().isEmpty()) return target.id().getPath();
            Identifier enchantId = target.enchants().get(0).id();
            Enchantment enchantment = getEnchantment(enchantId);
            return enchantment != null ? enchantment.description().getString() : enchantId.getPath();
        }
        var item = BuiltInRegistries.ITEM.get(target.id()).map(net.minecraft.core.Holder.Reference::value).orElse(null);
        return item != null ? item.getName(new ItemStack(item)).getString() : target.id().toString();
    }

    private static Enchantment getEnchantment(Identifier id) {
        var client = Minecraft.getInstance();
        if (client.level == null) return null;
        var registry = client.level.registryAccess().lookupOrThrow(
                net.minecraft.core.registries.Registries.ENCHANTMENT);
        return registry.get(ResourceKey.create(
                net.minecraft.core.registries.Registries.ENCHANTMENT, id))
                .map(net.minecraft.core.Holder.Reference::value).orElse(null);
    }
}
