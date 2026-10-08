package com.liuyue.autoTradeCycling.client.command;

import com.liuyue.autoTradeCycling.client.manager.AutoTradeManager;
import com.liuyue.autoTradeCycling.common.TradeTargets;
import com.liuyue.autoTradeCycling.common.TradeTargets.EnchantRequirement;
import com.liuyue.autoTradeCycling.common.TradeTargets.TargetEntry;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** 客户端 /autotrade 命令，用于增删与查询交易目标。 */
public class AutoTradeCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

            var addItemNode = ClientCommandManager.literal("item")
                    .then(ClientCommandManager.argument("minCount", IntegerArgumentType.integer(1, 64))
                            .then(ClientCommandManager.argument("maxPrice", IntegerArgumentType.integer(1, 64))
                                    .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                            .suggests(ITEM_SUGGESTIONS)
                                            .executes(AutoTradeCommand::executeAddItem))));

            var addEnchNode = ClientCommandManager.literal("enchantment")
                    .then(ClientCommandManager.argument("minLevel", IntegerArgumentType.integer(1, 5))
                            .then(ClientCommandManager.argument("maxPrice", IntegerArgumentType.integer(1, 64))
                                    .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                            .suggests(ENCHANTMENT_SUGGESTIONS)
                                            .executes(AutoTradeCommand::executeAddEnchantment))));

            var addItemEnchNode = ClientCommandManager.literal("itemEnchantment")
                    .then(ClientCommandManager.argument("minLevel", IntegerArgumentType.integer(1, 5))
                            .then(ClientCommandManager.argument("itemId", StringArgumentType.string())
                                    .suggests((ctx, builder) -> {
                                        AutoTradeManager.getInstance().getTargets().stream()
                                                .filter(t -> !t.isEnchantedBook())
                                                .map(t -> "\"" + t.id().toString() + "\"")
                                                .forEach(builder::suggest);
                                        return builder.buildFuture();
                                    })
                                    .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                            .suggests(ENCHANTMENT_SUGGESTIONS)
                                            .executes(AutoTradeCommand::executeAddItemEnchantment))));

            var addNode = ClientCommandManager.literal("add")
                    .then(addItemNode)
                    .then(addEnchNode)
                    .then(addItemEnchNode);

            var removeNode = ClientCommandManager.literal("remove")
                    .then(ClientCommandManager.argument("id", StringArgumentType.greedyString())
                            .suggests((ctx, builder) -> {
                                AutoTradeManager.getInstance().getTargets().stream()
                                        .filter(t -> !t.isEnchantedBook())
                                        .forEach(t -> builder.suggest(t.id().toString()));
                                return builder.buildFuture();
                            })
                            .executes(AutoTradeCommand::executeRemove));

            var removeBookNode = ClientCommandManager.literal("removeBook")
                    .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                            .suggests(BOOK_TARGET_SUGGESTIONS)
                            .executes(AutoTradeCommand::executeRemoveBook));

            var removeAllNode = ClientCommandManager.literal("removeAll")
                    .executes(AutoTradeCommand::executeRemoveAll);

            var listNode = ClientCommandManager.literal("list")
                    .executes(AutoTradeCommand::executeList);

            var startNode = ClientCommandManager.literal("start")
                    .executes(AutoTradeCommand::executeStart);

            var modeNode = ClientCommandManager.literal("mode")
                    .then(ClientCommandManager.literal("all").executes(ctx -> {
                        AutoTradeManager.getInstance().setMatchMode(AutoTradeManager.MatchMode.ALL);
                        send(ctx, "§a匹配模式: 全部");
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("any").executes(ctx -> {
                        AutoTradeManager.getInstance().setMatchMode(AutoTradeManager.MatchMode.ANY);
                        send(ctx, "§a匹配模式: 任一");
                        return 1;
                    }));

            var root = ClientCommandManager.literal("autoTradeCycling")
                    .then(addNode)
                    .then(removeNode)
                    .then(removeBookNode)
                    .then(removeAllNode)
                    .then(listNode)
                    .then(startNode)
                    .then(modeNode);

            dispatcher.register(root);
        });
    }

    private static int executeAddItem(CommandContext<FabricClientCommandSource> ctx) {
        int minCount = IntegerArgumentType.getInteger(ctx, "minCount");
        int maxPrice = IntegerArgumentType.getInteger(ctx, "maxPrice");
        String input = StringArgumentType.getString(ctx, "name");
        ResourceLocation id = findItem(input);
        if (id == null) { send(ctx, "§c找不到物品: " + input); return 0; }
        if (!AutoTradeManager.getInstance().addTarget(id, new java.util.ArrayList<>(), minCount, maxPrice)) {
            send(ctx, "§c该物品已存在，未重复添加: " + id);
            return 0;
        }
        Item item = BuiltInRegistries.ITEM.get(id).map(net.minecraft.core.Holder.Reference::value).orElse(null);
        String name = item != null ? item.getName(new ItemStack(item)).getString() : id.toString();
        send(ctx, "§a已添加: §e" + id + " §7(" + name + " x" + minCount + " 价格<=" + maxPrice + ")");
        send(ctx, "§7当前列表: " + formatTargets());
        return 1;
    }

    private static int executeAddEnchantment(CommandContext<FabricClientCommandSource> ctx) {
        int minLevel = IntegerArgumentType.getInteger(ctx, "minLevel");
        int maxPrice = IntegerArgumentType.getInteger(ctx, "maxPrice");
        String input = StringArgumentType.getString(ctx, "name");
        ResourceLocation enchId = findEnchantment(input);
        if (enchId == null) { send(ctx, "§c找不到附魔: " + input); return 0; }
        var req = new EnchantRequirement(enchId, minLevel);
        ResourceLocation bookId = BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.ENCHANTED_BOOK);
        if (!AutoTradeManager.getInstance().addTarget(bookId, java.util.List.of(req), 1, maxPrice)) {
            send(ctx, "§c该附魔的附魔书目标已存在，未重复添加（如需改等级请用 removeBook 后再加）");
            return 0;
        }
        send(ctx, "§a已添加: §e" + enchId + " §7(" + input + " 等级>=" + minLevel + " 价格<=" + maxPrice + ")");
        send(ctx, "§7当前列表: " + formatTargets());
        return 1;
    }

    private static int executeAddItemEnchantment(CommandContext<FabricClientCommandSource> ctx) {
        int minLevel = IntegerArgumentType.getInteger(ctx, "minLevel");
        String itemIdStr = StringArgumentType.getString(ctx, "itemId");
        String enchInput = StringArgumentType.getString(ctx, "name");
        ResourceLocation itemId = ResourceLocation.tryParse(itemIdStr);
        if (itemId == null) { send(ctx, "§c无效物品ID: " + itemIdStr); return 0; }
        ResourceLocation enchId = findEnchantment(enchInput);
        if (enchId == null) { send(ctx, "§c找不到附魔: " + enchInput); return 0; }
        var req = new EnchantRequirement(enchId, minLevel);
        if (AutoTradeManager.getInstance().addEnchantToItem(itemId, req)) {
            send(ctx, "§a已添加附魔要求: §e" + enchInput + " 等级>=" + minLevel
                    + " §7到 " + itemIdStr);
            send(ctx, "§7当前列表: " + formatTargets());
            return 1;
        }
        send(ctx, "§c找不到已添加的物品: " + itemIdStr + "，请先用 /autoTradeCycling add item 添加");
        return 0;
    }

    private static int executeRemove(CommandContext<FabricClientCommandSource> ctx) {
        String input = StringArgumentType.getString(ctx, "id");
        ResourceLocation id = ResourceLocation.tryParse(input);
        if (id == null) { send(ctx, "§c无效ID: " + input); return 0; }
        if (TradeTargets.isEnchantedBookId(id)) {
            send(ctx, "§c附魔书目标不能按物品 ID 移除，请用 /autoTradeCycling removeBook <附魔名>");
            return 0;
        }
        int removed = AutoTradeManager.getInstance().removeTarget(id);
        if (removed == 0) { send(ctx, "§c没有该物品的目标: " + id); return 0; }
        send(ctx, "§c已移除 " + removed + " 条: " + id);
        send(ctx, "§7当前列表: " + formatTargets());
        return 1;
    }

    private static int executeRemoveBook(CommandContext<FabricClientCommandSource> ctx) {
        String input = StringArgumentType.getString(ctx, "name");
        ResourceLocation enchantId = findEnchantment(input);
        if (enchantId == null) { send(ctx, "§c找不到附魔: " + input); return 0; }
        int removed = AutoTradeManager.getInstance().removeBookTarget(enchantId);
        if (removed == 0) { send(ctx, "§c没有该附魔的附魔书目标: " + input); return 0; }
        send(ctx, "§c已移除附魔书目标: " + input);
        send(ctx, "§7当前列表: " + formatTargets());
        return 1;
    }

    private static int executeRemoveAll(CommandContext<FabricClientCommandSource> ctx) {
        AutoTradeManager.getInstance().clearTargets();
        send(ctx, "§c已清空目标列表。");
        return 1;
    }

    private static int executeList(CommandContext<FabricClientCommandSource> ctx) {
        java.util.List<TargetEntry> targets = AutoTradeManager.getInstance().getTargets();
        if (targets.isEmpty()) send(ctx, "§7目标列表为空。");
        else { send(ctx, "§a目标 (" + targets.size() + "):"); send(ctx, "  " + formatTargets()); }
        return 1;
    }

    private static int executeStart(CommandContext<FabricClientCommandSource> ctx) {
        var targets = AutoTradeManager.getInstance().getTargets();
        if (targets.isEmpty()) { send(ctx, "§c列表为空！"); return 0; }
        AutoTradeManager.getInstance().start();
        send(ctx, "§a开始刷新！目标: " + formatTargets());
        send(ctx, "§7打开 1 级村民交易界面...");
        return 1;
    }

    private static ResourceLocation findItem(String input) {
        String lower = input.toLowerCase().trim();
        for (var e : BuiltInRegistries.ITEM.entrySet()) {
            if (e.getValue().getName(new ItemStack(e.getValue())).getString().toLowerCase().equals(lower))
                return e.getKey().location();
        }
        for (var e : BuiltInRegistries.ITEM.entrySet()) {
            if (e.getValue().getName(new ItemStack(e.getValue())).getString().toLowerCase().contains(lower))
                return e.getKey().location();
        }
        return null;
    }

    private static ResourceLocation findEnchantment(String input) {
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.level == null) return null;
        var registry = client.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        String lower = input.toLowerCase().trim();
        for (var e : registry.entrySet()) {
            if (e.getValue().description().getString().toLowerCase().equals(lower))
                return e.getKey().location();
        }
        for (var e : registry.entrySet()) {
            if (e.getValue().description().getString().toLowerCase().contains(lower))
                return e.getKey().location();
        }
        return null;
    }

    private static final SuggestionProvider<FabricClientCommandSource> BOOK_TARGET_SUGGESTIONS =
            (ctx, builder) -> {
                AutoTradeManager.getInstance().getTargets().stream()
                        .filter(TargetEntry::isEnchantedBook)
                        .flatMap(t -> t.enchants().stream())
                        .map(req -> "\"" + findEnchantmentName(req.id()) + "\"")
                        .forEach(builder::suggest);
                return builder.buildFuture();
            };

    private static final SuggestionProvider<FabricClientCommandSource> ENCHANTMENT_SUGGESTIONS =
            (ctx, builder) -> {
                String input = builder.getRemaining().toLowerCase();
                List<String> m = new ArrayList<>();
                var client = net.minecraft.client.Minecraft.getInstance();
                if (client.level == null) return builder.buildFuture();
                var registry = client.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                for (var e : registry.entrySet()) {
                    String name = e.getValue().description().getString();
                    if (name.toLowerCase().contains(input)) m.add(name);
                }
                m.stream().distinct().sorted().limit(20).forEach(builder::suggest);
                return builder.buildFuture();
            };

    private static final SuggestionProvider<FabricClientCommandSource> ITEM_SUGGESTIONS =
            (ctx, builder) -> {
                String input = builder.getRemaining().toLowerCase();
                List<String> m = new ArrayList<>();
                for (var e : BuiltInRegistries.ITEM.entrySet()) {
                    String name = e.getValue().getName(new ItemStack(e.getValue())).getString();
                    if (name.toLowerCase().contains(input)) m.add(name);
                }
                m.stream().distinct().sorted().limit(20).forEach(builder::suggest);
                return builder.buildFuture();
            };

    private static String formatTargets() {
        return AutoTradeManager.getInstance().getTargets().stream()
                .map(t -> {
                    String name;
                    if (t.isEnchantedBook()) {
                        name = t.enchants().isEmpty() ? t.id().toString() : findEnchantmentName(t.enchants().get(0).id());
                    } else {
                        var item = BuiltInRegistries.ITEM.get(t.id()).map(net.minecraft.core.Holder.Reference::value).orElse(null);
                        name = item != null ? item.getName(new ItemStack(item)).getString() : t.id().toString();
                    }
                    return name + "(x" + t.minCount() + " <=" + t.maxPrice() + ")";
                }).collect(Collectors.joining(", "));
    }

    private static String findEnchantmentName(ResourceLocation id) {
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.level == null) return id.getPath();
        var registry = client.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var ench = registry.get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT, id));
        return ench.map(e -> e.value().description().getString()).orElse(id.getPath());
    }

    private static void send(CommandContext<FabricClientCommandSource> ctx, String msg) {
        ctx.getSource().sendFeedback(Component.literal(msg));
    }
}
