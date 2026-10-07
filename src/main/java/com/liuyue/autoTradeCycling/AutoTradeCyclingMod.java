package com.liuyue.autoTradeCycling;

import com.liuyue.autoTradeCycling.net.AutoTradeNetwork;
import com.liuyue.autoTradeCycling.server.ServerSearchHandler;
import net.fabricmc.api.ModInitializer;

/**
 * 通用入口（客户端与服务端都会加载）。
 * 服务端侧提供"批量搜索"能力：一次请求内连续重掷村民交易直到命中，省掉逐轮网络往返。
 */
public class AutoTradeCyclingMod implements ModInitializer {

    public static final String MOD_ID = "auto-trade-cycling";

    @Override
    public void onInitialize() {
        AutoTradeNetwork.registerPayloads();
        ServerSearchHandler.register();
    }
}