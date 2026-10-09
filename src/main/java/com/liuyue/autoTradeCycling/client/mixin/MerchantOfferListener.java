package com.liuyue.autoTradeCycling.client.mixin;

import com.liuyue.autoTradeCycling.client.manager.AutoTradeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 收到服务端交易报价后通知 AutoTradeManager。 */
@Mixin(ClientPacketListener.class)
public class MerchantOfferListener {
    @Inject(method = "handleMerchantOffers", at = @At("TAIL"))
    private void onMerchantOffers(ClientboundMerchantOffersPacket packet, CallbackInfo ci) {
        AutoTradeManager.getInstance().onOffersUpdated(Minecraft.getInstance());
    }
}
