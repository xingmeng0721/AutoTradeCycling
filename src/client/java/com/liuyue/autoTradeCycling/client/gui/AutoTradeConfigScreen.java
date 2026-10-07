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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 现代化图形配置界面（owo-lib 标签页版）。
 *
 * <h3>界面特性</h3>
 * <ul>
 *   <li>标签页设计：装备物品、附魔书、已选目标三个独立区域</li>
 *   <li>行内等级选择：点击附魔时在行内展开等级按钮组，直观快捷</li>
 *   <li>就地编辑：卡片内嵌选择器，数量/价格/等级全部原地修改</li>
 *   <li>状态保持：选中物品不重建列表，点击不会让滚动位置回弹</li>
 *   <li>视觉优化：鲜艳配色、渐变背景、悬停反馈、色彩分层</li>
 * </ul>
 */
public class AutoTradeConfigScreen extends BaseOwoScreen<FlowLayout> {

    // ------------------------------------------------------------------ 现代配色方案
    // 深色主题 + 鲜艳点缀色，不刺眼但有活力。

    /** 根背景：深紫蓝渐变基调。 */
    private static final Color ROOT_BG = Color.ofArgb(0xFF0F111A);
    /** 主面板背景：深蓝紫，科技感。 */
    private static final Color PANEL_BG = Color.ofArgb(0xFF1A1D2E);
    /** 次级面板（卡片）：略浅的蓝灰。 */
    private static final Color CARD_BG = Color.ofArgb(0xFF16213E);
    /** 面板描边：亮蓝色半透明，形成发光边框。 */
    private static final Color PANEL_BORDER = Color.ofArgb(0x664FC3F7);
    /** 列表行底色：深蓝，内凹感。 */
    private static final Color ROW_BG = Color.ofArgb(0xFF0E1621);
    /** 列表行悬停：靛蓝，明显反馈。 */
    private static final Color ROW_HOVER = Color.ofArgb(0xFF1E3A5F);
    /** 选中行：亮青蓝，表示焦点。 */
    private static final Color ROW_SELECTED = Color.ofArgb(0xFF2A5298);
    /** 选中行悬停：更亮的蓝。 */
    private static final Color ROW_SELECTED_HOVER = Color.ofArgb(0xFF3666BB);
    /** 已加入目标的行：青绿色，积极状态。 */
    private static final Color ROW_ADDED = Color.ofArgb(0xFF1B4D3E);
    /** 已加入目标悬停：亮青绿。 */
    private static final Color ROW_ADDED_HOVER = Color.ofArgb(0xFF26614F);
    /** 内嵌选择器背景：更深。 */
    private static final Color PICKER_BG = Color.ofArgb(0xFF0A0C14);
    /** 等级选择器背景：高亮青蓝，吸引注意。 */
    private static final Color LEVEL_PICKER_BG = Color.ofArgb(0xFF1A2E4A);
    /** 标题/强调色：亮青色，醒目不刺眼。 */
    private static final Color TITLE = Color.ofArgb(0xFF4DD0E1);
    /** 主文字：柔和白。 */
    private static final Color TEXT = Color.ofArgb(0xFFE8EAF6);
    /** 次要文字：淡灰蓝。 */
    private static final Color DIM = Color.ofArgb(0xFF9FA8DA);
    /** 积极状态色：明亮青绿。 */
    private static final Color ACTION = Color.ofArgb(0xFF4AE5B0);
    /** 警告色：柔和的珊瑚红。 */
    private static final Color DANGER = Color.ofArgb(0xFFFF6B9D);

    /** 新建目标的默认数量/价格。 */
    private static final int DEFAULT_MIN_COUNT = 1;
    private static final int DEFAULT_MAX_PRICE = 64;
    /** 单次最多渲染的列表行数。 */
    private static final int MAX_ROWS = 300;

    private static final int TAB_ITEM = 0;
    private static final int TAB_BOOK = 1;
    private static final int TAB_TARGET = 2;

    /** 一条附魔的可选信息。 */
    private record EnchOption(ResourceLocation id, String name, int maxLevel) {}

    /** 物品行的组件引用，用于就地重绘。 */
    private static final class ItemRow {
        final FlowLayout row;
        final LabelComponent name;
        final ButtonComponent state;
        ItemRow(FlowLayout row, LabelComponent name, ButtonComponent state) {
            this.row = row;
            this.name = name;
            this.state = state;
        }
    }

    /** 附魔行的组件引用（支持行内展开等级选择器）。 */
    private static final class EnchRow {
        final FlowLayout row;
        final LabelComponent name;
        final ButtonComponent button;
        final FlowLayout levelPicker; // 等级选择器容器（展开时可见）
        EnchRow(FlowLayout row, LabelComponent name, ButtonComponent button, FlowLayout levelPicker) {
            this.row = row;
            this.name = name;
            this.button = button;
            this.levelPicker = levelPicker;
        }
    }

    // ------------------------------------------------------------------ 状态

    private int activeTab = TAB_ITEM;
    private boolean onlyTradeable = true;
    private ResourceLocation selectedItemId;
    private String selectedItemName = "";
    private String itemSearchText = "";
    private String itemEnchSearchText = "";
    private String bookEnchSearchText = "";

    /** 卡片内嵌选择器的搜索词（重建卡片后仍保留）。 */
    private String pickerSearch = "";
    /** 内嵌附魔选择器展开在哪张卡片上，null 表示都收起。 */
    private String openPickerKey;
    /** 目标列表里哪些卡片处于展开状态。 */
    private final Set<String> expandedCards = new HashSet<>();
    /** 「清空」按钮的两段式确认状态。 */
    private boolean confirmClear = false;

    /** 当前展开等级选择器的附魔 ID（物品附魔列表）。 */
    private ResourceLocation expandedItemEnchant;
    /** 当前展开等级选择器的附魔 ID（附魔书列表）。 */
    private ResourceLocation expandedBookEnchant;

    private final List<Item> allItems = new ArrayList<>();
    private final List<EnchOption> allEnchants = new ArrayList<>();
    private final Map<ResourceLocation, EnchOption> enchById = new HashMap<>();

    // ------------------------------------------------------------------ 组件引用

    private FlowLayout contentArea;
    private final ButtonComponent[] tabButtons = new ButtonComponent[3];
    private ButtonComponent startButton;
    private ButtonComponent modeButton;
    private LabelComponent itemEnchTitle;
    private FlowLayout itemListFlow;
    private FlowLayout itemEnchListFlow;
    private FlowLayout bookListFlow;
    private FlowLayout targetListFlow;
    private final Map<ResourceLocation, ItemRow> itemRows = new HashMap<>();
    private final Map<ResourceLocation, EnchRow> itemEnchRows = new HashMap<>();
    private final Map<ResourceLocation, EnchRow> bookRows = new HashMap<>();

    public AutoTradeConfigScreen() {
        super(Component.literal("自动刷新交易"));
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root.gap(0);
        root.padding(Insets.of(0));
        root.surface(Surface.flat(ROOT_BG.argb()));
        cacheRegistries();

        // 主界面容器：顶部栏 + 内容 + 底栏
        FlowLayout main = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        main.gap(0);
        main.padding(Insets.of(10, 12, 10, 12));
        main.child(buildHeader());

        contentArea = Containers.verticalFlow(Sizing.fill(100), Sizing.expand(100));
        main.child(contentArea);

        main.child(buildFooter());
        root.child(main);

        switchTab(TAB_ITEM);
    }

    // ------------------------------------------------------------------ 顶部：标题 + 标签页

    private FlowLayout buildHeader() {
        FlowLayout header = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        header.gap(12);
        header.padding(Insets.bottom(10));
        header.verticalAlignment(VerticalAlignment.CENTER);

        LabelComponent title = Components.label(Component.literal("§l自动刷新交易")).color(TITLE);
        header.child(title);

        header.child(horizontalSpacer());

        // 三个标签页按钮，当前项高亮
        for (int tab = 0; tab < 3; tab++) {
            final int which = tab;
            tabButtons[tab] = createTabButton(which);
            header.child(tabButtons[tab]);
        }
        return header;
    }

    private ButtonComponent createTabButton(int which) {
        ButtonComponent btn = Components.button(Component.empty(), b -> switchTab(which));
        btn.sizing(Sizing.content(), Sizing.fixed(24));
        return btn;
    }

    /** 切换标签页。重复点当前页不重建，滚动位置不丢。 */
    private void switchTab(int tab) {
        if (this.activeTab == tab && contentArea.children().size() > 0) {
            updateTabLabels();
            return;
        }
        this.activeTab = tab;
        // 切换页面时收起所有等级选择器
        expandedItemEnchant = null;
        expandedBookEnchant = null;
        contentArea.clearChildren();
        switch (tab) {
            case TAB_BOOK -> contentArea.child(buildBookTab());
            case TAB_TARGET -> contentArea.child(buildTargetsTab());
            default -> contentArea.child(buildItemTab());
        }
        updateTabLabels();
    }

    /** 刷新标签页按钮文字（当前项加 § 高亮，目标页显示徽标）。 */
    private void updateTabLabels() {
        int count = AutoTradeManager.getInstance().getTargets().size();
        tabButtons[TAB_ITEM].setMessage(Component.literal(
                activeTab == TAB_ITEM ? "§l§e装备物品" : "§7装备物品"));
        tabButtons[TAB_BOOK].setMessage(Component.literal(
                activeTab == TAB_BOOK ? "§l§e附魔书" : "§7附魔书"));
        String targetLabel = count > 0 ? "§a已选目标 §f(" + count + ")" : "§7已选目标 (0)";
        if (activeTab == TAB_TARGET) targetLabel = "§l§e" + targetLabel;
        tabButtons[TAB_TARGET].setMessage(Component.literal(targetLabel));
    }

    // ------------------------------------------------------------------ 标签页一：装备物品

    private FlowLayout buildItemTab() {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fill(100));
        row.gap(8);
        row.child(buildItemPanel());
        row.child(buildItemEnchPanel());
        return row;
    }

    private FlowLayout buildItemPanel() {
        FlowLayout panel = Containers.verticalFlow(Sizing.fill(49), Sizing.fill(100));
        panel.gap(6);
        panel.padding(Insets.of(8));
        panel.surface(cardSurface());

        FlowLayout titleRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        titleRow.gap(6);
        titleRow.verticalAlignment(VerticalAlignment.CENTER);
        titleRow.child(Components.label(Component.literal("§l物品列表")).color(TITLE));
        titleRow.child(horizontalSpacer());
        ButtonComponent filter = createFilterButton();
        titleRow.child(filter);
        panel.child(titleRow);

        TextBoxComponent searchBox = Components.textBox(Sizing.expand());
        searchBox.setHint(Component.literal("🔍 搜索物品名称或 ID"));
        searchBox.setMaxLength(48);
        searchBox.text(itemSearchText);
        searchBox.onChanged().subscribe(text -> {
            this.itemSearchText = text;
            rebuildItemList();
        });
        FlowLayout searchRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        searchRow.child(searchBox);
        panel.child(searchRow);

        itemListFlow = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        itemListFlow.gap(2);
        panel.child(Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), itemListFlow));

        rebuildItemList();
        return panel;
    }

    private ButtonComponent createFilterButton() {
        ButtonComponent filter = smallButton(filterLabel(), b -> {
            this.onlyTradeable = !this.onlyTradeable;
            b.setMessage(filterLabel());
            rebuildItemList();
        });
        filter.active(!VillagerTradeData.villagerItems().isEmpty());
        filter.tooltip(Component.literal("只显示村民可能出售的物品"));
        return filter;
    }

    private Component filterLabel() {
        if (VillagerTradeData.villagerItems().isEmpty()) return Component.literal("§8过滤不可用");
        return Component.literal(this.onlyTradeable ? "§a✓ 仅可交易" : "§7○ 显示全部");
    }

    private FlowLayout buildItemEnchPanel() {
        FlowLayout panel = Containers.verticalFlow(Sizing.fill(49), Sizing.fill(100));
        panel.gap(6);
        panel.padding(Insets.of(8));
        panel.surface(cardSurface());

        itemEnchTitle = Components.label(Component.literal("§l附魔")).color(TITLE);
        panel.child(itemEnchTitle);

        TextBoxComponent searchBox = Components.textBox(Sizing.expand());
        searchBox.setHint(Component.literal("🔍 搜索附魔"));
        searchBox.setMaxLength(48);
        searchBox.text(itemEnchSearchText);
        searchBox.onChanged().subscribe(text -> {
            this.itemEnchSearchText = text;
            refreshItemEnchList();
        });
        FlowLayout searchRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        searchRow.child(searchBox);
        panel.child(searchRow);

        panel.child(Components.label(Component.literal("§7点击附魔展开等级选择")).color(DIM));

        itemEnchListFlow = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        itemEnchListFlow.gap(2);
        panel.child(Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), itemEnchListFlow));

        refreshItemEnchList();
        return panel;
    }

    /** 重建左侧物品列表。只在搜索词/过滤开关变化时调用。 */
    private void rebuildItemList() {
        if (itemListFlow == null) return;
        itemRows.clear();
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
            itemListFlow.child(buildItemRow(item, id, name));
        }

        if (shown == 0) {
            itemListFlow.child(Components.label(Component.literal("§7没有匹配的物品")).color(DIM));
        } else if (shown >= MAX_ROWS) {
            itemListFlow.child(Components.label(Component.literal("§e结果过多，请细化搜索")).color(DIM));
        }
    }

    private FlowLayout buildItemRow(Item item, ResourceLocation id, String name) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(6);
        row.padding(Insets.of(4, 6, 4, 6));
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.cursorStyle(CursorStyle.HAND);
        row.tooltip(Component.literal(id.toString()));

        row.child(Components.item(new ItemStack(item)));
        LabelComponent nameLabel = Components.label(Component.literal(name));
        row.child(nameLabel);
        row.child(horizontalSpacer());

        ButtonComponent state = smallButton(Component.literal(""), b -> onItemStateButton(item, id));
        row.child(state);

        row.mouseDown().subscribe((click, doubled) -> {
            selectItem(item);
            return true;
        });
        row.mouseEnter().subscribe(() -> paintItemRow(id, true));
        row.mouseLeave().subscribe(() -> paintItemRow(id, false));

        itemRows.put(id, new ItemRow(row, nameLabel, state));
        paintItemRow(id, false);
        return row;
    }

    /** 就地重绘一行物品（选中/已添加/悬停三种状态叠加）。 */
    private void paintItemRow(ResourceLocation id, boolean hovered) {
        ItemRow row = itemRows.get(id);
        if (row == null) return;
        boolean selected = id.equals(selectedItemId);
        boolean added = hasItemTarget(id);

        Color background;
        if (added) background = hovered ? ROW_ADDED_HOVER : ROW_ADDED;
        else if (selected) background = hovered ? ROW_SELECTED_HOVER : ROW_SELECTED;
        else background = hovered ? ROW_HOVER : ROW_BG;

        row.row.surface(roundedSurface(background));
        row.name.color(selected || hovered ? TEXT : DIM);
        if (added) {
            row.state.setMessage(Component.literal("§a✓ 已选"));
            row.state.tooltip(Component.literal("已有该物品的目标，点击查看"));
        } else {
            row.state.setMessage(Component.literal("§b＋ 添加"));
            row.state.tooltip(Component.literal("添加为不带附魔要求的目标"));
        }
    }

    private void refreshItemRowStates() {
        for (ResourceLocation id : itemRows.keySet()) paintItemRow(id, false);
    }

    /** 点选物品：不重建左侧列表，只重绘行样式 + 重建右侧附魔列表。 */
    private void selectItem(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id.equals(selectedItemId)) return;
        ResourceLocation previous = selectedItemId;
        this.selectedItemId = id;
        this.selectedItemName = item.getName(new ItemStack(item)).getString();
        // 切换选中物品时收起等级选择器
        expandedItemEnchant = null;
        if (previous != null) paintItemRow(previous, false);
        paintItemRow(id, false);
        refreshItemEnchList();
    }

    private void onItemStateButton(Item item, ResourceLocation id) {
        if (hasItemTarget(id)) {
            expandedCards.add(cardKeyForItem(id));
            switchTab(TAB_TARGET);
            return;
        }
        AutoTradeManager manager = AutoTradeManager.getInstance();
        if (manager.addTarget(id, new ArrayList<>(), DEFAULT_MIN_COUNT, DEFAULT_MAX_PRICE)) {
            expandedCards.add(cardKeyForItem(id));
        }
        selectItem(item);
        refreshItemRowStates();
        updateTabLabels();
    }

    // ------------------------------------------------------------------ 物品附魔列表（右侧）

    /** 重建右侧附魔列表。 */
    private void refreshItemEnchList() {
        if (itemEnchListFlow == null) return;
        itemEnchRows.clear();
        itemEnchListFlow.clearChildren();

        if (allEnchants.isEmpty()) {
            if (itemEnchTitle != null) itemEnchTitle.text(Component.literal("§l附魔"));
            itemEnchListFlow.child(Components.label(
                    Component.literal("§7进入世界后才能列出附魔")).color(DIM));
            return;
        }
        if (selectedItemId == null) {
            if (itemEnchTitle != null) itemEnchTitle.text(Component.literal("§l附魔"));
            itemEnchListFlow.child(Components.label(
                    Component.literal("§7← 先在左侧选择物品")).color(DIM));
            return;
        }
        if (itemEnchTitle != null) {
            itemEnchTitle.text(Component.literal("§l附魔: §r" + selectedItemName));
        }

        String query = itemEnchSearchText.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (EnchOption option : allEnchants) {
            if (!VillagerTradeData.canApplyTo(option.id(), selectedItemId)) continue;
            if (!VillagerTradeData.enchantOnTradedEquipment(option.id())) continue;
            if (!matchesQuery(option, query)) continue;
            if (shown >= MAX_ROWS) break;
            shown++;
            buildEnchRow(itemEnchRows, option, true);
        }

        if (shown == 0) {
            itemEnchListFlow.child(Components.label(
                    Component.literal("§7该物品没有可交易的附魔")).color(DIM));
        }
    }

    /** 构造一条附魔行（支持行内展开等级选择器）。 */
    private void buildEnchRow(Map<ResourceLocation, EnchRow> rows, EnchOption option, boolean forItem) {
        FlowLayout listFlow = forItem ? itemEnchListFlow : bookListFlow;
        
        // 垂直容器：主行 + 等级选择器
        FlowLayout container = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        container.gap(2);
        
        // 主行
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(6);
        row.padding(Insets.of(4, 6, 4, 6));
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.cursorStyle(CursorStyle.HAND);
        row.tooltip(Component.literal(option.id().toString()));

        LabelComponent name = Components.label(Component.literal(option.name()));
        row.child(name);
        row.child(horizontalSpacer());
        row.child(Components.label(Component.literal("§8" + levelRange(option))).color(DIM));

        // ButtonComponent button = smallButton(Component.literal(""), b -> toggleEnchantLevelPicker(option, forItem));
        ButtonComponent button = tinyButton( "§b＋" , b -> toggleEnchantLevelPicker(option, forItem));
        row.child(button);

        row.mouseDown().subscribe((click, doubled) -> {
            toggleEnchantLevelPicker(option, forItem);
            return true;
        });
        row.mouseEnter().subscribe(() -> paintEnchRow(rows, option.id(), true));
        row.mouseLeave().subscribe(() -> paintEnchRow(rows, option.id(), false));

        container.child(row);
        
        // 等级选择器（初始隐藏）
        FlowLayout levelPicker = buildLevelPicker(option, forItem);
        container.child(levelPicker);

        rows.put(option.id(), new EnchRow(row, name, button, levelPicker));
        listFlow.child(container);
        paintEnchRow(rows, option.id(), false);
    }

    /** 构建等级选择器（1 到 maxLevel 的按钮组）。 */
    private FlowLayout buildLevelPicker(EnchOption option, boolean forItem) {
        FlowLayout picker = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        picker.gap(4);
        picker.padding(Insets.of(6));
        picker.surface(Surface.flat(LEVEL_PICKER_BG.argb()));
        picker.verticalAlignment(VerticalAlignment.CENTER);
        
        picker.child(Components.label(Component.literal("§e等级:")).color(TITLE));
        
        int maxLevel = option.maxLevel() > 0 ? option.maxLevel() : 10;
        for (int level = 1; level <= maxLevel; level++) {
            final int lv = level;
            ButtonComponent btn = Components.button(Component.literal("§b" + lv), b -> {
                selectEnchantLevel(option, lv, forItem);
            });
            btn.sizing(Sizing.fixed(28), Sizing.fixed(28));
            picker.child(btn);
        }
        
        return picker;
    }

    /** 切换附魔的等级选择器展开状态。 */
    private void toggleEnchantLevelPicker(EnchOption option, boolean forItem) {
        // 已加入时直接移除，不展开选择器
        if (forItem) {
            if (selectedItemId == null) return;
            TargetEntry entry = AutoTradeManager.getInstance().findItemTarget(selectedItemId);
            if (entry != null && hasRequirement(entry, option.id())) {
                AutoTradeManager.getInstance().removeEnchant(entry, option.id());
                refreshItemRowStates();
                refreshItemEnchList();
                updateTabLabels();
                return;
            }
        } else {
            TargetEntry existing = AutoTradeManager.getInstance().findBookTarget(option.id());
            if (existing != null) {
                AutoTradeManager.getInstance().removeEntry(existing);
                rebuildBookList();
                updateTabLabels();
                return;
            }
        }

        // 切换展开状态：就地更新受影响的两行，不重建整表
        // （clearChildren() 会让列表内容高度瞬间归零，滚动位置会被 clamp 回顶部）
        Map<ResourceLocation, EnchRow> rows = forItem ? itemEnchRows : bookRows;
        ResourceLocation previous = forItem ? expandedItemEnchant : expandedBookEnchant;
        ResourceLocation now = option.id().equals(previous) ? null : option.id();

        if (forItem) {
            expandedItemEnchant = now;
        } else {
            expandedBookEnchant = now;
        }

        if (previous != null && !previous.equals(now)) {
            paintEnchRowButton(rows, previous);
        }
        if (now != null) {
            paintEnchRowButton(rows, now);
        }
    }

    /** 选择某个等级后加入目标。 */
    private void selectEnchantLevel(EnchOption option, int level, boolean forItem) {
        if (forItem) {
            if (selectedItemId == null) return;
            AutoTradeManager manager = AutoTradeManager.getInstance();
            TargetEntry entry = manager.findItemTarget(selectedItemId);
            if (entry == null) {
                manager.addTarget(selectedItemId, new ArrayList<>(), DEFAULT_MIN_COUNT, DEFAULT_MAX_PRICE);
            }
            manager.addEnchantToItem(selectedItemId, new EnchantRequirement(option.id(), level));
            expandedItemEnchant = null; // 选完收起
            refreshItemRowStates();
            refreshItemEnchList();
        } else {
            List<EnchantRequirement> requirements = new ArrayList<>();
            requirements.add(new EnchantRequirement(option.id(), level));
            AutoTradeManager.getInstance().addTarget(BuiltInRegistries.ITEM.getKey(Items.ENCHANTED_BOOK),
                    requirements, DEFAULT_MIN_COUNT, DEFAULT_MAX_PRICE);
            expandedBookEnchant = null;
            rebuildBookList();
        }
        updateTabLabels();
    }

    /** 就地重绘一条附魔行（含等级选择器的显示/隐藏）。 */
    private void paintEnchRow(Map<ResourceLocation, EnchRow> rows, ResourceLocation id, boolean hovered) {
        EnchRow row = rows.get(id);
        if (row == null) return;
        boolean added = rows == itemEnchRows
                ? hasEnchantOnItem(selectedItemId, id)
                : AutoTradeManager.getInstance().findBookTarget(id) != null;

        Color bg = added ? (hovered ? ROW_ADDED_HOVER : ROW_ADDED)
                : (hovered ? ROW_HOVER : ROW_BG);
        row.row.surface(roundedSurface(bg));
        row.name.color(added ? ACTION : (hovered ? TEXT : DIM));

        paintEnchRowButton(rows, id);
    }

    /**
     * 只刷新一条附魔行的按钮与等级选择器，不触碰底色/悬停状态。
     * 供展开/收起时就地更新，避免整表重建导致滚动位置回弹。
     */
    private void paintEnchRowButton(Map<ResourceLocation, EnchRow> rows, ResourceLocation id) {
        EnchRow row = rows.get(id);
        if (row == null) return;
        boolean added = rows == itemEnchRows
                ? hasEnchantOnItem(selectedItemId, id)
                : AutoTradeManager.getInstance().findBookTarget(id) != null;
        boolean expanded = rows == itemEnchRows
                ? id.equals(expandedItemEnchant)
                : id.equals(expandedBookEnchant);

        if (added) {
            row.button.setMessage(Component.literal("§a✓"));
            row.button.tooltip(Component.literal("已加入，点击移除"));
        } else if (expanded) {
            row.button.setMessage(Component.literal("§e▼"));
            row.button.tooltip(Component.literal("点击收起"));
        } else {
            row.button.setMessage(Component.literal("§b＋"));
            row.button.tooltip(Component.literal("点击展开等级选择"));
        }

        // 控制等级选择器可见性
        row.levelPicker.sizing(Sizing.fill(100), expanded ? Sizing.content() : Sizing.fixed(0));
    }

    private void refreshEnchRowStates(Map<ResourceLocation, EnchRow> rows) {
        for (ResourceLocation id : rows.keySet()) paintEnchRow(rows, id, false);
    }

    // ------------------------------------------------------------------ 标签页二：附魔书

    private FlowLayout buildBookTab() {
        FlowLayout panel = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        panel.gap(6);
        panel.padding(Insets.of(8));
        panel.surface(cardSurface());

        panel.child(Components.label(Component.literal("§l附魔书")).color(TITLE));

        TextBoxComponent searchBox = Components.textBox(Sizing.expand());
        searchBox.setHint(Component.literal("🔍 搜索附魔（附魔书）"));
        searchBox.setMaxLength(48);
        searchBox.text(bookEnchSearchText);
        searchBox.onChanged().subscribe(text -> {
            this.bookEnchSearchText = text;
            rebuildBookList();
        });
        FlowLayout searchRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        searchRow.child(searchBox);
        panel.child(searchRow);

        panel.child(Components.label(
                Component.literal("§7每个附魔一条目标，点击展开等级选择")).color(DIM));

        bookListFlow = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        bookListFlow.gap(2);
        panel.child(Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), bookListFlow));

        rebuildBookList();
        return panel;
    }

    /** 重建附魔书列表。 */
    private void rebuildBookList() {
        if (bookListFlow == null) return;
        bookRows.clear();
        bookListFlow.clearChildren();

        if (allEnchants.isEmpty()) {
            bookListFlow.child(Components.label(
                    Component.literal("§7进入世界后才能列出附魔")).color(DIM));
            return;
        }
        String query = bookEnchSearchText.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (EnchOption option : allEnchants) {
            if (!VillagerTradeData.enchantInBooks(option.id())) continue;
            if (!matchesQuery(option, query)) continue;
            if (shown >= MAX_ROWS) break;
            shown++;
            buildEnchRow(bookRows, option, false);
        }

        if (shown == 0) {
            bookListFlow.child(Components.label(Component.literal("§7没有匹配的附魔")).color(DIM));
        }
    }

    // ------------------------------------------------------------------ 标签页三：已选目标

    private FlowLayout buildTargetsTab() {
        FlowLayout panel = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        panel.gap(6);
        panel.padding(Insets.of(8));
        panel.surface(cardSurface());

        FlowLayout titleRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        titleRow.gap(6);
        titleRow.verticalAlignment(VerticalAlignment.CENTER);
        titleRow.child(Components.label(Component.literal("§l目标列表")).color(TITLE));
        titleRow.child(horizontalSpacer());
        ButtonComponent clear = smallButton(Component.literal("§c清空"), this::onClearButton);
        clear.tooltip(Component.literal("删除全部目标（点两次确认）"));
        titleRow.child(clear);
        panel.child(titleRow);

        targetListFlow = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        targetListFlow.gap(6);
        panel.child(Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), targetListFlow));

        rebuildTargetList();
        return panel;
    }

    private void onClearButton(ButtonComponent clear) {
        if (!confirmClear) {
            confirmClear = true;
            clear.setMessage(Component.literal("§c§l确认清空?"));
            return;
        }
        AutoTradeManager.getInstance().clearTargets();
        confirmClear = false;
        clear.setMessage(Component.literal("§c清空"));
        expandedCards.clear();
        openPickerKey = null;
        rebuildTargetList();
        updateTabLabels();
    }

    /** 整个目标列表按当前数据重建。 */
    private void rebuildTargetList() {
        if (targetListFlow == null) return;
        targetListFlow.clearChildren();
        confirmClear = false;

        List<TargetEntry> targets = AutoTradeManager.getInstance().getTargets();
        if (targets.isEmpty()) {
            targetListFlow.child(Components.label(Component.literal(
                    "§7还没有目标\n§7去「装备物品」或「附魔书」页添加")).color(DIM));
            return;
        }
        for (TargetEntry entry : new ArrayList<>(targets)) {
            targetListFlow.child(buildTargetCard(entry));
        }
    }

    /**
     * 一张目标卡片：标题行（展开/图标/名称/数量/价格/删除），
     * 展开后是每条附魔的等级步进子行 + 内嵌的"添加附魔"选择器。
     */
    private FlowLayout buildTargetCard(TargetEntry token) {
        String key = cardKey(token);
        boolean expanded = expandedCards.contains(key);

        FlowLayout card = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        card.gap(4);
        card.padding(Insets.of(8));
        card.surface(cardSurface());
        final FlowLayout[] self = {card};

        Runnable swapSelf = () -> swapCard(self[0], token);

        // ---- 标题行
        FlowLayout header = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        header.gap(6);
        header.verticalAlignment(VerticalAlignment.CENTER);

        ButtonComponent expand = tinyButton(expanded ? "§e▼" : "§7▶", b -> {
            if (expandedCards.contains(key)) expandedCards.remove(key);
            else expandedCards.add(key);
            swapSelf.run();
        });
        header.child(expand);

        ItemStack icon = token.isEnchantedBook()
                ? new ItemStack(Items.ENCHANTED_BOOK)
                : BuiltInRegistries.ITEM.getOptional(token.id()).map(ItemStack::new).orElse(ItemStack.EMPTY);
        header.child(Components.item(icon));
        header.child(Components.label(Component.literal(cardTitle(token)))
                .color(token.isEnchantedBook() ? TITLE : TEXT));

        header.child(horizontalSpacer());
        header.child(Components.label(Component.literal("§7数量≥")).color(DIM));
        TextBoxComponent countBox = numericBox(String.valueOf(token.minCount()));
        header.child(countBox);
        header.child(Components.label(Component.literal("§7价格≤")).color(DIM));
        TextBoxComponent priceBox = numericBox(String.valueOf(token.maxPrice()));
        header.child(priceBox);

        ButtonComponent delete = smallButton(Component.literal("§c✕"), b -> {
            TargetEntry latest = AutoTradeManager.getInstance().latest(token);
            if (latest != null) AutoTradeManager.getInstance().removeEntry(latest);
            expandedCards.remove(key);
            if (key.equals(openPickerKey)) openPickerKey = null;
            rebuildTargetList();
            updateTabLabels();
        });
        delete.tooltip(Component.literal("删除此目标"));
        header.child(delete);
        card.child(header);

        countBox.onChanged().subscribe(value -> AutoTradeManager.getInstance().updateEntry(
                token, readInt(countBox, DEFAULT_MIN_COUNT), readInt(priceBox, DEFAULT_MAX_PRICE)));
        priceBox.onChanged().subscribe(value -> AutoTradeManager.getInstance().updateEntry(
                token, readInt(countBox, DEFAULT_MIN_COUNT), readInt(priceBox, DEFAULT_MAX_PRICE)));

        if (!expanded) return card;

        // ---- 展开区：附魔子行
        for (EnchantRequirement requirement : new ArrayList<>(token.enchants())) {
            card.child(buildRequirementRow(token, requirement, swapSelf));
        }
        if (token.enchants().isEmpty()) {
            card.child(Components.label(Component.literal(
                    token.isEnchantedBook() ? "§8无附魔要求（任意附魔书都匹配）" : "§8无附魔要求")).color(DIM));
        }

        if (token.isEnchantedBook()) {
            card.child(Components.label(Component.literal(
                    "§7一本书只能有一条附魔\n§7需要其他附魔书目标，请到「附魔书」页添加")).color(DIM));
        } else if (key.equals(openPickerKey)) {
            card.child(buildCardPicker(token.id(), swapSelf));
        } else {
            card.child(smallButton(Component.literal("§b＋ 添加附魔"), b -> {
                openPickerKey = key;
                pickerSearch = "";
                swapSelf.run();
            }));
        }
        return card;
    }

    /** 卡片里一条附魔要求：名称 + 等级步进器 + 移除按钮。 */
    private FlowLayout buildRequirementRow(TargetEntry token, EnchantRequirement requirement,
                                           Runnable swapSelf) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(6);
        row.padding(Insets.of(4, 6, 4, 6));
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.surface(roundedSurface(ROW_BG));

        EnchOption option = enchById.get(requirement.id());
        row.child(Components.label(Component.literal(option != null ? option.name() : requirement.id().getPath()))
                .color(TEXT));
        if (option != null) row.tooltip(Component.literal(option.id().toString()));
        row.child(horizontalSpacer());
        row.child(Components.label(Component.literal("§7等级≥")).color(DIM));
        row.child(requirementLevelControl(token, requirement, option));
        ButtonComponent remove = smallButton(Component.literal("§c✕"), b -> {
            AutoTradeManager.getInstance().removeEnchant(token, requirement.id());
            swapSelf.run();
        });
        remove.tooltip(Component.literal("移除此附魔"));
        row.child(remove);
        return row;
    }

    /** 已有附魔要求的等级步进器。 */
    private FlowLayout requirementLevelControl(TargetEntry token, EnchantRequirement requirement, EnchOption option) {
        int maxLevel = option != null && option.maxLevel() > 0 ? option.maxLevel() : 10;
        FlowLayout control = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        control.gap(3);
        control.verticalAlignment(VerticalAlignment.CENTER);
        int[] current = {requirement.minLevel()};
        LabelComponent value = Components.label(Component.literal(String.valueOf(current[0]))).color(ACTION);
        control.child(tinyButton("§7−", b -> {
            if (current[0] <= 1) return;
            current[0]--;
            value.text(Component.literal(String.valueOf(current[0])));
            AutoTradeManager.getInstance().setEnchantLevel(token, requirement.id(), current[0]);
        }));
        control.child(value);
        control.child(tinyButton("§7＋", b -> {
            if (current[0] >= maxLevel) return;
            current[0]++;
            value.text(Component.literal(String.valueOf(current[0])));
            AutoTradeManager.getInstance().setEnchantLevel(token, requirement.id(), current[0]);
        }));
        return control;
    }

    /** 卡片内嵌的附魔选择器（支持行内等级选择）。 */
    private FlowLayout buildCardPicker(ResourceLocation itemId, Runnable swapSelf) {
        FlowLayout picker = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        picker.gap(4);
        picker.padding(Insets.of(8));
        picker.surface(Surface.flat(PICKER_BG.argb()).and(Surface.outline(PANEL_BORDER.argb())));

        FlowLayout searchRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        searchRow.gap(6);
        searchRow.verticalAlignment(VerticalAlignment.CENTER);
        TextBoxComponent searchBox = Components.textBox(Sizing.expand());
        searchBox.setHint(Component.literal("🔍 搜索附魔"));
        searchBox.setMaxLength(48);
        searchBox.text(pickerSearch);
        FlowLayout pickerList = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        pickerList.gap(2);
        searchBox.onChanged().subscribe(text -> {
            this.pickerSearch = text;
            refreshPickerList(itemId, pickerList, swapSelf);
        });
        searchRow.child(searchBox);
        picker.child(searchRow);

        picker.child(Containers.verticalScroll(Sizing.fill(100), Sizing.fixed(120), pickerList));
        refreshPickerList(itemId, pickerList, swapSelf);
        return picker;
    }

    private void refreshPickerList(ResourceLocation itemId, FlowLayout pickerList, Runnable swapSelf) {
        pickerList.clearChildren();
        TargetEntry latest = AutoTradeManager.getInstance().findItemTarget(itemId);
        String query = pickerSearch.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (EnchOption option : allEnchants) {
            if (!VillagerTradeData.canApplyTo(option.id(), itemId)) continue;
            if (!VillagerTradeData.enchantOnTradedEquipment(option.id())) continue;
            if (latest != null && hasRequirement(latest, option.id())) continue;
            if (!matchesQuery(option, query)) continue;
            if (shown >= 20) break;
            shown++;
            pickerList.child(buildPickerRow(itemId, option, swapSelf));
        }
        if (shown == 0) {
            pickerList.child(Components.label(Component.literal("§7没有可选的附魔")).color(DIM));
        } else if (shown >= 20) {
            pickerList.child(Components.label(Component.literal("§e结果过多，请细化搜索")).color(DIM));
        }
    }

    /** 卡片内嵌选择器的附魔行（点击后弹出等级选择对话框）。 */
    private FlowLayout buildPickerRow(ResourceLocation itemId, EnchOption option, Runnable swapSelf) {
        // 容器：主行 + 等级选择器
        FlowLayout container = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        container.gap(2);
        
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(6);
        row.padding(Insets.of(3, 6, 3, 6));
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.cursorStyle(CursorStyle.HAND);
        row.surface(roundedSurface(ROW_BG));
        row.tooltip(Component.literal(option.id().toString()));

        row.child(Components.label(Component.literal(option.name())));
        row.child(horizontalSpacer());
        row.child(Components.label(Component.literal("§8" + levelRange(option))).color(DIM));
        
        ButtonComponent addBtn = tinyButton("§b＋", b -> {});
        row.child(addBtn);
        
        container.child(row);
        
        // 行内等级选择器
        FlowLayout levelPicker = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(0)); // 初始隐藏
        levelPicker.gap(3);
        levelPicker.padding(Insets.of(4));
        levelPicker.surface(Surface.flat(LEVEL_PICKER_BG.argb()));
        levelPicker.child(Components.label(Component.literal("§e等级:")).color(TITLE));
        
        int maxLevel = option.maxLevel() > 0 ? option.maxLevel() : 10;
        final boolean[] pickerExpanded = {false};
        for (int level = 1; level <= maxLevel; level++) {
            final int lv = level;
            ButtonComponent btn = Components.button(Component.literal("§b" + lv), b -> {
                AutoTradeManager.getInstance().addEnchantToItem(itemId,
                        new EnchantRequirement(option.id(), lv));
                updateTabLabels();
                swapSelf.run();
            });
            btn.sizing(Sizing.fixed(26), Sizing.fixed(26));
            levelPicker.child(btn);
        }
        container.child(levelPicker);
        
        Runnable togglePicker = () -> {
            pickerExpanded[0] = !pickerExpanded[0];
            levelPicker.sizing(Sizing.fill(100), pickerExpanded[0] ? Sizing.content() : Sizing.fixed(0));
            addBtn.setMessage(Component.literal(pickerExpanded[0] ? "§e▼" : "§b＋"));
        };
        
        addBtn.onPress(b -> togglePicker.run());
        row.mouseDown().subscribe((click, doubled) -> {
            togglePicker.run();
            return true;
        });
        row.mouseEnter().subscribe(() -> row.surface(roundedSurface(ROW_HOVER)));
        row.mouseLeave().subscribe(() -> row.surface(roundedSurface(ROW_BG)));

        return container;
    }

    /** 用新卡片替换旧卡片（只动这一张，滚动位置基本保持）。 */
    private void swapCard(FlowLayout oldCard, TargetEntry token) {
        if (targetListFlow == null) return;
        TargetEntry latest = AutoTradeManager.getInstance().latest(token);
        int index = targetListFlow.children().indexOf(oldCard);
        if (index < 0 || latest == null) {
            rebuildTargetList();
            return;
        }
        FlowLayout fresh = buildTargetCard(latest);
        targetListFlow.child(index, fresh);
        targetListFlow.removeChild(oldCard);
        updateTabLabels();
    }

    // ------------------------------------------------------------------ 底部操作栏

    private FlowLayout buildFooter() {
        AutoTradeManager manager = AutoTradeManager.getInstance();
        FlowLayout footer = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        footer.gap(8);
        footer.padding(Insets.top(10));
        footer.verticalAlignment(VerticalAlignment.CENTER);

        modeButton = smallButton(modeLabel(), button -> {
            AutoTradeManager m = AutoTradeManager.getInstance();
            m.setMatchMode(m.getMatchMode() == MatchMode.ALL ? MatchMode.ANY : MatchMode.ALL);
            button.setMessage(modeLabel());
        });
        modeButton.tooltip(Component.literal("全部：所有目标都要刷出\n任一：刷出任意一个就提醒"));
        footer.child(modeButton);

        footer.child(horizontalSpacer());

        startButton = Components.button(Component.literal(manager.isActive() ? "§c■ 停止" : "§2▶ 开始"),
                button -> onStartStop());
        startButton.sizing(Sizing.fixed(80), Sizing.fixed(24));
        footer.child(startButton);

        footer.child(smallButton(Component.literal("§7关闭"), button -> this.onClose()));
        return footer;
    }

    private Component modeLabel() {
        return Component.literal(AutoTradeManager.getInstance().getMatchMode() == MatchMode.ALL
                ? "§7匹配: §e全部" : "§7匹配: §e任一");
    }

    // ------------------------------------------------------------------ 开始/停止

    private void onStartStop() {
        AutoTradeManager manager = AutoTradeManager.getInstance();
        if (manager.isActive()) {
            manager.cancel();
            if (startButton != null) startButton.setMessage(Component.literal("§2▶ 开始"));
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

    // ------------------------------------------------------------------ 通用小组件

    /** 宽度按内容、高度 24 的按钮。 */
    private static ButtonComponent smallButton(Component text, Consumer<ButtonComponent> onPress) {
        ButtonComponent button = Components.button(text, onPress);
        button.sizing(Sizing.content(), Sizing.fixed(24));
        return button;
    }

    /** 24×24 的方形按钮。 */
    private static ButtonComponent tinyButton(String text, Consumer<ButtonComponent> onPress) {
        ButtonComponent button = Components.button(Component.literal(text), onPress);
        button.sizing(Sizing.fixed(24), Sizing.fixed(24));
        return button;
    }

    /** 数字输入框。 */
    private static TextBoxComponent numericBox(String initial) {
        TextBoxComponent box = Components.textBox(Sizing.fixed(40));
        box.setMaxLength(4);
        box.text(initial);
        return box;
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
        enchById.clear();
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        var lookup = client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        lookup.listElements().forEach(holder -> {
            EnchOption option = new EnchOption(
                    holder.key().location(),
                    holder.value().description().getString(),
                    holder.value().getMaxLevel());
            allEnchants.add(option);
            enchById.put(option.id(), option);
        });
        allEnchants.sort(Comparator.comparing(EnchOption::name));
    }

    /** 圆角卡片底色。 */
    private static Surface cardSurface() {
        return Surface.flat(CARD_BG.argb()).and(Surface.outline(PANEL_BORDER.argb()));
    }

    /** 圆角行底色（比卡片略小的圆角）。 */
    private static Surface roundedSurface(Color color) {
        return Surface.flat(color.argb());
    }

    private static FlowLayout horizontalSpacer() {
        return Containers.horizontalFlow(Sizing.expand(), Sizing.fixed(0));
    }

    private static boolean matchesQuery(EnchOption option, String lowerQuery) {
        return lowerQuery.isEmpty()
                || option.name().toLowerCase(Locale.ROOT).contains(lowerQuery)
                || option.id().toString().toLowerCase(Locale.ROOT).contains(lowerQuery);
    }

    private static String levelRange(EnchOption option) {
        return option.maxLevel() <= 1 ? "Ⅰ" : "Ⅰ-" + toRoman(option.maxLevel());
    }

    private static String toRoman(int num) {
        if (num <= 0 || num > 10) return String.valueOf(num);
        String[] romans = {"", "Ⅰ", "Ⅱ", "Ⅲ", "Ⅳ", "Ⅴ", "Ⅵ", "Ⅶ", "Ⅷ", "Ⅸ", "Ⅹ"};
        return romans[num];
    }

    private boolean hasItemTarget(ResourceLocation id) {
        return AutoTradeManager.getInstance().findItemTarget(id) != null;
    }

    private boolean hasEnchantOnItem(ResourceLocation itemId, ResourceLocation enchantId) {
        if (itemId == null) return false;
        TargetEntry entry = AutoTradeManager.getInstance().findItemTarget(itemId);
        return entry != null && hasRequirement(entry, enchantId);
    }

    private static boolean hasRequirement(TargetEntry entry, ResourceLocation enchantId) {
        for (EnchantRequirement requirement : entry.enchants()) {
            if (requirement.id().equals(enchantId)) return true;
        }
        return false;
    }

    private static String cardKey(TargetEntry entry) {
        if (entry.isEnchantedBook()) {
            return entry.enchants().isEmpty()
                    ? "book:-"
                    : "book:" + entry.enchants().get(0).id();
        }
        return "item:" + entry.id();
    }

    private static String cardKeyForItem(ResourceLocation itemId) {
        return "item:" + itemId;
    }

    private String cardTitle(TargetEntry entry) {
        if (entry.isEnchantedBook()) {
            if (entry.enchants().isEmpty()) return "附魔书";
            return entry.enchants().stream()
                    .map(requirement -> enchantName(requirement.id()))
                    .collect(Collectors.joining(" + "));
        }
        return itemName(entry.id());
    }

    private String enchantName(ResourceLocation id) {
        EnchOption option = enchById.get(id);
        return option != null ? option.name() : id.getPath();
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
