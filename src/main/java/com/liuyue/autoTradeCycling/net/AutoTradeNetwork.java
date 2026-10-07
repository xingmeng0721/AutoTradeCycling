package com.liuyue.autoTradeCycling.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class AutoTradeNetwork {

    private AutoTradeNetwork() {
    }

    /** 两侧都要注册，且必须在任何收发之前。 */
    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(SearchTradesPayload.TYPE, SearchTradesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SearchResultPayload.TYPE, SearchResultPayload.CODEC);
    }
}