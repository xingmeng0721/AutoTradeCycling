package com.liuyue.autoTradeCycling.mixin;

import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.trading.VillagerTrade;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(VillagerTrade.class)
public interface VillagerTradeAccessor {

    @Accessor("gives")
    ItemStackTemplate getGives();
}