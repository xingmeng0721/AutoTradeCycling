package com.liuyue.autoTradeCycling.common;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 目标模型与匹配逻辑，客户端与服务端共用，保证两边判定一致。
 */
public final class TradeTargets {

    public record EnchantRequirement(Identifier id, int minLevel) {}

    public record TargetEntry(Identifier id, List<EnchantRequirement> enchants, int minCount, int maxPrice,
                              Identifier potion) {
        public TargetEntry(Identifier id, List<EnchantRequirement> enchants, int minCount, int maxPrice) {
            this(id, enchants, minCount, maxPrice, null);
        }

        public boolean isEnchantedBook() {
            return isEnchantedBookId(id);
        }
    }

    public static boolean isEnchantedBookId(Identifier id) {
        return id.equals(BuiltInRegistries.ITEM.getKey(Items.ENCHANTED_BOOK));
    }

    private TradeTargets() {
    }

    /** 单条报价与单个目标的匹配（冷路径：客户端上报用）。 */
    public static boolean matches(MerchantOffer offer, TargetEntry target) {
        if (offer.isOutOfStock()) return false;
        ItemStack result = offer.getResult();
        if (!target.id().equals(BuiltInRegistries.ITEM.getKey(result.getItem()))) return false;
        return new ParsedOffer(offer, result).matches(target);
    }

    /** 逐目标收集命中的下标，命中即按目标下标升序返回。 */
    public static List<Integer> matchIndices(MerchantOffers offers, List<TargetEntry> targets) {
        return Index.compile(targets, false).matchIndices(offers);
    }

    public static boolean isMatch(List<Integer> matched, int targetCount, boolean matchAny) {
        return matchAny ? !matched.isEmpty() : matched.size() == targetCount;
    }

    /**
     * 按物品 ID 预编译的目标索引。一次搜索建一次，之后每次重掷只解析报价，
     * 把 registry 反查次数从「目标数 × 报价数」降到「报价数」。
     */
    public static final class Index {

        private final List<TargetEntry> targets;
        private final Map<Item, int[]> buckets;
        private final boolean matchAny;
        private int[] stamp = new int[0];
        private int generation;

        private Index(List<TargetEntry> targets, Map<Item, int[]> buckets, boolean matchAny) {
            this.targets = targets;
            this.buckets = buckets;
            this.matchAny = matchAny;
        }

        public static Index compile(List<TargetEntry> targets, boolean matchAny) {
            Map<Item, List<Integer>> grouped = new HashMap<>();
            for (int i = 0; i < targets.size(); i++) {
                Item item = BuiltInRegistries.ITEM.getValue(targets.get(i).id());
                if (item == null) continue;
                grouped.computeIfAbsent(item, key -> new ArrayList<>()).add(i);
            }
            Map<Item, int[]> buckets = new HashMap<>(grouped.size());
            grouped.forEach((item, indices) -> {
                int[] bucket = new int[indices.size()];
                for (int i = 0; i < bucket.length; i++) bucket[i] = indices.get(i);
                buckets.put(item, bucket);
            });
            return new Index(List.copyOf(targets), buckets, matchAny);
        }

        public int size() {
            return targets.size();
        }

        /** 判定本组报价是否满足匹配模式：ANY 命中一个即返回，ALL 需全部命中。 */
        public boolean satisfied(MerchantOffers offers) {
            return satisfied(offers, List.of());
        }

        public boolean satisfied(MerchantOffers primary, List<MerchantOffers> extra) {
            int count = targets.size();
            if (stamp.length < count) stamp = new int[count];
            int gen = ++generation;
            int remaining = count;

            int groups = extra.size() + 1;
            for (int group = 0; group < groups; group++) {
                MerchantOffers offers = group == 0 ? primary : extra.get(group - 1);
                for (MerchantOffer offer : offers) {
                    if (remaining == 0) break;
                    if (offer.isOutOfStock()) continue;
                    ItemStack result = offer.getResult();
                    int[] bucket = buckets.get(result.getItem());
                    if (bucket == null) continue;

                    ParsedOffer parsed = new ParsedOffer(offer, result);
                    for (int index : bucket) {
                        if (stamp[index] == gen) continue;
                        if (parsed.matches(targets.get(index))) {
                            stamp[index] = gen;
                            if (matchAny || --remaining == 0) return true;
                        }
                    }
                }
                if (remaining == 0) break;
            }
            return false;
        }

        /** 收集全部命中目标的下标，命中后按目标下标升序返回。 */
        public List<Integer> matchIndices(MerchantOffers offers) {
            return matchIndices(offers, List.of());
        }

        public List<Integer> matchIndices(MerchantOffers primary, List<MerchantOffers> extra) {
            int count = targets.size();
            if (stamp.length < count) stamp = new int[count];
            int gen = ++generation;
            int remaining = count;

            int groups = extra.size() + 1;
            for (int group = 0; group < groups; group++) {
                MerchantOffers offers = group == 0 ? primary : extra.get(group - 1);
                for (MerchantOffer offer : offers) {
                    if (remaining == 0) break;
                    if (offer.isOutOfStock()) continue;
                    ItemStack result = offer.getResult();
                    int[] bucket = buckets.get(result.getItem());
                    if (bucket == null) continue;

                    ParsedOffer parsed = new ParsedOffer(offer, result);
                    for (int index : bucket) {
                        if (stamp[index] == gen) continue;
                        if (parsed.matches(targets.get(index))) {
                            stamp[index] = gen;
                            remaining--;
                        }
                    }
                }
                if (remaining == 0) break;
            }

            List<Integer> matched = new ArrayList<>(count - remaining);
            for (int i = 0; i < count; i++) {
                if (stamp[i] == gen) matched.add(i);
            }
            return matched;
        }
    }

    /** 报价解析结果：价格、数量与组件在一条报价上只算一次。 */
    private static final class ParsedOffer {

        private final int price;
        private final int count;
        private final ItemStack result;
        private PotionContents potion;
        private boolean potionResolved;
        private ItemEnchantments enchantments;
        private boolean enchantmentsResolved;

        ParsedOffer(MerchantOffer offer, ItemStack result) {
            this.result = result;
            this.price = offer.getBaseCostA().getCount();
            this.count = result.getCount();
        }

        boolean matches(TargetEntry target) {
            if (price > target.maxPrice()) return false;
            if (count < target.minCount()) return false;
            if (target.potion() != null && !target.potion().equals(potionId())) return false;
            if (target.enchants().isEmpty()) return true;

            ItemEnchantments found = enchantments();
            for (EnchantRequirement requirement : target.enchants()) {
                if (!hasEnchantment(found, requirement)) return false;
            }
            return true;
        }

        private Identifier potionId() {
            if (!potionResolved) {
                potion = result.get(DataComponents.POTION_CONTENTS);
                potionResolved = true;
            }
            return potion == null ? null
                    : potion.potion().flatMap(holder -> holder.unwrapKey().map(ResourceKey::identifier)).orElse(null);
        }

        private ItemEnchantments enchantments() {
            if (!enchantmentsResolved) {
                ItemEnchantments stored = result.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
                enchantments = stored.isEmpty()
                        ? result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                        : stored;
                enchantmentsResolved = true;
            }
            return enchantments;
        }

        private static boolean hasEnchantment(ItemEnchantments enchantments, EnchantRequirement requirement) {
            for (var entry : enchantments.entrySet()) {
                Identifier id = entry.getKey().unwrapKey().map(ResourceKey::identifier).orElse(null);
                if (requirement.id().equals(id) && entry.getIntValue() >= requirement.minLevel()) return true;
            }
            return false;
        }
    }
}