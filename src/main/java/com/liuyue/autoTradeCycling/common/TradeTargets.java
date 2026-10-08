package com.liuyue.autoTradeCycling.common;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;

/**
 * 目标模型与匹配逻辑，客户端与服务端共用，保证两边判定一致。
 */
public final class TradeTargets {

    public record EnchantRequirement(ResourceLocation id, int minLevel) {}

    public record TargetEntry(ResourceLocation id, List<EnchantRequirement> enchants, int minCount, int maxPrice) {
        public boolean isEnchantedBook() {
            return isEnchantedBookId(id);
        }
    }

    public static boolean isEnchantedBookId(ResourceLocation id) {
        return id.equals(BuiltInRegistries.ITEM.getKey(Items.ENCHANTED_BOOK));
    }

    private TradeTargets() {
    }

    public static boolean matches(MerchantOffer offer, TargetEntry target) {
        if (offer.isOutOfStock()) return false;
        if (offer.getBaseCostA().getCount() > target.maxPrice()) return false;

        ItemStack result = offer.getResult();
        if (!target.id().equals(BuiltInRegistries.ITEM.getKey(result.getItem()))) return false;
        if (result.getCount() < target.minCount()) return false;
        if (target.enchants().isEmpty()) return true;

        ItemEnchantments enchantments = result.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (enchantments.isEmpty()) {
            enchantments = result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        }
        for (EnchantRequirement requirement : target.enchants()) {
            if (!hasEnchantment(enchantments, requirement)) return false;
        }
        return true;
    }

    private static boolean hasEnchantment(ItemEnchantments enchantments, EnchantRequirement requirement) {
        for (var entry : enchantments.entrySet()) {
            ResourceLocation id = entry.getKey().unwrapKey().map(ResourceKey::location).orElse(null);
            if (requirement.id().equals(id) && entry.getIntValue() >= requirement.minLevel()) return true;
        }
        return false;
    }

    public static List<Integer> matchIndices(MerchantOffers offers, List<TargetEntry> targets) {
        List<Integer> matched = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            TargetEntry target = targets.get(i);
            for (MerchantOffer offer : offers) {
                if (matches(offer, target)) {
                    matched.add(i);
                    break;
                }
            }
        }
        return matched;
    }

    public static boolean isMatch(List<Integer> matched, int targetCount, boolean matchAny) {
        return matchAny ? !matched.isEmpty() : matched.size() == targetCount;
    }
}
