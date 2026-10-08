package com.liuyue.autoTradeCycling.net;

import com.liuyue.autoTradeCycling.AutoTradeCyclingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import io.netty.handler.codec.DecoderException;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端批量搜索的状态回传：进度、结果、拒绝原因。
 */
public record SearchResultPayload(int status, int attempts, List<Integer> matched) implements CustomPacketPayload {

    public static final int STATUS_FOUND = 0;
    public static final int STATUS_REJECTED = 1;
    public static final int STATUS_PROGRESS = 2;

    private static final int MAX_MATCHED = 64;

    public static final Type<SearchResultPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AutoTradeCyclingMod.MOD_ID, "search_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SearchResultPayload> CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, SearchResultPayload value) {
            buf.writeVarInt(value.status);
            buf.writeVarInt(value.attempts);
            buf.writeVarInt(value.matched.size());
            for (int index : value.matched) {
                buf.writeVarInt(index);
            }
        }

        @Override
        public SearchResultPayload decode(RegistryFriendlyByteBuf buf) {
            int status = buf.readVarInt();
            int attempts = buf.readVarInt();
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_MATCHED) {
                throw new DecoderException("命中数量非法: " + count);
            }
            List<Integer> matched = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                matched.add(buf.readVarInt());
            }
            return new SearchResultPayload(status, attempts, matched);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
