package com.liuyue.autoTradeCycling.mixin;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 重掷交易时需要重算折扣，原版方法是 private。 */
@Mixin(Villager.class)
public interface VillagerAccessor {

    @Invoker("updateSpecialPrices")
    void invokeUpdateSpecialPrices(Player player);
}