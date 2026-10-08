package com.liuyue.autoTradeCycling.server;

import com.liuyue.autoTradeCycling.mixin.VillagerTradeAccessor;
import com.liuyue.autoTradeCycling.net.RequestTradeableItemsPayload;
import com.liuyue.autoTradeCycling.net.TradeableItemsPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.VillagerTrade;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 服务端读取村民交易注册表，下发给客户端可交易物品全集。
 */
public final class TradeableItemsServer {

    private TradeableItemsServer() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(RequestTradeableItemsPayload.TYPE, (payload, context) ->
                context.server().execute(() -> send(context.player())));
    }

    public static void send(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, TradeableItemsPayload.TYPE)) return;
        Registry<VillagerTrade> trades = player.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE);
        Set<Item> items = new HashSet<>();
        trades.stream().forEach(trade ->
                items.add(((VillagerTradeAccessor) trade).getGives().item().value()));
        items.add(Items.ENCHANTED_BOOK);
        items.add(Items.FILLED_MAP);
        items.add(Items.SUSPICIOUS_STEW);

        List<Identifier> ids = new ArrayList<>();
        for (Item item : items) {
            if (item == Items.AIR) continue;
            ids.add(BuiltInRegistries.ITEM.getKey(item));
        }
        ServerPlayNetworking.send(player, new TradeableItemsPayload(ids));
    }
}