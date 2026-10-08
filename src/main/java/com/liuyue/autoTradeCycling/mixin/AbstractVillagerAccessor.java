package com.liuyue.autoTradeCycling.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.TradeSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 26.x 中 addOffersFromTradeSet 是 protected，重掷后需要自行生成 2-5 级交易用于匹配。 */
@Mixin(AbstractVillager.class)
public interface AbstractVillagerAccessor {

    @Invoker("addOffersFromTradeSet")
    void invokeAddOffersFromTradeSet(ServerLevel level, MerchantOffers offers, ResourceKey<TradeSet> tradeSet);
}