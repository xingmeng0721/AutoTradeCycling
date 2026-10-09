package com.liuyue.autoTradeCycling;

import com.liuyue.autoTradeCycling.net.AutoTradeNetwork;
import com.liuyue.autoTradeCycling.server.ServerSearchHandler;
import com.liuyue.autoTradeCycling.server.TradeableItemsServer;
import net.fabricmc.api.ModInitializer;

public class AutoTradeCyclingMod implements ModInitializer {

    public static final String MOD_ID = "auto-trade-cycling";

    @Override
    public void onInitialize() {
        AutoTradeNetwork.registerPayloads();
        ServerSearchHandler.register();
        TradeableItemsServer.register();
    }
}