package com.liuyue.autoTradeCycling.client.manager;

import com.liuyue.autoTradeCycling.mixin.VillagerTradeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.VillagerTrade;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 村民交易的可能性数据，优先使用服务端下发的全集，供界面过滤与目标剔除。
 */
public final class VillagerTradeData {

    private static Set<Identifier> itemCache;
    private static volatile Set<Identifier> syncedItems = Set.of();

    private VillagerTradeData() {
    }

    public static Set<Identifier> syncedItems() {
        return syncedItems;
    }

    public static void acceptSynced(List<Identifier> items) {
        Set<Identifier> set = new HashSet<>(items);
        syncedItems = set;
        TargetStore.markDirty();
    }

    public static void restoreSynced(List<Identifier> items) {
        syncedItems = new HashSet<>(items);
    }

    public static List<Identifier> syncedItemsSnapshot() {
        return new ArrayList<>(syncedItems);
    }

    public static boolean hasCandidates() {
        return !villagerItems().isEmpty();
    }

    public static synchronized Set<Identifier> villagerItems() {
        if (!syncedItems.isEmpty()) return syncedItems;
        if (itemCache != null) return itemCache;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return Set.of();

        Registry<VillagerTrade> trades = client.level.registryAccess()
                .lookup(Registries.VILLAGER_TRADE).orElse(null);
        if (trades == null) return Set.of();
        Set<Item> items = new HashSet<>();
        trades.stream().forEach(trade ->
                items.add(((VillagerTradeAccessor) trade).getGives().item().value()));

        items.add(Items.ENCHANTED_BOOK);
        items.add(Items.FILLED_MAP);
        items.add(Items.SUSPICIOUS_STEW);

        Set<Identifier> ids = new HashSet<>();
        for (Item item : items) {
            if (item != Items.AIR) ids.add(BuiltInRegistries.ITEM.getKey(item));
        }
        if (ids.size() < 10) return Set.of();
        itemCache = ids;
        return itemCache;
    }

    public static boolean enchantOnTradedEquipment(Identifier enchantId) {
        return inTag(enchantId, EnchantmentTags.ON_TRADED_EQUIPMENT);
    }

    public static boolean enchantInBooks(Identifier enchantId) {
        return inTag(enchantId, EnchantmentTags.TRADEABLE);
    }

    private static boolean inTag(Identifier enchantId, TagKey<Enchantment> tag) {
        Registry<Enchantment> registry = enchantmentRegistry();
        if (registry == null) return true;
        Enchantment enchantment = registry.getValue(enchantId);
        if (enchantment == null) return false;
        return registry.wrapAsHolder(enchantment).is(tag);
    }

    public static int enchantMaxLevel(Identifier enchantId) {
        Registry<Enchantment> registry = enchantmentRegistry();
        if (registry == null) return 0;
        Enchantment enchantment = registry.getValue(enchantId);
        return enchantment == null ? 0 : enchantment.getMaxLevel();
    }

    public static boolean canApplyTo(Identifier enchantId, Identifier itemId) {
        Registry<Enchantment> registry = enchantmentRegistry();
        if (registry == null) return true;
        Enchantment enchantment = registry.getValue(enchantId);
        if (enchantment == null) return false;
        return BuiltInRegistries.ITEM.getOptional(itemId)
                .map(item -> enchantment.canEnchant(new ItemStack(item)))
                .orElse(false);
    }

    private static Registry<Enchantment> enchantmentRegistry() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return null;
        return client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
    }
}