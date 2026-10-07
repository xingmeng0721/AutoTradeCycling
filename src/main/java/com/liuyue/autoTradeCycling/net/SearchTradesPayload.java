package com.liuyue.autoTradeCycling.net;

import com.liuyue.autoTradeCycling.AutoTradeCyclingMod;
import com.liuyue.autoTradeCycling.common.TradeTargets.EnchantRequirement;
import com.liuyue.autoTradeCycling.common.TradeTargets.TargetEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端请求服务端批量重掷村民交易，直到命中目标或玩家手动停止。
 * 服务端会分 tick 处理，避免一次卡服。
 */
public record SearchTradesPayload(List<TargetEntry> targets, boolean matchAny)
        implements CustomPacketPayload {

    public static final Type<SearchTradesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AutoTradeCyclingMod.MOD_ID, "search_trades"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SearchTradesPayload> CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, SearchTradesPayload value) {
            buf.writeVarInt(value.targets.size());
            for (TargetEntry target : value.targets) {
                buf.writeResourceLocation(target.id());
                buf.writeVarInt(target.minCount());
                buf.writeVarInt(target.maxPrice());
                buf.writeVarInt(target.enchants().size());
                for (EnchantRequirement requirement : target.enchants()) {
                    buf.writeResourceLocation(requirement.id());
                    buf.writeVarInt(requirement.minLevel());
                }
            }
            buf.writeBoolean(value.matchAny);
        }

        @Override
        public SearchTradesPayload decode(RegistryFriendlyByteBuf buf) {
            int targetCount = buf.readVarInt();
            List<TargetEntry> targets = new ArrayList<>(targetCount);
            for (int i = 0; i < targetCount; i++) {
                ResourceLocation id = buf.readResourceLocation();
                int minCount = buf.readVarInt();
                int maxPrice = buf.readVarInt();
                int enchantCount = buf.readVarInt();
                List<EnchantRequirement> enchants = new ArrayList<>(enchantCount);
                for (int j = 0; j < enchantCount; j++) {
                    enchants.add(new EnchantRequirement(buf.readResourceLocation(), buf.readVarInt()));
                }
                targets.add(new TargetEntry(id, enchants, minCount, maxPrice));
            }
            return new SearchTradesPayload(targets, buf.readBoolean());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}