package com.liuyue.autoTradeCycling.client.manager;

import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffers;
import net.ramixin.visibletraders.ducks.MerchantMenuDuck;

/**
 * Visible Traders 兼容层：合并村民各等级交易并提供当前可交易判定。
 */
public final class VisibleTradersCompat {

    private VisibleTradersCompat() {
    }

    public static MerchantOffers getUnlockedOffers(MerchantMenu menu) {
        MerchantOffers result = new MerchantOffers();
        MerchantOffers offers = menu.getOffers();
        MerchantMenuDuck duck = (MerchantMenuDuck) menu;
        for (int i = 0; i < offers.size(); i++) {
            if (duck.visibleTraders$shouldAllowTrade(i)) {
                result.add(offers.get(i));
            }
        }
        return result;
    }
}
