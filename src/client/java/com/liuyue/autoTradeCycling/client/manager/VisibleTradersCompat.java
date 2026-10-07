package com.liuyue.autoTradeCycling.client.manager;

import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffers;
import net.ramixin.visibletraders.ducks.MerchantMenuDuck;

/**
 * Visible Traders 0.1.2.2（1.21.10）兼容层。
 * <p>
 * 该版本会把村民所有等级的交易都放进 {@link MerchantMenu#getOffers()}，
 * 并通过 {@link MerchantMenuDuck#visibleTraders$shouldAllowTrade(int)} 标记哪些是当前可交易的。
 * <p>
 * 本类引用了 Visible Traders 的类，只能在确认其已加载后调用。
 */
public final class VisibleTradersCompat {

    private VisibleTradersCompat() {
    }

    /**
     * 当前已解锁的交易。
     */
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