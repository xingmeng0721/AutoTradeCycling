package com.liuyue.autoTradeCycling.client.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.liuyue.autoTradeCycling.common.SearchSpeed;
import com.liuyue.autoTradeCycling.common.TradeTargets.EnchantRequirement;
import com.liuyue.autoTradeCycling.common.TradeTargets.TargetEntry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 已选目标的本地存档（config/auto-trade-cycling.json）
 */
public final class TargetStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("auto-trade-cycling");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("auto-trade-cycling.json");

    private static boolean dirty = false;

    private TargetStore() {
    }

    public static void markDirty() {
        dirty = true;
    }

    public static void flushIfDirty() {
        if (!dirty) return;
        dirty = false;
        save();
    }

    public static void load() {
        if (!Files.exists(PATH)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(PATH, StandardCharsets.UTF_8)).getAsJsonObject();
            AutoTradeManager manager = AutoTradeManager.getInstance();

            if (root.has("matchMode")) {
                try {
                    manager.setMatchMode(AutoTradeManager.MatchMode.valueOf(root.get("matchMode").getAsString()));
                } catch (IllegalArgumentException ignored) {
                }
            }

            if (root.has("speed")) {
                manager.setSearchSpeed(SearchSpeed.byName(root.get("speed").getAsString(), manager.getSearchSpeed()));
            }

            List<TargetEntry> restored = new ArrayList<>();
            if (root.has("targets")) {
                for (JsonElement element : root.getAsJsonArray("targets")) {
                    TargetEntry entry = readEntry(element.getAsJsonObject());
                    if (entry != null) restored.add(entry);
                }
            }
            manager.restoreTargets(restored);

            List<Identifier> tradeableItems = new ArrayList<>();
            if (root.has("tradeableItems")) {
                for (JsonElement element : root.getAsJsonArray("tradeableItems")) {
                    Identifier id = Identifier.tryParse(element.getAsString());
                    if (id != null) tradeableItems.add(id);
                }
            }
            VillagerTradeData.restoreSynced(tradeableItems);

            dirty = false;
            LOGGER.info("已从存档恢复 {} 条目标", restored.size());
        } catch (Exception e) {
            LOGGER.warn("目标存档读取失败，已忽略", e);
        }
    }

    private static void save() {
        JsonObject root = new JsonObject();
        root.addProperty("matchMode", AutoTradeManager.getInstance().getMatchMode().name());
        root.addProperty("speed", AutoTradeManager.getInstance().getSearchSpeed().name());

        JsonArray array = new JsonArray();
        for (TargetEntry entry : AutoTradeManager.getInstance().getTargets()) {
            JsonObject object = new JsonObject();
            object.addProperty("id", entry.id().toString());
            object.addProperty("minCount", entry.minCount());
            object.addProperty("maxPrice", entry.maxPrice());

            JsonArray enchants = new JsonArray();
            for (EnchantRequirement requirement : entry.enchants()) {
                JsonObject enchant = new JsonObject();
                enchant.addProperty("id", requirement.id().toString());
                enchant.addProperty("minLevel", requirement.minLevel());
                enchants.add(enchant);
            }
            object.add("enchants", enchants);
            array.add(object);
        }
        root.add("targets", array);

        JsonArray tradeable = new JsonArray();
        for (Identifier id : VillagerTradeData.syncedItemsSnapshot()) {
            tradeable.add(id.toString());
        }
        root.add("tradeableItems", tradeable);

        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("目标存档写入失败", e);
        }
    }

    private static TargetEntry readEntry(JsonObject object) {
        if (!object.has("id")) return null;
        Identifier id = Identifier.tryParse(object.get("id").getAsString());
        if (id == null) return null;

        int minCount = object.has("minCount") ? Math.max(1, object.get("minCount").getAsInt()) : 1;
        int maxPrice = object.has("maxPrice") ? Math.max(1, object.get("maxPrice").getAsInt()) : 64;

        List<EnchantRequirement> enchants = new ArrayList<>();
        if (object.has("enchants")) {
            for (JsonElement element : object.getAsJsonArray("enchants")) {
                JsonObject enchant = element.getAsJsonObject();
                if (!enchant.has("id")) continue;
                Identifier enchantId = Identifier.tryParse(enchant.get("id").getAsString());
                if (enchantId == null) continue;
                int minLevel = enchant.has("minLevel") ? Math.max(1, enchant.get("minLevel").getAsInt()) : 1;
                enchants.add(new EnchantRequirement(enchantId, minLevel));
            }
        }
        return new TargetEntry(id, enchants, minCount, maxPrice);
    }
}
