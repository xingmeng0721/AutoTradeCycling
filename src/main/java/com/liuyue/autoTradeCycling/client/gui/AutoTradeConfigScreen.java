package com.liuyue.autoTradeCycling.client.gui;

import com.liuyue.autoTradeCycling.client.manager.AutoTradeManager;
import com.liuyue.autoTradeCycling.client.manager.AutoTradeManager.MatchMode;
import com.liuyue.autoTradeCycling.client.manager.VillagerTradeData;
import com.liuyue.autoTradeCycling.common.SearchSpeed;
import com.liuyue.autoTradeCycling.common.TradeTargets.EnchantRequirement;
import com.liuyue.autoTradeCycling.common.TradeTargets.TargetEntry;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.UIContainers;
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
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
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
 * owo-lib 标签页版，统一管理装备物品/附魔书/已选目标。
 */
public class AutoTradeConfigScreen extends BaseOwoScreen<FlowLayout> {

    private static final Color ROOT_BG = Color.ofArgb(0xFF0F111A);
    private static final Color PANEL_BG = Color.ofArgb(0xFF1A1D2E);
    private static final Color CARD_BG = Color.ofArgb(0xFF16213E);
    private static final Color PANEL_BORDER = Color.ofArgb(0x664FC3F7);
    private static final Color ROW_BG = Color.ofArgb(0xFF0E1621);
    private static final Color ROW_HOVER = Color.ofArgb(0xFF1E3A5F);
    private static final Color ROW_SELECTED = Color.ofArgb(0xFF2A5298);
    private static final Color ROW_SELECTED_HOVER = Color.ofArgb(0xFF3666BB);
    private static final Color ROW_ADDED = Color.ofArgb(0xFF1B4D3E);
    private static final Color ROW_ADDED_HOVER = Color.ofArgb(0xFF26614F);
    private static final Color PICKER_BG = Color.ofArgb(0xFF0A0C14);
    private static final Color LEVEL_PICKER_BG = Color.ofArgb(0xFF1A2E4A);
    private static final Color TITLE = Color.ofArgb(0xFF4DD0E1);
    private static final Color TEXT = Color.ofArgb(0xFFE8EAF6);
    private static final Color DIM = Color.ofArgb(0xFF9FA8DA);
    private static final Color ACTION = Color.ofArgb(0xFF4AE5B0);
    private static final Color DANGER = Color.ofArgb(0xFFFF6B9D);

    private static final int DEFAULT_MIN_COUNT = 1;
    private static final int DEFAULT_MAX_PRICE = 64;
    private static final int MAX_ROWS = 300;
    private static final int SEARCH_DEBOUNCE_TICKS = 4;

    private static final int TAB_ITEM = 0;
    private static final int TAB_BOOK = 1;
    private static final int TAB_TARGET = 2;

    private record EnchOption(Identifier id, String name, int maxLevel) {}

    private record ItemRowData(Item item, Identifier id, Identifier potion, String label) {}

    private static final class ItemRow {
        final FlowLayout row;
        final LabelComponent name;
        final ButtonComponent state;
        final Identifier id;
        final Identifier potion;
        final String label;
        ItemRow(FlowLayout row, LabelComponent name, ButtonComponent state,
                Identifier id, Identifier potion, String label) {
            this.row = row;
            this.name = name;
            this.state = state;
            this.id = id;
            this.potion = potion;
            this.label = label;
        }
    }

    private static final class EnchRow {
        final FlowLayout row;
        final LabelComponent name;
        final ButtonComponent button;
        final FlowLayout levelPicker;
        EnchRow(FlowLayout row, LabelComponent name, ButtonComponent button, FlowLayout levelPicker) {
            this.row = row;
            this.name = name;
            this.button = button;
            this.levelPicker = levelPicker;
        }
    }

    private int activeTab = TAB_ITEM;
    private boolean onlyTradeable = true;
    private Identifier selectedItemId;
    private String selectedItemKey;
    private String selectedItemName = "";
    private String itemSearchText = "";
    private String itemEnchSearchText = "";
    private String bookEnchSearchText = "";

    private String pickerSearch = "";
    private String openPickerKey;
    private final Set<String> expandedCards = new HashSet<>();
    private boolean confirmClear = false;

    private int itemListDebounce = 0;
    private int itemEnchDebounce = 0;
    private int bookListDebounce = 0;
    private boolean startButtonRunning = false;

    private Identifier expandedItemEnchant;
    private Identifier expandedBookEnchant;

    private final List<Item> allItems = new ArrayList<>();
    private final List<EnchOption> allEnchants = new ArrayList<>();
    private final Map<Identifier, EnchOption> enchById = new HashMap<>();

    private FlowLayout contentArea;
    private final ButtonComponent[] tabButtons = new ButtonComponent[3];
    private ButtonComponent startButton;
    private ButtonComponent clearButton;
    private ButtonComponent modeButton;
    private ButtonComponent speedButton;
    private ButtonComponent filterButton;
    private LabelComponent itemEnchTitle;
    private FlowLayout itemListFlow;
    private FlowLayout itemEnchListFlow;
    private FlowLayout bookListFlow;
    private FlowLayout targetListFlow;
    private final Map<Identifier, String> itemNames = new HashMap<>();
    private final Map<String, ItemRow> itemRows = new HashMap<>();
    private final Map<Identifier, EnchRow> itemEnchRows = new HashMap<>();
    private final Map<Identifier, EnchRow> bookRows = new HashMap<>();

    public AutoTradeConfigScreen() {
        super(Component.literal("自动刷新交易"));
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root.gap(0);
        root.padding(Insets.of(0));
        root.surface(Surface.flat(ROOT_BG.argb()));
        cacheRegistries();

        FlowLayout main = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        main.gap(0);
        main.padding(Insets.of(10, 12, 10, 12));
        main.child(buildHeader());

        contentArea = UIContainers.verticalFlow(Sizing.fill(100), Sizing.expand(100));
        main.child(contentArea);

        main.child(buildFooter());
        root.child(main);

        switchTab(TAB_ITEM);
    }

    private FlowLayout buildHeader() {
        FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        header.gap(12);
        header.padding(Insets.bottom(10));
        header.verticalAlignment(VerticalAlignment.CENTER);

        LabelComponent title = UIComponents.label(Component.literal("§l自动刷新交易")).color(TITLE);
        header.child(title);

        header.child(horizontalSpacer());

        for (int tab = 0; tab < 3; tab++) {
            final int which = tab;
            tabButtons[tab] = createTabButton(which);
            header.child(tabButtons[tab]);
        }
        return header;
    }

    private ButtonComponent createTabButton(int which) {
        ButtonComponent btn = UIComponents.button(Component.empty(), b -> switchTab(which));
        btn.sizing(Sizing.content(), Sizing.fixed(24));
        return btn;
    }

    private void switchTab(int tab) {
        if (this.activeTab == tab && contentArea.children().size() > 0) {
            updateTabLabels();
            return;
        }
        this.activeTab = tab;
        expandedItemEnchant = null;
        expandedBookEnchant = null;
        itemListDebounce = 0;
        itemEnchDebounce = 0;
        bookListDebounce = 0;
        contentArea.clearChildren();
        filterButton = null;
        switch (tab) {
            case TAB_BOOK -> contentArea.child(buildBookTab());
            case TAB_TARGET -> contentArea.child(buildTargetsTab());
            default -> contentArea.child(buildItemTab());
        }
        updateTabLabels();
    }

    @Override
    public void tick() {
        super.tick();
        syncStartButton();
        if (itemListDebounce > 0 && --itemListDebounce == 0) rebuildItemList();
        if (itemEnchDebounce > 0 && --itemEnchDebounce == 0) refreshItemEnchList();
        if (bookListDebounce > 0 && --bookListDebounce == 0) rebuildBookList();
    }

    private void syncStartButton() {
        if (startButton == null) return;
        boolean running = AutoTradeManager.getInstance().isActive();
        if (running == startButtonRunning) return;
        startButtonRunning = running;
        startButton.setMessage(Component.literal(running ? "§c■ 停止" : "§2▶ 开始"));
    }

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

    private FlowLayout buildItemTab() {
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fill(100));
        row.gap(8);
        row.child(buildItemPanel());
        row.child(buildItemEnchPanel());
        return row;
    }

    private FlowLayout buildItemPanel() {
        FlowLayout panel = UIContainers.verticalFlow(Sizing.fill(49), Sizing.fill(100));
        panel.gap(6);
        panel.padding(Insets.of(8));
        panel.surface(cardSurface());

        FlowLayout titleRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        titleRow.gap(6);
        titleRow.verticalAlignment(VerticalAlignment.CENTER);
        titleRow.child(UIComponents.label(Component.literal("§l物品列表")).color(TITLE));
        titleRow.child(horizontalSpacer());
        ButtonComponent filter = createFilterButton();
        titleRow.child(filter);
        panel.child(titleRow);

        TextBoxComponent searchBox = UIComponents.textBox(Sizing.expand());
        searchBox.setHint(Component.literal("🔍 搜索物品名称或 ID"));
        searchBox.setMaxLength(48);
        searchBox.text(itemSearchText);
        searchBox.onChanged().subscribe(text -> {
            this.itemSearchText = text;
            itemListDebounce = SEARCH_DEBOUNCE_TICKS;
        });
        FlowLayout searchRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        searchRow.child(searchBox);
        panel.child(searchRow);

        itemListFlow = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        itemListFlow.gap(2);
        panel.child(UIContainers.verticalScroll(Sizing.fill(100), Sizing.expand(), itemListFlow));

        rebuildItemList();
        return panel;
    }

    private ButtonComponent createFilterButton() {
        ButtonComponent filter = smallButton(filterLabel(), b -> {
            this.onlyTradeable = !this.onlyTradeable;
            b.setMessage(filterLabel());
            rebuildItemList();
        });
        filter.active(VillagerTradeData.hasCandidates());
        filter.tooltip(Component.literal("只显示村民可能出售的物品"));
        filterButton = filter;
        return filter;
    }

    public void onTradeableItemsSynced() {
        if (activeTab != TAB_ITEM) return;
        if (filterButton != null) {
            filterButton.active(VillagerTradeData.hasCandidates());
            filterButton.setMessage(filterLabel());
        }
        rebuildItemList();
    }

    private Component filterLabel() {
        if (!VillagerTradeData.hasCandidates()) return Component.literal("§8过滤不可用");
        return Component.literal(this.onlyTradeable ? "§a✓ 仅可交易" : "§7○ 显示全部");
    }

    private FlowLayout buildItemEnchPanel() {
        FlowLayout panel = UIContainers.verticalFlow(Sizing.fill(49), Sizing.fill(100));
        panel.gap(6);
        panel.padding(Insets.of(8));
        panel.surface(cardSurface());

        itemEnchTitle = UIComponents.label(Component.literal("§l附魔")).color(TITLE);
        panel.child(itemEnchTitle);

        TextBoxComponent searchBox = UIComponents.textBox(Sizing.expand());
        searchBox.setHint(Component.literal("🔍 搜索附魔"));
        searchBox.setMaxLength(48);
        searchBox.text(itemEnchSearchText);
        searchBox.onChanged().subscribe(text -> {
            this.itemEnchSearchText = text;
            itemEnchDebounce = SEARCH_DEBOUNCE_TICKS;
        });
        FlowLayout searchRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        searchRow.child(searchBox);
        panel.child(searchRow);

        panel.child(UIComponents.label(Component.literal("§7点击附魔展开等级选择")).color(DIM));

        itemEnchListFlow = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        itemEnchListFlow.gap(2);
        panel.child(UIContainers.verticalScroll(Sizing.fill(100), Sizing.expand(), itemEnchListFlow));

        refreshItemEnchList();
        return panel;
    }

    private void rebuildItemList() {
        if (itemListFlow == null) return;
        itemRows.clear();
        itemListFlow.clearChildren();

        String query = itemSearchText.trim().toLowerCase(Locale.ROOT);
        Set<Identifier> tradeable = onlyTradeable ? VillagerTradeData.villagerItems() : Set.of();

        List<ItemRowData> rows = new ArrayList<>();
        for (Item item : allItems) {
            if (item == Items.POTION) continue;
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (!tradeable.isEmpty() && !tradeable.contains(id)) continue;

            if (item == Items.TIPPED_ARROW) {
                if (matchesRowQuery(query, id, "药水箭")) {
                    rows.add(new ItemRowData(item, id, null, "药水箭"));
                }
                for (Identifier potion : VillagerTradeData.tradeablePotions()) {
                    String label = "药水箭 · " + VillagerTradeData.potionName(potion).getString();
                    if (matchesRowQuery(query, id, label)) rows.add(new ItemRowData(item, id, potion, label));
                }
                continue;
            }

            String name = itemNames.computeIfAbsent(id, key -> item.getName(new ItemStack(item)).getString());
            if (matchesRowQuery(query, id, name)) rows.add(new ItemRowData(item, id, null, name));
        }

        int shown = 0;
        for (ItemRowData data : rows) {
            if (shown >= MAX_ROWS) break;
            shown++;
            itemListFlow.child(buildItemRow(data));
        }

        if (shown == 0) {
            itemListFlow.child(UIComponents.label(Component.literal("§7没有匹配的物品")).color(DIM));
        } else if (shown >= MAX_ROWS) {
            itemListFlow.child(UIComponents.label(Component.literal("§e结果过多，请细化搜索")).color(DIM));
        }
    }

    private static boolean matchesRowQuery(String lowerQuery, Identifier id, String label) {
        return lowerQuery.isEmpty()
                || id.toString().toLowerCase(Locale.ROOT).contains(lowerQuery)
                || label.toLowerCase(Locale.ROOT).contains(lowerQuery);
    }

    private FlowLayout buildItemRow(ItemRowData data) {
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(6);
        row.padding(Insets.of(4, 6, 4, 6));
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.cursorStyle(CursorStyle.HAND);
        row.tooltip(Component.literal(data.id().toString()));

        row.child(UIComponents.item(itemIcon(data.item(), data.potion())));
        LabelComponent nameLabel = UIComponents.label(Component.literal(data.label()));
        row.child(nameLabel);
        row.child(horizontalSpacer());

        String key = rowKey(data.id(), data.potion());
        ButtonComponent state = smallButton(Component.literal(""), b -> onItemStateButton(key));
        row.child(state);

        row.mouseDown().subscribe((click, doubled) -> {
            selectItem(key);
            return true;
        });
        row.mouseEnter().subscribe(() -> paintItemRow(key, true));
        row.mouseLeave().subscribe(() -> paintItemRow(key, false));

        itemRows.put(key, new ItemRow(row, nameLabel, state, data.id(), data.potion(), data.label()));
        paintItemRow(key, false);
        return row;
    }

    private static ItemStack itemIcon(Item item, Identifier potion) {
        if (potion == null) return new ItemStack(item);
        Potion value = BuiltInRegistries.POTION.getValue(potion);
        if (value == null) return new ItemStack(item);
        return PotionContents.createItemStack(item, BuiltInRegistries.POTION.wrapAsHolder(value));
    }

    private static String rowKey(Identifier id, Identifier potion) {
        return potion == null ? id.toString() : id + "|" + potion;
    }

    private void paintItemRow(String key, boolean hovered) {
        ItemRow row = itemRows.get(key);
        if (row == null) return;
        boolean selected = key.equals(selectedItemKey);
        boolean added = AutoTradeManager.getInstance().findItemTarget(row.id, row.potion) != null;

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
        for (String key : itemRows.keySet()) paintItemRow(key, false);
    }

    private void selectItem(String key) {
        ItemRow row = itemRows.get(key);
        if (row == null || key.equals(selectedItemKey)) return;
        String previous = selectedItemKey;
        this.selectedItemKey = key;
        this.selectedItemId = row.id;
        this.selectedItemName = row.label;
        expandedItemEnchant = null;
        if (previous != null) paintItemRow(previous, false);
        paintItemRow(key, false);
        refreshItemEnchList();
    }

    private void onItemStateButton(String key) {
        ItemRow row = itemRows.get(key);
        if (row == null) return;
        AutoTradeManager manager = AutoTradeManager.getInstance();
        if (manager.findItemTarget(row.id, row.potion) != null) {
            expandedCards.add(cardKeyForItem(row.id, row.potion));
            switchTab(TAB_TARGET);
            return;
        }
        if (manager.addTarget(row.id, new ArrayList<>(), DEFAULT_MIN_COUNT, DEFAULT_MAX_PRICE, row.potion)) {
            expandedCards.add(cardKeyForItem(row.id, row.potion));
        }
        selectItem(key);
        refreshItemRowStates();
        updateTabLabels();
    }

    private void refreshItemEnchList() {
        if (itemEnchListFlow == null) return;
        itemEnchRows.clear();
        itemEnchListFlow.clearChildren();

        if (allEnchants.isEmpty()) {
            if (itemEnchTitle != null) itemEnchTitle.text(Component.literal("§l附魔"));
            itemEnchListFlow.child(UIComponents.label(
                    Component.literal("§7进入世界后才能列出附魔")).color(DIM));
            return;
        }
        if (selectedItemId == null) {
            if (itemEnchTitle != null) itemEnchTitle.text(Component.literal("§l附魔"));
            itemEnchListFlow.child(UIComponents.label(
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
            itemEnchListFlow.child(UIComponents.label(
                    Component.literal("§7该物品没有可交易的附魔")).color(DIM));
        }
    }

    private void buildEnchRow(Map<Identifier, EnchRow> rows, EnchOption option, boolean forItem) {
        FlowLayout listFlow = forItem ? itemEnchListFlow : bookListFlow;
        
        FlowLayout container = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        container.gap(2);
        
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(6);
        row.padding(Insets.of(4, 6, 4, 6));
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.cursorStyle(CursorStyle.HAND);
        row.tooltip(Component.literal(option.id().toString()));

        LabelComponent name = UIComponents.label(Component.literal(option.name()));
        row.child(name);
        row.child(horizontalSpacer());
        row.child(UIComponents.label(Component.literal("§8" + levelRange(option))).color(DIM));

        ButtonComponent button = tinyButton("§b＋", b -> toggleEnchantLevelPicker(option, forItem));
        row.child(button);

        row.mouseDown().subscribe((click, doubled) -> {
            toggleEnchantLevelPicker(option, forItem);
            return true;
        });
        row.mouseEnter().subscribe(() -> paintEnchRow(rows, option.id(), true));
        row.mouseLeave().subscribe(() -> paintEnchRow(rows, option.id(), false));

        container.child(row);
        
        FlowLayout levelPicker = buildLevelPicker(option, forItem);
        container.child(levelPicker);

        rows.put(option.id(), new EnchRow(row, name, button, levelPicker));
        listFlow.child(container);
        paintEnchRow(rows, option.id(), false);
    }

    private FlowLayout buildLevelPicker(EnchOption option, boolean forItem) {
        FlowLayout picker = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        picker.gap(4);
        picker.padding(Insets.of(6));
        picker.surface(Surface.flat(LEVEL_PICKER_BG.argb()));
        picker.verticalAlignment(VerticalAlignment.CENTER);
        
        picker.child(UIComponents.label(Component.literal("§e等级:")).color(TITLE));
        
        int maxLevel = option.maxLevel() > 0 ? option.maxLevel() : 10;
        for (int level = 1; level <= maxLevel; level++) {
            final int lv = level;
            ButtonComponent btn = UIComponents.button(Component.literal("§b" + lv), b -> {
                selectEnchantLevel(option, lv, forItem);
            });
            btn.sizing(Sizing.fixed(28), Sizing.fixed(28));
            picker.child(btn);
        }
        
        return picker;
    }

    private void toggleEnchantLevelPicker(EnchOption option, boolean forItem) {
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
                if (option.id().equals(expandedBookEnchant)) expandedBookEnchant = null;
                paintEnchRow(bookRows, option.id(), false);
                updateTabLabels();
                return;
            }
        }

        Map<Identifier, EnchRow> rows = forItem ? itemEnchRows : bookRows;
        Identifier previous = forItem ? expandedItemEnchant : expandedBookEnchant;
        Identifier now = option.id().equals(previous) ? null : option.id();

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

    private void selectEnchantLevel(EnchOption option, int level, boolean forItem) {
        if (forItem) {
            if (selectedItemId == null) return;
            AutoTradeManager manager = AutoTradeManager.getInstance();
            TargetEntry entry = manager.findItemTarget(selectedItemId);
            if (entry == null) {
                manager.addTarget(selectedItemId, new ArrayList<>(), DEFAULT_MIN_COUNT, DEFAULT_MAX_PRICE);
            }
            manager.addEnchantToItem(selectedItemId, new EnchantRequirement(option.id(), level));
            expandedItemEnchant = null;
            refreshItemRowStates();
            refreshItemEnchList();
        } else {
            List<EnchantRequirement> requirements = new ArrayList<>();
            requirements.add(new EnchantRequirement(option.id(), level));
            AutoTradeManager.getInstance().addTarget(BuiltInRegistries.ITEM.getKey(Items.ENCHANTED_BOOK),
                    requirements, DEFAULT_MIN_COUNT, DEFAULT_MAX_PRICE);
            Identifier previous = expandedBookEnchant;
            expandedBookEnchant = null;
            if (previous != null && !previous.equals(option.id())) paintEnchRow(bookRows, previous, false);
            paintEnchRow(bookRows, option.id(), false);
        }
        updateTabLabels();
    }

    private void paintEnchRow(Map<Identifier, EnchRow> rows, Identifier id, boolean hovered) {
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

    private void paintEnchRowButton(Map<Identifier, EnchRow> rows, Identifier id) {
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

        row.levelPicker.sizing(Sizing.fill(100), expanded ? Sizing.content() : Sizing.fixed(0));
    }

    private FlowLayout buildBookTab() {
        FlowLayout panel = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        panel.gap(6);
        panel.padding(Insets.of(8));
        panel.surface(cardSurface());

        panel.child(UIComponents.label(Component.literal("§l附魔书")).color(TITLE));

        TextBoxComponent searchBox = UIComponents.textBox(Sizing.expand());
        searchBox.setHint(Component.literal("🔍 搜索附魔（附魔书）"));
        searchBox.setMaxLength(48);
        searchBox.text(bookEnchSearchText);
        searchBox.onChanged().subscribe(text -> {
            this.bookEnchSearchText = text;
            bookListDebounce = SEARCH_DEBOUNCE_TICKS;
        });
        FlowLayout searchRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        searchRow.child(searchBox);
        panel.child(searchRow);

        panel.child(UIComponents.label(
                Component.literal("§7每个附魔一条目标，点击展开等级选择")).color(DIM));

        bookListFlow = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        bookListFlow.gap(2);
        panel.child(UIContainers.verticalScroll(Sizing.fill(100), Sizing.expand(), bookListFlow));

        rebuildBookList();
        return panel;
    }

    private void rebuildBookList() {
        if (bookListFlow == null) return;
        bookRows.clear();
        bookListFlow.clearChildren();

        if (allEnchants.isEmpty()) {
            bookListFlow.child(UIComponents.label(
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
            bookListFlow.child(UIComponents.label(Component.literal("§7没有匹配的附魔")).color(DIM));
        }
    }

    private FlowLayout buildTargetsTab() {
        FlowLayout panel = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        panel.gap(6);
        panel.padding(Insets.of(8));
        panel.surface(cardSurface());

        FlowLayout titleRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        titleRow.gap(6);
        titleRow.verticalAlignment(VerticalAlignment.CENTER);
        titleRow.child(UIComponents.label(Component.literal("§l目标列表")).color(TITLE));
        titleRow.child(horizontalSpacer());
        ButtonComponent clear = smallButton(Component.literal("§c清空"), this::onClearButton);
        clear.tooltip(Component.literal("删除全部目标（点两次确认）"));
        clearButton = clear;
        titleRow.child(clear);
        panel.child(titleRow);

        targetListFlow = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        targetListFlow.gap(6);
        panel.child(UIContainers.verticalScroll(Sizing.fill(100), Sizing.expand(), targetListFlow));

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

    private void rebuildTargetList() {
        if (targetListFlow == null) return;
        targetListFlow.clearChildren();
        if (confirmClear && clearButton != null) clearButton.setMessage(Component.literal("§c清空"));
        confirmClear = false;

        List<TargetEntry> targets = AutoTradeManager.getInstance().getTargets();
        if (targets.isEmpty()) {
            targetListFlow.child(UIComponents.label(Component.literal(
                    "§7还没有目标\n§7去「装备物品」或「附魔书」页添加")).color(DIM));
            return;
        }
        for (TargetEntry entry : new ArrayList<>(targets)) {
            targetListFlow.child(buildTargetCard(entry));
        }
    }

    private FlowLayout buildTargetCard(TargetEntry token) {
        String key = cardKey(token);
        boolean expanded = expandedCards.contains(key);

        FlowLayout card = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        card.gap(4);
        card.padding(Insets.of(8));
        card.surface(cardSurface());
        final FlowLayout[] self = {card};

        Runnable swapSelf = () -> swapCard(self[0], token);

        FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
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
                : itemIcon(BuiltInRegistries.ITEM.getOptional(token.id()).orElse(Items.AIR), token.potion());
        header.child(UIComponents.item(icon));
        header.child(UIComponents.label(Component.literal(cardTitle(token)))
                .color(token.isEnchantedBook() ? TITLE : TEXT));

        header.child(horizontalSpacer());
        header.child(UIComponents.label(Component.literal("§7数量≥")).color(DIM));
        TextBoxComponent countBox = numericBox(String.valueOf(token.minCount()));
        header.child(countBox);
        header.child(UIComponents.label(Component.literal("§7价格≤")).color(DIM));
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

        for (EnchantRequirement requirement : new ArrayList<>(token.enchants())) {
            card.child(buildRequirementRow(token, requirement, swapSelf));
        }
        if (token.enchants().isEmpty()) {
            card.child(UIComponents.label(Component.literal(
                    token.isEnchantedBook() ? "§8无附魔要求（任意附魔书都匹配）" : "§8无附魔要求")).color(DIM));
        }

        if (token.isEnchantedBook()) {
            card.child(UIComponents.label(Component.literal(
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

    private FlowLayout buildRequirementRow(TargetEntry token, EnchantRequirement requirement,
                                           Runnable swapSelf) {
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(6);
        row.padding(Insets.of(4, 6, 4, 6));
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.surface(roundedSurface(ROW_BG));

        EnchOption option = enchById.get(requirement.id());
        row.child(UIComponents.label(Component.literal(option != null ? option.name() : requirement.id().getPath()))
                .color(TEXT));
        if (option != null) row.tooltip(Component.literal(option.id().toString()));
        row.child(horizontalSpacer());
        row.child(UIComponents.label(Component.literal("§7等级≥")).color(DIM));
        row.child(requirementLevelControl(token, requirement, option));
        ButtonComponent remove = smallButton(Component.literal("§c✕"), b -> {
            AutoTradeManager.getInstance().removeEnchant(token, requirement.id());
            swapSelf.run();
        });
        remove.tooltip(Component.literal("移除此附魔"));
        row.child(remove);
        return row;
    }

    private FlowLayout requirementLevelControl(TargetEntry token, EnchantRequirement requirement, EnchOption option) {
        int maxLevel = option != null && option.maxLevel() > 0 ? option.maxLevel() : 10;
        FlowLayout control = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        control.gap(3);
        control.verticalAlignment(VerticalAlignment.CENTER);
        int[] current = {requirement.minLevel()};
        LabelComponent value = UIComponents.label(Component.literal(String.valueOf(current[0]))).color(ACTION);
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

    private FlowLayout buildCardPicker(Identifier itemId, Runnable swapSelf) {
        FlowLayout picker = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        picker.gap(4);
        picker.padding(Insets.of(8));
        picker.surface(Surface.flat(PICKER_BG.argb()).and(Surface.outline(PANEL_BORDER.argb())));

        FlowLayout searchRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        searchRow.gap(6);
        searchRow.verticalAlignment(VerticalAlignment.CENTER);
        TextBoxComponent searchBox = UIComponents.textBox(Sizing.expand());
        searchBox.setHint(Component.literal("🔍 搜索附魔"));
        searchBox.setMaxLength(48);
        searchBox.text(pickerSearch);
        FlowLayout pickerList = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        pickerList.gap(2);
        searchBox.onChanged().subscribe(text -> {
            this.pickerSearch = text;
            refreshPickerList(itemId, pickerList, swapSelf);
        });
        searchRow.child(searchBox);
        picker.child(searchRow);

        picker.child(UIContainers.verticalScroll(Sizing.fill(100), Sizing.fixed(120), pickerList));
        refreshPickerList(itemId, pickerList, swapSelf);
        return picker;
    }

    private void refreshPickerList(Identifier itemId, FlowLayout pickerList, Runnable swapSelf) {
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
            pickerList.child(UIComponents.label(Component.literal("§7没有可选的附魔")).color(DIM));
        } else if (shown >= 20) {
            pickerList.child(UIComponents.label(Component.literal("§e结果过多，请细化搜索")).color(DIM));
        }
    }

    private FlowLayout buildPickerRow(Identifier itemId, EnchOption option, Runnable swapSelf) {
        FlowLayout container = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        container.gap(2);
        
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(6);
        row.padding(Insets.of(3, 6, 3, 6));
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.cursorStyle(CursorStyle.HAND);
        row.surface(roundedSurface(ROW_BG));
        row.tooltip(Component.literal(option.id().toString()));

        row.child(UIComponents.label(Component.literal(option.name())));
        row.child(horizontalSpacer());
        row.child(UIComponents.label(Component.literal("§8" + levelRange(option))).color(DIM));
        
        ButtonComponent addBtn = tinyButton("§b＋", b -> {});
        row.child(addBtn);
        
        container.child(row);
        
        FlowLayout levelPicker = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(0));
        levelPicker.gap(3);
        levelPicker.padding(Insets.of(4));
        levelPicker.surface(Surface.flat(LEVEL_PICKER_BG.argb()));
        levelPicker.child(UIComponents.label(Component.literal("§e等级:")).color(TITLE));
        
        int maxLevel = option.maxLevel() > 0 ? option.maxLevel() : 10;
        final boolean[] pickerExpanded = {false};
        for (int level = 1; level <= maxLevel; level++) {
            final int lv = level;
            ButtonComponent btn = UIComponents.button(Component.literal("§b" + lv), b -> {
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

    private FlowLayout buildFooter() {
        AutoTradeManager manager = AutoTradeManager.getInstance();
        FlowLayout footer = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
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

        speedButton = smallButton(speedLabel(), button -> {
            AutoTradeManager m = AutoTradeManager.getInstance();
            m.setSearchSpeed(m.getSearchSpeed().next());
            button.setMessage(speedLabel());
            button.tooltip(speedTooltip());
        });
        speedButton.tooltip(speedTooltip());
        footer.child(speedButton);

        footer.child(horizontalSpacer());

        startButtonRunning = manager.isActive();
        startButton = UIComponents.button(Component.literal(startButtonRunning ? "§c■ 停止" : "§2▶ 开始"),
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

    private Component speedLabel() {
        return Component.literal("§7速度: §e" + AutoTradeManager.getInstance().getSearchSpeed().label());
    }

    private Component speedTooltip() {
        SearchSpeed current = AutoTradeManager.getInstance().getSearchSpeed();
        StringBuilder text = new StringBuilder("§b服务端每 tick 的重掷强度\n§7档位越高刷得越快，占用服务端也越多");
        for (SearchSpeed speed : SearchSpeed.values()) {
            text.append('\n').append(speed == current ? "§a▶ " : "§7  ").append(speed.label()).append("§7: ")
                    .append(speed.unlimitedTime()
                            ? "不设时间预算，单 tick 最多刷 " + speed.maxAttemptsPerTick() + " 次（可能明显卡顿）"
                            : "单 tick 约 " + (speed.timeBudgetNanos() / 1_000_000) + "ms、最多 "
                                    + speed.maxAttemptsPerTick() + " 次");
        }
        return Component.literal(text.toString());
    }

    private void onStartStop() {
        AutoTradeManager manager = AutoTradeManager.getInstance();
        if (manager.isActive()) {
            manager.cancel();
            syncStartButton();
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

    private static ButtonComponent smallButton(Component text, Consumer<ButtonComponent> onPress) {
        ButtonComponent button = UIComponents.button(text, onPress);
        button.sizing(Sizing.content(), Sizing.fixed(24));
        return button;
    }

    private static ButtonComponent tinyButton(String text, Consumer<ButtonComponent> onPress) {
        ButtonComponent button = UIComponents.button(Component.literal(text), onPress);
        button.sizing(Sizing.fixed(24), Sizing.fixed(24));
        return button;
    }

    private static TextBoxComponent numericBox(String initial) {
        TextBoxComponent box = UIComponents.textBox(Sizing.fixed(40));
        box.setMaxLength(4);
        box.text(initial);
        return box;
    }

    private void cacheRegistries() {
        allItems.clear();
        itemNames.clear();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            allItems.add(item);
            itemNames.put(BuiltInRegistries.ITEM.getKey(item), item.getName(new ItemStack(item)).getString());
        }
        allItems.sort(Comparator.comparing(item -> itemNames.get(BuiltInRegistries.ITEM.getKey(item))));

        allEnchants.clear();
        enchById.clear();
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        var lookup = client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        lookup.listElements().forEach(holder -> {
            EnchOption option = new EnchOption(
                    holder.key().identifier(),
                    holder.value().description().getString(),
                    holder.value().getMaxLevel());
            allEnchants.add(option);
            enchById.put(option.id(), option);
        });
        allEnchants.sort(Comparator.comparing(EnchOption::name));
    }

    private static Surface cardSurface() {
        return Surface.flat(CARD_BG.argb()).and(Surface.outline(PANEL_BORDER.argb()));
    }

    private static Surface roundedSurface(Color color) {
        return Surface.flat(color.argb());
    }

    private static FlowLayout horizontalSpacer() {
        return UIContainers.horizontalFlow(Sizing.expand(), Sizing.fixed(0));
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

    private boolean hasEnchantOnItem(Identifier itemId, Identifier enchantId) {
        if (itemId == null) return false;
        TargetEntry entry = AutoTradeManager.getInstance().findItemTarget(itemId);
        return entry != null && hasRequirement(entry, enchantId);
    }

    private static boolean hasRequirement(TargetEntry entry, Identifier enchantId) {
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
        return cardKeyForItem(entry.id(), entry.potion());
    }

    private static String cardKeyForItem(Identifier itemId, Identifier potion) {
        return potion == null ? "item:" + itemId : "item:" + itemId + "|" + potion;
    }

    private String cardTitle(TargetEntry entry) {
        if (entry.isEnchantedBook()) {
            if (entry.enchants().isEmpty()) return "附魔书";
            return entry.enchants().stream()
                    .map(requirement -> enchantName(requirement.id()))
                    .collect(Collectors.joining(" + "));
        }
        return entry.potion() == null
                ? itemName(entry.id())
                : itemName(entry.id()) + " · " + VillagerTradeData.potionName(entry.potion()).getString();
    }

    private String enchantName(Identifier id) {
        EnchOption option = enchById.get(id);
        return option != null ? option.name() : id.getPath();
    }

    private String itemName(Identifier id) {
        String cached = itemNames.get(id);
        if (cached != null) return cached;
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
        if (client.player != null) client.player.sendSystemMessage(Component.literal(message));
    }
}
