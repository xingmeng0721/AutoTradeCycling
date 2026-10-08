package com.liuyue.autoTradeCycling.net;

import com.liuyue.autoTradeCycling.AutoTradeCyclingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端请求服务端下发村民可交易物品全集（空载荷）。
 */
public record RequestTradeableItemsPayload() implements CustomPacketPayload {

    public static final RequestTradeableItemsPayload INSTANCE = new RequestTradeableItemsPayload();

    public static final Type<RequestTradeableItemsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AutoTradeCyclingMod.MOD_ID, "request_tradeable_items"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestTradeableItemsPayload> CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, RequestTradeableItemsPayload value) {
        }

        @Override
        public RequestTradeableItemsPayload decode(RegistryFriendlyByteBuf buf) {
            return INSTANCE;
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}