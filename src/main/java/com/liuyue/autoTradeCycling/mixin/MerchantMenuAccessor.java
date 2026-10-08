package com.liuyue.autoTradeCycling.mixin;

import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 服务端需要从菜单里拿到村民实体和交易容器 */
@Mixin(MerchantMenu.class)
public interface MerchantMenuAccessor {

    @Accessor("trader")
    Merchant getTrader();

    @Accessor("tradeContainer")
    MerchantContainer getTradeContainer();
}