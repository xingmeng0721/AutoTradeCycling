package com.liuyue.autoTradeCycling.server;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffers;
import net.ramixin.visibletraders.ducks.VillagerDuck; //#replace MC >= 1.21.3 && MC < 1.21.8 ? import net.ramgames.visibletraders.ducks.VillagerDuck;

/**
 * Visible Traders 桥接。VT 的 2-5 级锁定交易不在 villager.getOffers()
 */
public final class VisibleTradersServer {

    private VisibleTradersServer() {
    }

    public static MerchantOffers combinedOffers(Villager villager) {
        return VillagerDuck.of(villager).visibleTraders$getCombinedOffers(); //#replace MC >= 1.21.3 && MC < 1.21.8 ? return VillagerDuck.of(villager).visibleTraders$getLockedOffers();
    }

    public static int shiftedLevel(Villager villager) {
        return VillagerDuck.of(villager).visibleTraders$getShiftedLevel(); //#replace MC >= 1.21.3 && MC < 1.21.8 ? return villageLevel(villager);
    }

    public static void regenerateTrades(Villager villager) {
        VillagerDuck.of(villager).visibleTrades$regenerateTrades(); //#replace MC >= 1.21.3 && MC < 1.21.8 ? VillagerDuck.of(villager).visibleTraders$forceTradeGeneration();
    }

    private static int villageLevel(Villager villager) {
        return villager.getVillagerData().level(); //#replace < 1.21.5 ? return villager.getVillagerData().getLevel();
    }
}