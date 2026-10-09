package com.liuyue.autoTradeCycling.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** 跨版本获取当前打开的界面。 */
public final class ClientScreens {

    private ClientScreens() {
    }

    public static Screen current() {
        return Minecraft.getInstance().screen; //#replace >= 26.2 ? return Minecraft.getInstance().gui.screen();
    }
}