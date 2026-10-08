package com.liuyue.autoTradeCycling.net;

import com.liuyue.autoTradeCycling.AutoTradeCyclingMod;
import com.liuyue.autoTradeCycling.common.SearchSpeed;
import com.liuyue.autoTradeCycling.common.TradeTargets.EnchantRequirement;
import com.liuyue.autoTradeCycling.common.TradeTargets.TargetEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import io.netty.handler.codec.DecoderException;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端请求服务端批量重掷村民交易，直到命中目标或玩家手动停止。
 */
public record SearchTradesPayload(List<TargetEntry> targets, boolean matchAny, SearchSpeed speed)
        implements CustomPacketPayload {

    public static final Type<SearchTradesPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AutoTradeCyclingMod.MOD_ID, "search_trades"));

    private static final int MAX_TARGETS = 64;
    private static final int MAX_ENCHANTS_PER_TARGET = 16;

    public static final StreamCodec<RegistryFriendlyByteBuf, SearchTradesPayload> CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, SearchTradesPayload value) {
            buf.writeVarInt(value.targets.size());
            for (TargetEntry target : value.targets) {
                buf.writeIdentifier(target.id());
                buf.writeVarInt(target.minCount());
                buf.writeVarInt(target.maxPrice());
                buf.writeVarInt(target.enchants().size());
                for (EnchantRequirement requirement : target.enchants()) {
                    buf.writeIdentifier(requirement.id());
                    buf.writeVarInt(requirement.minLevel());
                }
            }
            buf.writeBoolean(value.matchAny);
            buf.writeUtf(value.speed.name());
        }

        @Override
        public SearchTradesPayload decode(RegistryFriendlyByteBuf buf) {
            int targetCount = buf.readVarInt();
            if (targetCount < 0 || targetCount > MAX_TARGETS) {
                throw new DecoderException("目标数量非法: " + targetCount);
            }
            List<TargetEntry> targets = new ArrayList<>(targetCount);
            for (int i = 0; i < targetCount; i++) {
                Identifier id = buf.readIdentifier();
                int minCount = buf.readVarInt();
                int maxPrice = buf.readVarInt();
                int enchantCount = buf.readVarInt();
                if (minCount < 1 || maxPrice < 1 || enchantCount < 0 || enchantCount > MAX_ENCHANTS_PER_TARGET) {
                    throw new DecoderException("目标参数非法: minCount=" + minCount
                            + " maxPrice=" + maxPrice + " enchantCount=" + enchantCount);
                }
                List<EnchantRequirement> enchants = new ArrayList<>(enchantCount);
                for (int j = 0; j < enchantCount; j++) {
                    enchants.add(new EnchantRequirement(buf.readIdentifier(), buf.readVarInt()));
                }
                targets.add(new TargetEntry(id, enchants, minCount, maxPrice));
            }
            boolean matchAny = buf.readBoolean();
            SearchSpeed speed = SearchSpeed.byName(buf.readUtf(16), SearchSpeed.DEFAULT_SPEED);
            return new SearchTradesPayload(targets, matchAny, speed);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
