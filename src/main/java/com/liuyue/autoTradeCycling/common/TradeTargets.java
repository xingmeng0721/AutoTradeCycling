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
 * 目标模型与匹配逻辑，客户端与服务端共用。
 * 抽到 common 是为了让服务端批量搜索和客户端回退模式用同一套判定，避免两边行为不一致。
 */
public final class TradeTargets {

    /** 某个物品上的一条附魔要求。 */
    public record EnchantRequirement(ResourceLocation id, int minLevel) {}

    /** 一个交易目标。enchants 必须是可变列表，{@code addEnchantToItem} 会往里追加。 */
    public record TargetEntry(ResourceLocation id, List<EnchantRequirement> enchants, int minCount, int maxPrice) {
        public boolean isEnchantedBook() {
            return id.equals(BuiltInRegistries.ITEM.getKey(Items.ENCHANTED_BOOK));
        }
    }

    private TradeTargets() {
    }

    /** 单条报价是否满足某个目标（价格上限、数量下限、附魔等级都算在内）。 */
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

    /** 命中的目标下标（对应传入 targets 的顺序）。 */
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

    /** 是否达成匹配：ALL 要求全部命中，ANY 只要有一个。 */
    public static boolean isMatch(List<Integer> matched, int targetCount, boolean matchAny) {
        return matchAny ? !matched.isEmpty() : matched.size() == targetCount;
    }
}