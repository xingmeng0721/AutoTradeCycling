package com.liuyue.autoTradeCycling.net;

import com.liuyue.autoTradeCycling.AutoTradeCyclingMod;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端下发村民可交易物品全集，客户端用于缓存与列表过滤。
 */
public record TradeableItemsPayload(List<Identifier> items) implements CustomPacketPayload {

    private static final int MAX_ITEMS = 4096;

    public static final Type<TradeableItemsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AutoTradeCyclingMod.MOD_ID, "tradeable_items"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TradeableItemsPayload> CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, TradeableItemsPayload value) {
            buf.writeVarInt(value.items.size());
            for (Identifier id : value.items) {
                buf.writeIdentifier(id);
            }
        }

        @Override
        public TradeableItemsPayload decode(RegistryFriendlyByteBuf buf) {
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_ITEMS) {
                throw new DecoderException("物品数量非法: " + count);
            }
            List<Identifier> items = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                items.add(buf.readIdentifier());
            }
            return new TradeableItemsPayload(items);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}