package com.liuyue.autoTradeCycling.server;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffers;
import net.ramixin.visibletraders.ducks.VillagerDuck;

/**
 * Visible Traders 桥接。VT 的 2-5 级锁定交易不在 villager.getOffers() 里，
 * 必须走它自己的 API：合并列表、移位等级、重建分级数据。
 */
public final class VisibleTradersServer {

    private VisibleTradersServer() {
    }

    public static MerchantOffers combinedOffers(Villager villager) {
        return VillagerDuck.of(villager).visibleTraders$getCombinedOffers();
    }

    public static int shiftedLevel(Villager villager) {
        return VillagerDuck.of(villager).visibleTraders$getShiftedLevel();
    }

    public static void regenerateTrades(Villager villager) {
        VillagerDuck.of(villager).visibleTrades$regenerateTrades();
    }
}
