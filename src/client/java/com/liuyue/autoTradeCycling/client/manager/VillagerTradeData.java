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
import java.util.List;
import java.util.Set;

/**
 * 原版村民交易的"可能性"数据：哪些物品村民卖得出来、哪些附魔可能出现在交易里。
 * 供配置界面过滤列表，以及 {@link AutoTradeManager} 在开始前剔除永远刷不出来的目标。
 *
 * <p>物品集合由真实 {@link Villager} 实例驱动 {@code ItemListing.getOffer} 得到，
 * 不依赖任何硬编码清单；注册表不可用（例如未进入世界）时一律"放行"，避免误删用户配置。
 */
public final class VillagerTradeData {

    /**
     * 村民交易用到的附魔标签：附魔书走 {@code TRADEABLE} 与各生物群系的 trades 标签，
     * 附魔装备走 {@code ON_TRADED_EQUIPMENT}（见 VillagerTrades.EnchantedItemForEmeralds）。
     * 标签内容由游戏/数据包提供，这里只负责说明"哪些标签参与了村民交易"。
     */
    private static final List<TagKey<Enchantment>> ENCHANT_TAGS = List.of(
            EnchantmentTags.TRADEABLE,
            EnchantmentTags.ON_TRADED_EQUIPMENT,
            EnchantmentTags.TRADES_DESERT_COMMON, EnchantmentTags.TRADES_DESERT_SPECIAL,
            EnchantmentTags.TRADES_JUNGLE_COMMON, EnchantmentTags.TRADES_JUNGLE_SPECIAL,
            EnchantmentTags.TRADES_PLAINS_COMMON, EnchantmentTags.TRADES_PLAINS_SPECIAL,
            EnchantmentTags.TRADES_SAVANNA_COMMON, EnchantmentTags.TRADES_SAVANNA_SPECIAL,
            EnchantmentTags.TRADES_SNOW_COMMON, EnchantmentTags.TRADES_SNOW_SPECIAL,
            EnchantmentTags.TRADES_SWAMP_COMMON, EnchantmentTags.TRADES_SWAMP_SPECIAL,
            EnchantmentTags.TRADES_TAIGA_COMMON, EnchantmentTags.TRADES_TAIGA_SPECIAL);

    private static Set<ResourceLocation> itemCache;

    private VillagerTradeData() {
    }

    // ------------------------------------------------------------------ 物品

    /**
     * 村民可能出售的物品 ID。逐个生成 {@code MerchantOffer} 取"结果物品"，
     * 所以只包含村民真正卖出的东西，不会混入村民收购的物品。
     * 提取失败时返回空集合（调用方据此关闭过滤）。
     */
    public static synchronized Set<ResourceLocation> villagerItems() {
        if (itemCache != null) return itemCache;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return Set.of();

        Set<Item> items = new HashSet<>();
        RandomSource random = RandomSource.create(0L);
        try {
            // 部分交易靠村民的生物群系类型挑选报价，因此每种已注册的类型都要过一遍
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
            // 兜底：能收到多少算多少
        }

        // 藏宝图交易要服务端世界才能生成地图，客户端拿不到，这里补上（村民确实会卖这三样）
        items.add(Items.ENCHANTED_BOOK);
        items.add(Items.FILLED_MAP);
        items.add(Items.SUSPICIOUS_STEW);

        Set<ResourceLocation> ids = new HashSet<>();
        for (Item item : items) {
            if (item != Items.AIR) ids.add(BuiltInRegistries.ITEM.getKey(item));
        }
        // 数量过少说明这批数据不可信，不缓存，等下次进世界后重算
        if (ids.size() < 10) return Set.of();
        itemCache = ids;
        return itemCache;
    }

    // ------------------------------------------------------------------ 附魔

    /** 村民交易里可能出现的附魔；注册表不可用时返回空集合。 */
    public static Set<ResourceLocation> tradeableEnchantments() {
        Set<ResourceLocation> ids = new HashSet<>();
        Registry<Enchantment> registry = enchantmentRegistry();
        if (registry == null) return ids;
        try {
            for (TagKey<Enchantment> tag : ENCHANT_TAGS) {
                for (Holder<Enchantment> holder : registry.getTagOrEmpty(tag)) {
                    holder.unwrapKey().ifPresent(key -> ids.add(key.location()));
                }
            }
        } catch (Throwable ignored) {
            return Set.of();
        }
        return ids;
    }

    /** 村民是否可能出售该附魔，不限于是附魔书还是附魔装备。 */
    public static boolean enchantTradeable(ResourceLocation enchantId) {
        Set<ResourceLocation> tradeable = tradeableEnchantments();
        return tradeable.isEmpty() || tradeable.contains(enchantId);
    }

    /**
     * 该附魔是否能出现在村民出售的**附魔装备**上。
     * 附魔装备走的是 {@code ON_TRADED_EQUIPMENT} 标签（见 VillagerTrades.EnchantedItemForEmeralds），
     * 比"附魔书能卖什么"窄，所以必须单独判断，不能拿并集糊弄过去。
     */
    public static boolean enchantOnTradedEquipment(ResourceLocation enchantId) {
        return inTag(enchantId, EnchantmentTags.ON_TRADED_EQUIPMENT);
    }

    /** 该附魔是否能出现在村民出售的附魔书上。 */
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

    /** 附魔最大等级；查不到返回 0（调用方应跳过等级校验）。 */
    public static int enchantMaxLevel(ResourceLocation enchantId) {
        Registry<Enchantment> registry = enchantmentRegistry();
        if (registry == null) return 0;
        Enchantment enchantment = registry.getValue(enchantId);
        return enchantment == null ? 0 : enchantment.getMaxLevel();
    }

    /** 该附魔能否附在指定物品上（用于排除"保护 铁斧"这类组合）。 */
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