package com.liuyue.autoTradeCycling.client.mixin;

import com.liuyue.autoTradeCycling.client.ClientScreens;
import com.liuyue.autoTradeCycling.client.manager.AutoTradeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.ramixin.visibletraders.client.VisibleTradersCommonClient;
import net.ramixin.visibletraders.networking.ClientboundLockedTradesPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 收到 Visible Traders 的锁定交易预览包后通知 AutoTradeManager。 */
@Mixin(VisibleTradersCommonClient.class)
public class VisibleTradersPreviewListener {
    @Inject(method = "handleClientboundTradesPayload", at = @At("TAIL"))
    private static void onLockedTrades(ClientboundLockedTradesPayload payload, CallbackInfo ci) {
        if (!(ClientScreens.current() instanceof MerchantScreen)) return;
        Minecraft.getInstance().execute(() -> AutoTradeManager.getInstance().onPreviewUpdated());
    }
}
