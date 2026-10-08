package com.liuyue.autoTradeCycling.server;

import com.liuyue.autoTradeCycling.mixin.AbstractVillagerAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.ramixin.visibletraders.LockedTradeData;
import net.ramixin.visibletraders.ducks.VillagerDuck;

import java.util.ArrayList;
import java.util.List;

/**
 * Visible Traders 桥接：搜索命中后把这一份 2-5 级交易写回 VT，保证判定、锁定预览与将来升级看到的是同一份。
 */
public final class VisibleTradersServer {

    private VisibleTradersServer() {
    }

    /** 同步生成 level+1..5 每级的交易，顺序与 VT 的 LockedTradeData 一致。 */
    public static List<MerchantOffers> generateLockedLevels(Villager villager, ServerLevel level) {
        List<MerchantOffers> levels = new ArrayList<>();
        VillagerData data = villager.getVillagerData();
        for (int nextLevel = data.level() + 1; nextLevel <= 5; nextLevel++) {
            VillagerData next = data.withLevel(nextLevel);
            VillagerProfession profession = next.profession().value();
            ResourceKey<TradeSet> tradeSet = profession.getTrades(next.level());
            if (tradeSet == null) continue;
            MerchantOffers offers = new MerchantOffers();
            ((AbstractVillagerAccessor) villager).invokeAddOffersFromTradeSet(level, offers, tradeSet);
            levels.add(offers);
        }
        return levels;
    }

    /** 把命中的这一份锁定交易覆盖到 VT 的数据上，替换它自己异步生成的版本。 */
    public static void storeLockedTrades(Villager villager, List<MerchantOffers> levels) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, villager.registryAccess());
        output.store("LockedOffers", MerchantOffers.CODEC.listOf(), levels);
        CompoundTag tag = output.buildResult();
        LockedTradeData data = LockedTradeData.constructOrNull(
                TagValueInput.create(ProblemReporter.DISCARDING, villager.registryAccess(), tag), villager);
        if (data != null) {
            VillagerDuck.of(villager).visibleTraders$setLockedTradeData(data);
        }
    }

    public static void requestOffers(Villager villager, ServerPlayer player) {
        VillagerDuck.of(villager).visibleTraders$requestOffers(player);
    }
}