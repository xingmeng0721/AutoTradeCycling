package com.liuyue.autoTradeCycling.client.gui;

import com.liuyue.autoTradeCycling.client.manager.AutoTradeManager;
import com.liuyue.autoTradeCycling.client.manager.AutoTradeManager.MatchMode;
import com.liuyue.autoTradeCycling.client.manager.VillagerTradeData;
import com.liuyue.autoTradeCycling.common.TradeTargets.EnchantRequirement;
import com.liuyue.autoTradeCycling.common.TradeTargets.TargetEntry;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.CollapsibleContainer;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 图形化配置界面（owo-lib 版）。
 *
 * <p>布局自上而下：顶部标题；左「物品列表」+ 右「该物品可出现的附魔」（两个独立滚动列表）；
 * 通栏「附魔书可出现的附魔」；通栏「目标列表」（可展开、可逐条删附魔）；底部操作栏。
 *
 * <p>交互主线：先在左侧点选物品 → 右上区域直接列出该物品能出现的附魔 → 点「加入」即把附魔
 * 追加到该物品的目标上；附魔书单独走通栏表格。两个附魔区域都支持搜索框 + 滚动点选。
 */
public class AutoTradeConfigScreen extends BaseOwoScreen<FlowLayout> {

    // ------------------------------------------------------------------ 配色
    // 全部为不透明色，保证在游戏画面上文字始终清晰可读，不依赖背景虚化。

    /** 根背景：接近纯黑，让各面板之间的分界一眼可辨。 */
    private static final Color ROOT_BG = Color.ofArgb(0xFF0E0E12);
    /** 面板底色：深灰蓝，作为浅色正文的背景。 */
    private static final Color PANEL_BG = Color.ofArgb(0xFF1E1F27);
    /** 面板描边：比底色亮一档，用于区分相邻区域。 */
    private static final Color PANEL_BORDER = Color.ofArgb(0xFF3B3D4A);
    /** 列表行底色：比面板略亮，形成"列表区域"的层次。 */
    private static final Color ROW_BG = Color.ofArgb(0xFF262833);
    /** 悬停文字色：鼠标移到可点击项时提亮。 */
    private static final Color TEXT_HOVER = Color.ofArgb(0xFFFFFFFF);
    /** 选中行底色：偏蓝，与悬停态区分，表示当前选中物品。 */
    private static final Color ROW_SELECTED = Color.ofArgb(0xFF2F4C7A);
    /** 正文色：接近白，保证深色面板上的可读性。 */
    private static final Color TEXT = Color.ofArgb(0xFFE8E8E8);
    /** 次要文字：灰，用于提示、单位、等级区间。 */
    private static final Color DIM = Color.ofArgb(0xFF9AA0AA);
    /** 标题强调色：只用于区域标题，不作为正文。 */
    private static final Color TITLE = Color.ofArgb(0xFF7FD4FF);
    /** 正向状态色：用于"已添加"等提示。 */
    private static final Color ACTION = Color.ofArgb(0xFF8CE99A);

    /** 单次最多渲染的列表行数，避免"显示全部"时创建上千个组件。 */
    private static final int MAX_ROWS = 300;

    /** 一条附魔的可选信息。 */
    private record EnchOption(ResourceLocation id, String name, int maxLevel) {}

    // ------------------------------------------------------------------ 状态

    private boolean onlyTradeable = true;
    private ResourceLocation selectedItemId;
    private String selectedItemName = "";
    private String itemSearchText = "";
    private String itemEnchSearchText = "";
    private String bookEnchSearchText = "";

    /** 目标列表里哪些行处于展开状态（用 targetKey 记忆，重建列表后不丢失）。 */
    private final Set<String> expandedKeys = new HashSet<>();

    private final List<Item> allItems = new ArrayList<>();
    private final List<EnchOption> allEnchants = new ArrayList<>();

    // ------------------------------------------------------------------ 组件引用

    private FlowLayout itemListFlow;
    private FlowLayout itemEnchListFlow;
    private LabelComponent itemEnchTitle;
    private FlowLayout bookEnchListFlow;
    private FlowLayout targetListFlow;
    private LabelComponent targetCountLabel;
    private TextBoxComponent itemLevelBox;
    private TextBoxComponent bookLevelBox;
    private ButtonComponent startButton;
    private ButtonComponent modeButton;
    private ButtonComponent filterButton;

    public AutoTradeConfigScreen() {
        super(Component.literal("自动刷新交易 - 配置"));
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root.gap(5);
        root.padding(Insets.of(8));
        root.surface(Surface.flat(ROOT_BG.argb()));
        cacheRegistries();

        root.child(buildHeader());
        // 三个主体区域用 expand 百分比分配剩余高度（同一父容器内 expand 之和为 100 才是正确拆分）
        root.child(buildMainRow());
        root.child(buildBookPanel());
        root.child(buildTargetPanel());
        root.child(buildFooter());

        refreshItemList();
        refreshItemEnchantList();
        refreshBookEnchantList();
        rebuildTargets();
    }

    // ------------------------------------------------------------------ 顶部标题

    private FlowLayout buildHeader() {
        FlowLayout header = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        header.gap(10);
        header.verticalAlignment(VerticalAlignment.CENTER);
        header.child(Components.label(Component.literal("自动刷新交易")).color(TITLE));
        header.child(Components.label(Component.literal("左侧选物品 → 右侧点「加入」把附魔加到目标上")).color(DIM));
        return header;
    }

    // ------------------------------------------------------------------ 左上：物品列表 / 右上：该物品可出现的附魔

    private FlowLayout buildMainRow() {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.expand(44));
        row.gap(5);
        row.child(buildItemPanel());
        row.child(buildItemEnchantPanel());
        return row;
    }

    private FlowLayout buildItemPanel() {
        FlowLayout panel = Containers.verticalFlow(Sizing.fill(49), Sizing.fill(100));
        panel.gap(3);
        panel.padding(Insets.of(4));
        panel.surface(panelSurface());

        panel.child(Components.label(Component.literal("物品")).color(TITLE));

        FlowLayout searchRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        searchRow.gap(3);
        TextBoxComponent searchBox = Components.textBox(Sizing.expand());
        searchBox.setHint(Component.literal("搜索物品"));
        searchBox.setMaxLength(48);
        searchBox.onChanged().subscribe(text -> {
            this.itemSearchText = text;
            refreshItemList();
        });
        filterButton = Components.button(filterLabel(), button -> {
            if (VillagerTradeData.villagerItems().isEmpty()) return;
            this.onlyTradeable = !this.onlyTradeable;
            button.setMessage(filterLabel());
            refreshItemList();
        });
        searchRow.child(searchBox);
        searchRow.child(filterButton);
        panel.child(searchRow);

        itemListFlow = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        itemListFlow.gap(1);
        panel.child(Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), itemListFlow));
        return panel;
    }

    private FlowLayout buildItemEnchantPanel() {
        FlowLayout panel = Containers.verticalFlow(Sizing.fill(49), Sizing.fill(100));
        panel.gap(3);
        panel.padding(Insets.of(4));
        panel.surface(panelSurface());

        itemEnchTitle = Components.label(Component.literal("该物品可出现的附魔")).color(TITLE);
        panel.child(itemEnchTitle);

        FlowLayout searchRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        searchRow.gap(3);
        TextBoxComponent searchBox = Components.textBox(Sizing.expand());
        searchBox.setHint(Component.literal("搜索附魔"));
        searchBox.setMaxLength(48);
        searchBox.onChanged().subscribe(text -> {
            this.itemEnchSearchText = text;
            refreshItemEnchantList();
        });
        itemLevelBox = Components.textBox(Sizing.fixed(24));
        itemLevelBox.setMaxLength(2);
        itemLevelBox.text("1");
        searchRow.child(searchBox);
        searchRow.child(Components.label(Component.literal("等级")).color(DIM));
        searchRow.child(itemLevelBox);
        panel.child(searchRow);

        itemEnchListFlow = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        itemEnchListFlow.gap(1);
        panel.child(Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), itemEnchListFlow));
        return panel;
    }

    // ------------------------------------------------------------------ 通栏：附魔书可出现的附魔

    private FlowLayout buildBookPanel() {
        FlowLayout panel = Containers.verticalFlow(Sizing.fill(100), Sizing.expand(30));
        panel.gap(3);
        panel.padding(Insets.of(4));
        panel.surface(panelSurface());

        panel.child(Components.label(Component.literal("附魔书可出现的附魔")).color(TITLE));

        FlowLayout searchRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        searchRow.gap(3);
        TextBoxComponent searchBox = Components.textBox(Sizing.expand());
        searchBox.setHint(Component.literal("搜索附魔（附魔书）"));
        searchBox.setMaxLength(48);
        searchBox.onChanged().subscribe(text -> {
            this.bookEnchSearchText = text;
            refreshBookEnchantList();
        });
        bookLevelBox = Components.textBox(Sizing.fixed(24));
        bookLevelBox.setMaxLength(2);
        bookLevelBox.text("1");
        searchRow.child(searchBox);
        searchRow.child(Components.label(Component.literal("等级")).color(DIM));
        searchRow.child(bookLevelBox);
        panel.child(searchRow);

        bookEnchListFlow = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        bookEnchListFlow.gap(1);
        panel.child(Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), bookEnchListFlow));
        return panel;
    }

    // ------------------------------------------------------------------ 通栏：目标列表

    private FlowLayout buildTargetPanel() {
        FlowLayout panel = Containers.verticalFlow(Sizing.fill(100), Sizing.expand(26));
        panel.gap(3);
        panel.padding(Insets.of(4));
        panel.surface(panelSurface());

        targetCountLabel = Components.label(Component.literal("目标列表")).color(TITLE);
        panel.child(targetCountLabel);

        targetListFlow = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        targetListFlow.gap(2);
        panel.child(Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), targetListFlow));
        return panel;
    }

    // ------------------------------------------------------------------ 底部操作栏

    private FlowLayout buildFooter() {
        AutoTradeManager manager = AutoTradeManager.getInstance();
        FlowLayout footer = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        footer.gap(6);
        footer.verticalAlignment(VerticalAlignment.CENTER);

        startButton = Components.button(Component.literal(manager.isActive() ? "§c停止" : "§a开始"),
                button -> onStartStop());
        footer.child(startButton);

        modeButton = Components.button(modeLabel(), button -> {
            AutoTradeManager m = AutoTradeManager.getInstance();
            m.setMatchMode(m.getMatchMode() == MatchMode.ALL ? MatchMode.ANY : MatchMode.ALL);
            button.setMessage(modeLabel());
        });
        footer.child(modeButton);

        footer.child(Components.spacer());
        footer.child(Components.button(Component.literal("关闭"), button -> this.onClose()));
        return footer;
    }

    private Component modeLabel() {
        return Component.literal("匹配模式: "
                + (AutoTradeManager.getInstance().getMatchMode() == MatchMode.ALL ? "全部" : "任一"));
    }

    // ------------------------------------------------------------------ 物品列表

    private Component filterLabel() {
        if (VillagerTradeData.villagerItems().isEmpty()) return Component.literal("§8过滤不可用");
        return Component.literal(this.onlyTradeable ? "§a仅可交易" : "§c显示全部");
    }

    /** 重建左侧物品列表（搜索词、过滤开关、选中项变化时调用）。 */
    private void refreshItemList() {
        if (itemListFlow == null) return;
        itemListFlow.clearChildren();

        String query = itemSearchText.trim().toLowerCase(Locale.ROOT);
        Set<ResourceLocation> tradeable = onlyTradeable ? VillagerTradeData.villagerItems() : Set.of();

        int shown = 0;
        for (Item item : allItems) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (!tradeable.isEmpty() && !tradeable.contains(id)) continue;
            String name = item.getName(new ItemStack(item)).getString();
            if (!query.isEmpty()
                    && !id.toString().toLowerCase(Locale.ROOT).contains(query)
                    && !name.toLowerCase(Locale.ROOT).contains(query)) continue;
            if (shown >= MAX_ROWS) break;
            shown++;

            boolean selected = id.equals(selectedItemId);

            FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.gap(4);
            row.padding(Insets.of(2, 2, 3, 3));
            row.verticalAlignment(VerticalAlignment.CENTER);
            row.surface(Surface.flat((selected ? ROW_SELECTED : ROW_BG).argb()));
            row.cursorStyle(CursorStyle.HAND);
            row.child(Components.item(new ItemStack(item)));

            LabelComponent nameLabel = Components.label(Component.literal(name));
            nameLabel.color(selected ? TEXT_HOVER : TEXT);
            nameLabel.cursorStyle(CursorStyle.HAND);
            nameLabel.mouseEnter().subscribe(() -> nameLabel.color(TEXT_HOVER));
            nameLabel.mouseLeave().subscribe(() -> nameLabel.color(selected ? TEXT_HOVER : TEXT));
            nameLabel.mouseDown().subscribe((click, doubled) -> {
                selectItem(item);
                return true;
            });
            row.child(nameLabel);

            row.child(Components.spacer());
            if (hasItemTarget(id)) {
                row.child(Components.label(Component.literal("已添加")).color(ACTION));
            } else {
                // 也支持只买物品、不要求附魔（点这里直接建目标，之后还能在右侧追加附魔）
                row.child(Components.button(Component.literal("添加"), button -> addItemTarget(item)));
            }

            row.mouseDown().subscribe((click, doubled) -> {
                selectItem(item);
                return true;
            });
            itemListFlow.child(row);
        }

        if (shown == 0) {
            itemListFlow.child(Components.label(Component.literal("没有匹配的物品")).color(DIM));
        } else if (shown >= MAX_ROWS) {
            itemListFlow.child(Components.label(Component.literal("结果过多，请细化搜索")).color(DIM));
        }
    }

    /** 点选物品：只改变选中态，并刷新右上"该物品可出现的附魔"。 */
    private void selectItem(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id.equals(selectedItemId)) return;
        this.selectedItemId = id;
        this.selectedItemName = item.getName(new ItemStack(item)).getString();
        refreshItemList();
        refreshItemEnchantList();
    }

    /** 直接添加一个不带附魔要求的物品目标（物品行上的「添加」按钮）。 */
    private void addItemTarget(Item item) {
        AutoTradeManager manager = AutoTradeManager.getInstance();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        String name = item.getName(new ItemStack(item)).getString();
        if (manager.addTarget(id, new ArrayList<>(), 1, 64)) {
            this.selectedItemId = id;
            this.selectedItemName = name;
            expandedKeys.add(id.toString());
            chat("§a已添加物品目标: §e" + name + " §7(数量≥1 价格≤64)");
        } else {
            chat("§e" + name + " 已在目标列表中。");
        }
        refreshItemList();
        rebuildTargets();
        refreshItemEnchantList();
    }

    // ------------------------------------------------------------------ 该物品可出现的附魔

    /** 重建右上列表：只保留"能附在该物品上"且"村民卖的附魔装备会带它"的附魔。 */
    private void refreshItemEnchantList() {
        if (itemEnchListFlow == null) return;
        itemEnchListFlow.clearChildren();

        if (selectedItemId == null) {
            if (itemEnchTitle != null) itemEnchTitle.text(Component.literal("该物品可出现的附魔"));
            itemEnchListFlow.child(Components.label(Component.literal("请先在左侧选择物品")).color(DIM));
            return;
        }
        if (itemEnchTitle != null) {
            itemEnchTitle.text(Component.literal("该物品可出现的附魔: " + selectedItemName));
        }

        String query = itemEnchSearchText.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (EnchOption option : allEnchants) {
            if (!VillagerTradeData.canApplyTo(option.id(), selectedItemId)) continue;
            if (!VillagerTradeData.enchantOnTradedEquipment(option.id())) continue;
            if (!matchesQuery(option, query)) continue;
            if (shown >= MAX_ROWS) break;
            shown++;
            itemEnchListFlow.child(buildEnchantRow(option, true));
        }

        if (shown == 0) {
            itemEnchListFlow.child(Components.label(Component.literal("没有匹配的附魔")).color(DIM));
        }
    }

    // ------------------------------------------------------------------ 附魔书可出现的附魔

    /** 重建通栏附魔书列表：只保留村民的附魔书会出售的附魔。 */
    private void refreshBookEnchantList() {
        if (bookEnchListFlow == null) return;
        bookEnchListFlow.clearChildren();

        String query = bookEnchSearchText.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (EnchOption option : allEnchants) {
            if (!VillagerTradeData.enchantInBooks(option.id())) continue;
            if (!matchesQuery(option, query)) continue;
            if (shown >= MAX_ROWS) break;
            shown++;
            bookEnchListFlow.child(buildEnchantRow(option, false));
        }

        if (shown == 0) {
            bookEnchListFlow.child(Components.label(Component.literal("没有匹配的附魔")).color(DIM));
        }
    }

    /** 附魔行：附魔名 + 等级区间 + 「加入」按钮；整行也可点。 */
    private FlowLayout buildEnchantRow(EnchOption option, boolean forItem) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(4);
        row.padding(Insets.of(2, 2, 3, 3));
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.surface(Surface.flat(ROW_BG.argb()));
        row.cursorStyle(CursorStyle.HAND);

        Runnable action = () -> {
            if (forItem) addEnchantToSelectedItem(option);
            else addEnchantToBook(option);
        };

        LabelComponent name = Components.label(Component.literal(option.name()));
        name.color(TEXT);
        name.cursorStyle(CursorStyle.HAND);
        name.mouseEnter().subscribe(() -> name.color(TEXT_HOVER));
        name.mouseLeave().subscribe(() -> name.color(TEXT));
        name.mouseDown().subscribe((click, doubled) -> {
            action.run();
            return true;
        });

        row.child(name);
        row.child(Components.spacer());
        row.child(Components.label(Component.literal(levelRange(option))).color(DIM));
        row.child(Components.button(Component.literal("加入"), button -> action.run()));

        row.mouseDown().subscribe((click, doubled) -> {
            action.run();
            return true;
        });
        return row;
    }

    /** 把一条附魔加到"当前选中物品"的目标上（不存在目标就先建一个）。 */
    private void addEnchantToSelectedItem(EnchOption option) {
        if (selectedItemId == null) {
            chat("§c请先在左侧选择物品。");
            return;
        }
        // 校验：附魔既能附上去，村民卖的附魔装备也确实会带它，否则永远刷不出来
        if (!VillagerTradeData.canApplyTo(option.id(), selectedItemId)) {
            chat("§c" + option.name() + " 无法附在 " + selectedItemName + " 上。");
            return;
        }
        if (!VillagerTradeData.enchantOnTradedEquipment(option.id())) {
            chat("§c村民卖的附魔装备不会带 " + option.name() + "，永远刷不出来。");
            return;
        }
        if (hasEnchantOnItem(selectedItemId, option.id())) {
            chat("§e" + selectedItemName + " 已经有附魔要求 " + option.name() + " 了。");
            return;
        }

        AutoTradeManager manager = AutoTradeManager.getInstance();
        int level = clampLevel(option, readInt(itemLevelBox, 1));
        // 该物品还没有目标就先建一个（数量 1、价格 64），再往里追加附魔要求
        if (!hasItemTarget(selectedItemId)) manager.addTarget(selectedItemId, new ArrayList<>(), 1, 64);
        if (manager.addEnchantToItem(selectedItemId, new EnchantRequirement(option.id(), level))) {
            expandedKeys.add(selectedItemId.toString());
            chat("§a已为 §e" + selectedItemName + " §a添加附魔: §e" + option.name() + " 等级≥" + level);
            rebuildTargets();
        }
    }

    /** 把一条附魔加成新的附魔书目标（每个附魔书目标只带一条附魔要求）。 */
    private void addEnchantToBook(EnchOption option) {
        if (!VillagerTradeData.enchantInBooks(option.id())) {
            chat("§c村民的附魔书不出售 " + option.name() + "，永远刷不出来。");
            return;
        }
        int level = clampLevel(option, readInt(bookLevelBox, 1));

        List<EnchantRequirement> requirements = new ArrayList<>();
        requirements.add(new EnchantRequirement(option.id(), level));
        ResourceLocation bookId = BuiltInRegistries.ITEM.getKey(Items.ENCHANTED_BOOK);
        if (AutoTradeManager.getInstance().addTarget(bookId, requirements, 1, 64)) {
            chat("§a已添加附魔书目标: §e" + option.name() + " 等级≥" + level);
            rebuildTargets();
        } else {
            chat("§e该附魔书目标已存在。");
        }
    }

    // ------------------------------------------------------------------ 目标列表

    /** 整个目标列表按当前数据重建（结构变化后调用）。 */
    private void rebuildTargets() {
        if (targetListFlow == null) return;
        targetListFlow.clearChildren();

        List<TargetEntry> targets = AutoTradeManager.getInstance().getTargets();
        if (targetCountLabel != null) {
            targetCountLabel.text(Component.literal("目标列表 (" + targets.size() + ")"));
        }
        if (targets.isEmpty()) {
            targetListFlow.child(Components.label(Component.literal(
                    "还没有目标：在右上/下方附魔列表点「加入」即可创建并追加附魔")).color(DIM));
            return;
        }
        for (TargetEntry entry : new ArrayList<>(targets)) {
            targetListFlow.child(buildTargetRow(entry));
        }
    }

    /** 一个可展开的目标行：物品行（图标+名称+数量/价格+展开/删除），展开后是带单独删除按钮的附魔子行。 */
    private CollapsibleContainer buildTargetRow(TargetEntry entry) {
        final String key = targetKey(entry);
        CollapsibleContainer row = Containers.collapsible(
                Sizing.fill(100), Sizing.content(), Component.literal(targetTitle(entry)), expandedKeys.contains(key));
        row.onToggled().subscribe(nowExpanded -> {
            if (nowExpanded) expandedKeys.add(key);
            else expandedKeys.remove(key);
        });

        // 标题行原本是 [名称, 展开箭头]，把图标插到最前面
        ItemStack icon = entry.isEnchantedBook()
                ? new ItemStack(Items.ENCHANTED_BOOK)
                : BuiltInRegistries.ITEM.getOptional(entry.id()).map(ItemStack::new).orElse(ItemStack.EMPTY);
        row.titleLayout().child(0, Components.item(icon));

        TextBoxComponent countBox = Components.textBox(Sizing.fixed(26));
        countBox.setMaxLength(4);
        countBox.text(String.valueOf(entry.minCount()));
        TextBoxComponent priceBox = Components.textBox(Sizing.fixed(26));
        priceBox.setMaxLength(4);
        priceBox.text(String.valueOf(entry.maxPrice()));

        int insertAt = 2;
        row.titleLayout().child(insertAt++, Components.label(Component.literal("数量≥")).color(DIM));
        row.titleLayout().child(insertAt++, countBox);
        row.titleLayout().child(insertAt++, Components.label(Component.literal("价格≤")).color(DIM));
        row.titleLayout().child(insertAt++, priceBox);
        row.titleLayout().child(insertAt++, Components.button(Component.literal("展开/收起"),
                button -> row.toggleExpansion()));
        row.titleLayout().child(insertAt, Components.button(Component.literal("删除"), button -> {
            AutoTradeManager.getInstance().removeEntry(entry);
            expandedKeys.remove(key);
            rebuildTargets();
        }));

        // 输入框在构建时已经填过初值，这里再挂监听，避免初始化就触发一次写入
        countBox.onChanged().subscribe(value ->
                updateTargetCountPrice(entry, readInt(countBox, 1), readInt(priceBox, 64)));
        priceBox.onChanged().subscribe(value ->
                updateTargetCountPrice(entry, readInt(countBox, 1), readInt(priceBox, 64)));

        // 展开区：每条附魔要求一个子行，右侧带单独删除按钮
        for (EnchantRequirement requirement : new ArrayList<>(entry.enchants())) {
            FlowLayout sub = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            sub.gap(4);
            sub.verticalAlignment(VerticalAlignment.CENTER);
            sub.child(Components.label(Component.literal(
                    enchantName(requirement.id()) + "  等级≥" + requirement.minLevel())).color(TEXT));
            sub.child(Components.spacer());
            sub.child(Components.button(Component.literal("删除"), button -> {
                removeEnchant(entry, requirement.id());
                chat("§c已移除附魔: " + enchantName(requirement.id()));
                rebuildTargets();
            }));
            row.child(sub);
        }

        if (entry.enchants().isEmpty()) {
            row.child(Components.label(Component.literal("还没有附魔要求：去上方列表点「加入」")).color(DIM));
        }
        return row;
    }

    /**
     * 修改某个目标的数量/价格。
     * {@link TargetEntry} 是不可变记录，改值只能重建；用附魔列表的引用相等定位"同一个目标"，
     * 这样同一行连续编辑多次也能生效。
     */
    private void updateTargetCountPrice(TargetEntry entry, int count, int price) {
        AutoTradeManager manager = AutoTradeManager.getInstance();
        List<TargetEntry> targets = manager.getTargets();
        for (int i = 0; i < targets.size(); i++) {
            TargetEntry current = targets.get(i);
            boolean sameTarget = current == entry
                    || (current.id().equals(entry.id()) && current.enchants() == entry.enchants());
            if (!sameTarget) continue;
            if (current.minCount() == count && current.maxPrice() == price) return;
            targets.remove(i);
            manager.addTarget(entry.id(), entry.enchants(), count, price);
            return;
        }
    }

    private void removeEnchant(TargetEntry entry, ResourceLocation enchantId) {
        if (entry.isEnchantedBook()) {
            entry.enchants().removeIf(requirement -> requirement.id().equals(enchantId));
        } else {
            AutoTradeManager.getInstance().removeEnchantFromItem(entry.id(), enchantId);
        }
    }

    private static String targetKey(TargetEntry entry) {
        if (entry.isEnchantedBook()) {
            // 附魔书目标的物品 id 都相同，用附魔集合区分
            return entry.id() + "|" + entry.enchants().stream()
                    .map(requirement -> requirement.id().toString())
                    .sorted().collect(Collectors.joining(","));
        }
        return entry.id().toString();
    }

    // ------------------------------------------------------------------ 开始/停止

    private void onStartStop() {
        AutoTradeManager manager = AutoTradeManager.getInstance();
        if (manager.isActive()) {
            manager.cancel();
            chat("§c已停止自动刷新。");
            if (startButton != null) startButton.setMessage(Component.literal("§a开始"));
            return;
        }
        if (manager.getTargets().isEmpty()) {
            chat("§c目标列表为空，请先添加目标。");
            return;
        }
        manager.start();
        if (!manager.isActive()) {
            chat("§c目标全部不可能刷出，已被排除，未开始。");
            return;
        }
        chat("§a已开始，请右键打开 1 级村民的交易界面。");
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ 数据与工具

    private void cacheRegistries() {
        allItems.clear();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            allItems.add(item);
        }
        allItems.sort(Comparator.comparing(item -> item.getName(new ItemStack(item)).getString()));

        allEnchants.clear();
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        var lookup = client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        lookup.listElements().forEach(holder -> allEnchants.add(new EnchOption(
                holder.key().location(),
                holder.value().description().getString(),
                holder.value().getMaxLevel())));
        allEnchants.sort(Comparator.comparing(EnchOption::name));
    }

    /** 建立面板底色：不透明填充 + 描边，保证文字对比度。 */
    private static Surface panelSurface() {
        return Surface.flat(PANEL_BG.argb()).and(Surface.outline(PANEL_BORDER.argb()));
    }

    private static boolean matchesQuery(EnchOption option, String lowerQuery) {
        return lowerQuery.isEmpty()
                || option.name().toLowerCase(Locale.ROOT).contains(lowerQuery)
                || option.id().toString().toLowerCase(Locale.ROOT).contains(lowerQuery);
    }

    private static String levelRange(EnchOption option) {
        return option.maxLevel() <= 1 ? "1级" : "1-" + option.maxLevel() + "级";
    }

    /** 等级超过该附魔上限时钳到上限（上限查不到 0 时不钳）。 */
    private static int clampLevel(EnchOption option, int level) {
        int max = option.maxLevel() > 0 ? option.maxLevel() : level;
        return Math.max(1, Math.min(level, max));
    }

    private boolean hasItemTarget(ResourceLocation id) {
        for (TargetEntry target : AutoTradeManager.getInstance().getTargets()) {
            if (!target.isEnchantedBook() && target.id().equals(id)) return true;
        }
        return false;
    }

    private boolean hasEnchantOnItem(ResourceLocation itemId, ResourceLocation enchantId) {
        for (TargetEntry target : AutoTradeManager.getInstance().getTargets()) {
            if (target.isEnchantedBook() || !target.id().equals(itemId)) continue;
            for (EnchantRequirement requirement : target.enchants()) {
                if (requirement.id().equals(enchantId)) return true;
            }
        }
        return false;
    }

    private String targetTitle(TargetEntry entry) {
        if (entry.isEnchantedBook()) {
            if (entry.enchants().isEmpty()) return "附魔书";
            return entry.enchants().stream()
                    .map(requirement -> enchantName(requirement.id()))
                    .collect(Collectors.joining(" + "));
        }
        return itemName(entry.id());
    }

    private String enchantName(ResourceLocation id) {
        for (EnchOption option : allEnchants) {
            if (option.id().equals(id)) return option.name();
        }
        return id.getPath();
    }

    private static String itemName(ResourceLocation id) {
        return BuiltInRegistries.ITEM.getOptional(id)
                .map(item -> item.getName(new ItemStack(item)).getString())
                .orElse(id.toString());
    }

    private static int readInt(TextBoxComponent box, int fallback) {
        try {
            int value = Integer.parseInt(box.getValue().trim());
            return value < 1 ? fallback : value;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private void chat(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) client.player.displayClientMessage(Component.literal(message), false);
    }
}
