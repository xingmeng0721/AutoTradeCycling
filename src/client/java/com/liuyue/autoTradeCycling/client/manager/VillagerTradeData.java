package com.liuyue.autoTradeCycling.client.manager;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.HashSet;
import java.util.Set;

/**
 * 原版村民交易的可能性数据：村民能卖出哪些物品、哪些附魔可能出现在交易里，供界面过滤与目标剔除。
 */
public final class VillagerTradeData {

    private static Set<ResourceLocation> itemCache;

    private VillagerTradeData() {
    }

    public static synchronized Set<ResourceLocation> villagerItems() {
        if (itemCache != null) return itemCache;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return Set.of();

        Set<Item> items = new HashSet<>();
        RandomSource random = RandomSource.create(0L);
        try {
            for (Holder.Reference<VillagerType> type : BuiltInRegistries.VILLAGER_TYPE.listElements().toList()) {
                Villager trader = new Villager(EntityType.VILLAGER, client.level, type);
                for (var tiers : VillagerTrades.TRADES.values()) {
                    for (var listings : tiers.values()) {
                        for (VillagerTrades.ItemListing listing : listings) {
                            MerchantOffer offer;
                            try {
                                offer = listing.getOffer(trader, random);
                            } catch (Throwable ignored) {
                                continue;
                            }
                            if (offer != null) items.add(offer.getResult().getItem());
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        items.add(Items.ENCHANTED_BOOK);
        items.add(Items.FILLED_MAP);
        items.add(Items.SUSPICIOUS_STEW);

        Set<ResourceLocation> ids = new HashSet<>();
        for (Item item : items) {
            if (item != Items.AIR) ids.add(BuiltInRegistries.ITEM.getKey(item));
        }
        if (ids.size() < 10) return Set.of();
        itemCache = ids;
        return itemCache;
    }

    public static boolean enchantOnTradedEquipment(ResourceLocation enchantId) {
        return inTag(enchantId, EnchantmentTags.ON_TRADED_EQUIPMENT);
    }

    public static boolean enchantInBooks(ResourceLocation enchantId) {
        return inTag(enchantId, EnchantmentTags.TRADEABLE);
    }

    private static boolean inTag(ResourceLocation enchantId, TagKey<Enchantment> tag) {
        Registry<Enchantment> registry = enchantmentRegistry();
        if (registry == null) return true;
        Enchantment enchantment = registry.getValue(enchantId);
        if (enchantment == null) return false;
        return registry.wrapAsHolder(enchantment).is(tag);
    }

    public static int enchantMaxLevel(ResourceLocation enchantId) {
        Registry<Enchantment> registry = enchantmentRegistry();
        if (registry == null) return 0;
        Enchantment enchantment = registry.getValue(enchantId);
        return enchantment == null ? 0 : enchantment.getMaxLevel();
    }

    public static boolean canApplyTo(ResourceLocation enchantId, ResourceLocation itemId) {
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