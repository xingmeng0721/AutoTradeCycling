package com.liuyue.autoTradeCycling.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** 网络包类型注册。 */
public final class AutoTradeNetwork {

    private AutoTradeNetwork() {
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.serverboundPlay().register(SearchTradesPayload.TYPE, SearchTradesPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SearchResultPayload.TYPE, SearchResultPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestTradeableItemsPayload.TYPE, RequestTradeableItemsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TradeableItemsPayload.TYPE, TradeableItemsPayload.CODEC);
    }
}