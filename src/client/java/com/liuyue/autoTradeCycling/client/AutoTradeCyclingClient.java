package com.liuyue.autoTradeCycling.client;

import com.liuyue.autoTradeCycling.client.command.AutoTradeCommand;
import com.liuyue.autoTradeCycling.client.gui.AutoTradeConfigScreen;
import com.liuyue.autoTradeCycling.client.manager.AutoTradeManager;
import com.liuyue.autoTradeCycling.client.manager.TargetStore;
import com.liuyue.autoTradeCycling.net.SearchResultPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** 客户端入口：注册命令、按键、网络接收与 tick 回调。 */
public class AutoTradeCyclingClient implements ClientModInitializer {

    private static KeyMapping openConfigKey;

    @Override
    public void onInitializeClient() {
        AutoTradeCommand.register();
        TargetStore.load();

        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.auto-trade-cycling.open_config",
                InputConstants.KEY_G,
                KeyMapping.Category.MISC)); //#replace < 1.21.9 ? KeyMapping.CATEGORY_MISC));

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

    private static void onEndTick(Minecraft client) {
        if (client == null) return;
        TargetStore.flushIfDirty();
        while (openConfigKey.consumeClick()) {
            if (!(client.screen instanceof AutoTradeConfigScreen)) {
                AutoTradeManager.getInstance().cancel();
                client.setScreen(new AutoTradeConfigScreen());
            }
        }
    }
}