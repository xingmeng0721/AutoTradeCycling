package com.liuyue.autoTradeCycling.client.mixin;

import com.liuyue.autoTradeCycling.client.manager.AutoTradeManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** 在交易界面标题的「交易」后追加已刷新次数。 */
@Mixin(MerchantScreen.class)
public class CycleCountDisplay {

    @Shadow
    @Final
    private static Component TRADES_LABEL;

    @WrapOperation(method = "renderLabels", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I"))
    private int width(Font instance, FormattedText text, Operation<Integer> original) {
        AutoTradeManager mgr = AutoTradeManager.getInstance();
        if (!mgr.isActive() || !text.getString().equals(TRADES_LABEL.getString())) return original.call(instance, text);
        int count = mgr.getCycleCount();
        if (count > 0) {
            return original.call(instance, Component.literal(text.getString() + " (" + count + ")"));
        }
        return original.call(instance, text);
    }

    @WrapOperation(method = "renderLabels", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V")) //#replace < 1.21.8 ?     @WrapOperation(method = "renderLabels", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"))
    private void renderCycleCount(GuiGraphics instance, Font font, Component str, int x, int y, int color, boolean dropShadow, Operation<Void> original) { //#replace < 1.21.8 ?     private int renderCycleCount(GuiGraphics instance, Font font, Component str, int x, int y, int color, boolean dropShadow, Operation<Integer> original) {
        original.call(instance, font, decorate(str), x, y, color, dropShadow); //#replace < 1.21.8 ?         return original.call(instance, font, decorate(str), x, y, color, dropShadow);
    }

    private static Component decorate(Component str) {
        AutoTradeManager mgr = AutoTradeManager.getInstance();
        if (!mgr.isActive() || !str.getString().equals(TRADES_LABEL.getString())) return str;
        int count = mgr.getCycleCount();
        return count > 0 ? Component.literal(str.getString() + " (" + count + ")") : str;
    }
}