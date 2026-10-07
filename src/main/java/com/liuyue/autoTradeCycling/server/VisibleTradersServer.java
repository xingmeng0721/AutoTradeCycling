package com.liuyue.autoTradeCycling.server;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffers;
import net.ramixin.visibletraders.ducks.VillagerDuck;

/**
 * Visible Traders 桥接。VT 是可选依赖，单独放一个类里，
 * 没装 VT 时不会触发 {@link VillagerDuck} 的类加载。
 *
 * <p>VT 的 2-5 级"锁定交易"不在 villager.getOffers() 里：VT 生成时会临时把村民等级抬到 5、
 * 调 updateTrades() 再把新增的报价从真实 offers 里摘出来，单独存进 LockedTradeData。
 * 只有 openTradingScreen 里才会用 getCombinedOffers() 把完整列表发给客户端。
 * 我们直接调 sendMerchantOffers 会绕过那个 hook，所以必须自己取合并列表、自己发，
 * 否则客户端看不到 2 级以上的交易，匹配也会漏掉它们。
 */
public final class VisibleTradersServer {

    private VisibleTradersServer() {
    }

    /** 1 级报价 + 2-5 级锁定交易的合并列表，即 VT 平时发给客户端的那一份。 */
    public static MerchantOffers combinedOffers(Villager villager) {
        return VillagerDuck.of(villager).visibleTraders$getCombinedOffers();
    }

    /** 让 VT 依据村民当前的报价重建分级交易数据，必须每轮重掷后调用。 */
    public static void regenerateTrades(Villager villager) {
        VillagerDuck.of(villager).visibleTrades$regenerateTrades();
    }
}