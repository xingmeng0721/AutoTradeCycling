package com.liuyue.autoTradeCycling.net;

import com.liuyue.autoTradeCycling.AutoTradeCyclingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端批量搜索的状态回传：进度、最终结果、拒绝原因共用这一个包。
 * matched 是命中目标在请求列表中的下标，客户端据此显示详情。
 */
public record SearchResultPayload(int status, int attempts, List<Integer> matched) implements CustomPacketPayload {

    /** 找到目标。 */
    public static final int STATUS_FOUND = 0;
    /** 达到上限仍未找到。 */
    public static final int STATUS_NOT_FOUND = 1;
    /** 村民/界面状态不合法，未执行搜索。 */
    public static final int STATUS_REJECTED = 2;
    /** 搜索进行中的进度回传，搜索还会继续；matched 为空。 */
    public static final int STATUS_PROGRESS = 3;

    public static final Type<SearchResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AutoTradeCyclingMod.MOD_ID, "search_result"));

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