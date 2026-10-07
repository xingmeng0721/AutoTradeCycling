package com.liuyue.autoTradeCycling.client;

import com.liuyue.autoTradeCycling.client.command.AutoTradeCommand;
import com.liuyue.autoTradeCycling.client.gui.AutoTradeConfigScreen;
import com.liuyue.autoTradeCycling.client.manager.AutoTradeManager;
import com.liuyue.autoTradeCycling.net.SearchResultPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public class AutoTradeCyclingClient implements ClientModInitializer {

    private static KeyMapping openConfigKey;

    @Override
    public void onInitializeClient() {
        AutoTradeCommand.register();

        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.auto-trade-cycling.open_config",
                InputConstants.KEY_G,
                KeyMapping.Category.MISC));

        ClientPlayNetworking.registerGlobalReceiver(SearchResultPayload.TYPE, (payload, context) ->
                context.client().execute(() -> AutoTradeManager.getInstance().onSearchResult(payload)));

        ClientTickEvents.START_CLIENT_TICK.register(AutoTradeCyclingClient::onClientTick);
        ClientTickEvents.END_CLIENT_TICK.register(AutoTradeCyclingClient::onEndTick);
    }

    private static void onClientTick(Minecraft client) {
        if (client == null) return;

        AutoTradeManager manager = AutoTradeManager.getInstance();
        if (manager.isActive()) {
            manager.onClientTick(client);
        }
    }

    /** 按快捷键打开图形化配置界面。 */
    private static void onEndTick(Minecraft client) {
        if (client == null) return;
        while (openConfigKey.consumeClick()) {
            if (!(client.screen instanceof AutoTradeConfigScreen)) {
                client.setScreen(new AutoTradeConfigScreen());
            }
        }
    }
}