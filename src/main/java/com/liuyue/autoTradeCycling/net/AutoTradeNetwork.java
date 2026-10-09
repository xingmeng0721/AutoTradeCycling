package com.liuyue.autoTradeCycling.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class AutoTradeNetwork {

    private AutoTradeNetwork() {
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(SearchTradesPayload.TYPE, SearchTradesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SearchResultPayload.TYPE, SearchResultPayload.CODEC);
    }
}