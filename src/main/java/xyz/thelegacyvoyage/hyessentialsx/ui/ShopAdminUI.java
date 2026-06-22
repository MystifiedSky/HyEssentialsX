package xyz.thelegacyvoyage.hyessentialsx.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.math.vector.Vector3i;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopAdminDraftCache;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopNpcInteractionRegistry;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopChestModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopItemModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopTradeModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopNpcModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ShopContainerUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ShopNpcNameplateUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ShopAdminUI extends InteractiveCustomUIPage<ShopAdminUI.UIEventData> {

    private static final String LAYOUT = "hyessentialsx/ShopAdmin.ui";
    private static final String TRADE_ROW_LAYOUT = "hyessentialsx/ShopConfigureTradeItem.ui";
    private static final String ITEM_ICON_LAYOUT = "hyessentialsx/ShopTradeItemIconSmall.ui";
    private static final String SELECTED_ITEM_LAYOUT = "hyessentialsx/ShopSelectedItem.ui";
    private static final String PREVIEW_ROW_LAYOUT = "hyessentialsx/ShopBrowseTradeItem.ui";
    private static final int TRADES_PER_PAGE = 4;

    private final PlayerRef playerRef;
    private final ShopManager shopManager;
    private final EconomyManager economy;
    private final ShopAdminDraftCache draftCache;
    private final StorageManager storage;
    private final ConfigManager config;
    private ShopModel shop;

    private Tab tab = Tab.TRADES;
    private int tradePage = 0;
    private boolean useMoney = true;
    private boolean sellTrade = false;
    private List<String> npcRoles = List.of();
    private int npcRoleIndex = 0;
    private String npcRoleSelected = "";
    private ShopItemModel pendingCostItem;
    private ShopItemModel pendingOutputItem;
    private String pendingPriceText = "";
    private String pendingNameText = "";
    private String pendingStockLimitText = "";
    private String pendingStockResetDaysText = "";
    private String pendingMoneyStockLimitText = "";
    private String pendingEditorInputText = "";
    private String pendingCostQtyText = "";
    private String pendingOutputQtyText = "";
    private int editingIndex = -1;

    public ShopAdminUI(@Nonnull PlayerRef playerRef,
                       @Nonnull ShopManager shopManager,
                       @Nonnull EconomyManager economy,
                       @Nonnull ShopModel shop,
                       @Nonnull ShopAdminDraftCache draftCache) {
        this(playerRef, shopManager, economy, shop, draftCache, null, null);
    }

    public ShopAdminUI(@Nonnull PlayerRef playerRef,
                       @Nonnull ShopManager shopManager,
                       @Nonnull EconomyManager economy,
                       @Nonnull ShopModel shop,
                       @Nonnull ShopAdminDraftCache draftCache,
                       @Nullable StorageManager storage,
                       @Nullable ConfigManager config) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.playerRef = playerRef;
        this.shopManager = shopManager;
        this.economy = economy;
        this.shop = shop;
        this.draftCache = draftCache;
        this.storage = storage;
        this.config = config;
        restoreDraft();
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt,
                      @Nonnull Store<EntityStore> store) {
        cmd.append(LAYOUT);
        rebuild(ref, store, cmd, evt);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull UIEventData data) {
        if (data.priceInput != null) {
            pendingPriceText = data.priceInput;
        }
        if (data.shopNameInput != null) {
            pendingNameText = data.shopNameInput;
        }
        if (data.stockLimitInput != null) {
            pendingStockLimitText = data.stockLimitInput;
        }
        if (data.stockResetInput != null) {
            pendingStockResetDaysText = data.stockResetInput;
        }
        if (data.moneyStockLimitInput != null) {
            pendingMoneyStockLimitText = data.moneyStockLimitInput;
        }
        if (data.editorInput != null) {
            pendingEditorInputText = data.editorInput;
        }
        if (data.costQtyInput != null) {
            pendingCostQtyText = data.costQtyInput;
        }
        if (data.outputQtyInput != null) {
            pendingOutputQtyText = data.outputQtyInput;
        }

        if (data.action == null) return;
        switch (data.action) {
            case "tab_trades" -> tab = Tab.TRADES;
            case "tab_settings" -> tab = Tab.SETTINGS;
            case "tab_preview" -> tab = Tab.PREVIEW;
            case "trade_prev" -> {
                if (tradePage > 0) tradePage--;
            }
            case "trade_next" -> {
                int total = getTotalPages();
                if (tradePage < total - 1) tradePage++;
            }
            case "delete_trade" -> deleteTrade(data.tradeIndex);
            case "edit_trade" -> editTrade(data.tradeIndex);
            case "payment_money" -> useMoney = true;
            case "payment_items" -> {
                useMoney = false;
                sellTrade = false;
            }
            case "money_buy" -> sellTrade = false;
            case "money_sell" -> sellTrade = true;
            case "npc_role_prev" -> selectNpcRole(-1);
            case "npc_role_next" -> selectNpcRole(1);
            case "npc_role_apply" -> applyNpcRole();
            case "use_cost_hand" -> setCostFromHand(ref, store);
            case "use_output_hand" -> setOutputFromHand(ref, store);
            case "clear_cost" -> pendingCostItem = null;
            case "clear_output" -> pendingOutputItem = null;
            case "save_trade" -> saveTrade();
            case "save_name" -> saveName();
            case "toggle_open" -> toggleOpen();
            case "save_stock_settings" -> saveStockSettings();
            case "add_editor" -> addEditor();
            case "remove_editor" -> removeEditor();
            case "link_chest" -> linkChest(ref, store);
            case "unlink_chest" -> unlinkChest(ref, store);
            case "clear_chests" -> clearChests();
            default -> {
            }
        }
        syncPendingQuantitiesFromInputs();
        persistDraft();
        refresh(ref, store);
    }

    private void refresh(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        rebuild(ref, store, cmd, evt);
        sendUpdate(cmd, evt, false);
    }

    private void rebuild(@Nonnull Ref<EntityStore> ref,
                         @Nonnull Store<EntityStore> store,
                         @Nonnull UICommandBuilder cmd,
                         @Nonnull UIEventBuilder evt) {
        reloadShop();
        ensureNpcRoles();

        cmd.set("#TradesTabContent.Visible", tab == Tab.TRADES);
        cmd.set("#SettingsTabContent.Visible", tab == Tab.SETTINGS);
        cmd.set("#PreviewTabContent.Visible", tab == Tab.PREVIEW);

        cmd.set("#PaymentMoney.Disabled", useMoney);
        cmd.set("#PaymentItems.Disabled", !useMoney);
        cmd.set("#MoneySection.Visible", useMoney);
        cmd.set("#MoneyModeRow.Visible", useMoney);
        cmd.set("#ItemCostSection.Visible", !useMoney || sellTrade);
        cmd.set("#OutputSection.Visible", !(useMoney && sellTrade));
        cmd.set("#MoneyModeBuy.Disabled", !sellTrade);
        cmd.set("#MoneyModeSell.Disabled", sellTrade);
        boolean showStockLimit = !(useMoney && sellTrade) && !shop.isPlayerShop();
        cmd.set("#StockLimitSection.Visible", showStockLimit);
        cmd.set("#CostQtyRow.Visible", (!useMoney || sellTrade) && pendingCostItem != null);
        cmd.set("#OutputQtyRow.Visible", pendingOutputItem != null && !(useMoney && sellTrade));
        cmd.set("#NpcRoleLabel.Text", npcRoleSelected.isBlank() ? "None" : npcRoleSelected);
        cmd.set("#NpcRolePrev.Disabled", npcRoles.size() <= 1);
        cmd.set("#NpcRoleNext.Disabled", npcRoles.size() <= 1);

        cmd.set("#PriceInput #SearchInput.Value", pendingPriceText == null ? "" : pendingPriceText);
        cmd.set("#ShopNameInput #SearchInput.Value", pendingNameText == null ? "" : pendingNameText);
        cmd.set("#StockLimitInput #SearchInput.Value", pendingStockLimitText == null ? "" : pendingStockLimitText);
        cmd.set("#StockResetInput #SearchInput.Value", pendingStockResetDaysText == null ? "" : pendingStockResetDaysText);
        cmd.set("#MoneyStockLimitInput #SearchInput.Value", pendingMoneyStockLimitText == null ? "" : pendingMoneyStockLimitText);
        cmd.set("#EditorInput #SearchInput.Value", pendingEditorInputText == null ? "" : pendingEditorInputText);
        cmd.set("#CostQtyInput #SearchInput.Value", pendingCostQtyText == null ? "" : pendingCostQtyText);
        cmd.set("#OutputQtyInput #SearchInput.Value", pendingOutputQtyText == null ? "" : pendingOutputQtyText);
        cmd.set("#MoneyStockSection.Visible", !shop.isPlayerShop());
        cmd.set("#StockResetSection.Visible", !shop.isPlayerShop());
        cmd.set("#PlayerShopSettings.Visible", shop.isPlayerShop());

        buildTradeList(cmd, evt);
        buildSelectedItems(cmd);
        buildSettings(cmd);
        buildPreview(cmd);

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabTrades",
                EventData.of("Action", "tab_trades"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabSettings",
                EventData.of("Action", "tab_settings"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabPreview",
                EventData.of("Action", "tab_preview"), false);

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TradePrevPage",
                EventData.of("Action", "trade_prev"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TradeNextPage",
                EventData.of("Action", "trade_next"), false);

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PaymentMoney",
                EventData.of("Action", "payment_money"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PaymentItems",
                EventData.of("Action", "payment_items"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#MoneyModeBuy",
                EventData.of("Action", "money_buy"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#MoneyModeSell",
                EventData.of("Action", "money_sell"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NpcRolePrev",
                EventData.of("Action", "npc_role_prev"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NpcRoleNext",
                EventData.of("Action", "npc_role_next"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ApplyNpcRole",
                EventData.of("Action", "npc_role_apply"), false);

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#UseCostHand",
                EventData.of("Action", "use_cost_hand"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#UseOutputHand",
                EventData.of("Action", "use_output_hand"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ClearCost",
                EventData.of("Action", "clear_cost"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ClearOutput",
                EventData.of("Action", "clear_output"), false);

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SaveTradeButton",
                EventData.of("Action", "save_trade"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SaveNameButton",
                EventData.of("Action", "save_name"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleOpenButton",
                EventData.of("Action", "toggle_open"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SaveStockSettings",
                EventData.of("Action", "save_stock_settings"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#AddEditor",
                EventData.of("Action", "add_editor"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#RemoveEditor",
                EventData.of("Action", "remove_editor"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#LinkChest",
                EventData.of("Action", "link_chest"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#UnlinkChest",
                EventData.of("Action", "unlink_chest"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ClearChests",
                EventData.of("Action", "clear_chests"), false);

        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PriceInput #SearchInput",
                EventData.of("@PriceInput", "#PriceInput #SearchInput.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ShopNameInput #SearchInput",
                EventData.of("@ShopNameInput", "#ShopNameInput #SearchInput.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#StockLimitInput #SearchInput",
                EventData.of("@StockLimitInput", "#StockLimitInput #SearchInput.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#StockResetInput #SearchInput",
                EventData.of("@StockResetInput", "#StockResetInput #SearchInput.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MoneyStockLimitInput #SearchInput",
                EventData.of("@MoneyStockLimitInput", "#MoneyStockLimitInput #SearchInput.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#EditorInput #SearchInput",
                EventData.of("@EditorInput", "#EditorInput #SearchInput.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#CostQtyInput #SearchInput",
                EventData.of("@CostQtyInput", "#CostQtyInput #SearchInput.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#OutputQtyInput #SearchInput",
                EventData.of("@OutputQtyInput", "#OutputQtyInput #SearchInput.Value"), false);
    }

    private void buildTradeList(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt) {
        List<ShopTradeModel> trades = new ArrayList<>(shop.getTrades());
        int totalPages = Math.max(1, (int) Math.ceil(trades.size() / (double) TRADES_PER_PAGE));
        if (tradePage >= totalPages) tradePage = totalPages - 1;
        int start = tradePage * TRADES_PER_PAGE;
        int end = Math.min(trades.size(), start + TRADES_PER_PAGE);

        cmd.clear("#TradeCards");
        cmd.set("#NoTradesLabel.Visible", trades.isEmpty());
        cmd.set("#TradePageInfo.Text", "Page " + (tradePage + 1) + "/" + totalPages);
        cmd.set("#TradePrevPage.Disabled", tradePage <= 0);
        cmd.set("#TradeNextPage.Disabled", tradePage >= totalPages - 1);

        for (int idx = start; idx < end; idx++) {
            ShopTradeModel trade = trades.get(idx);
            String cardSelector = "#TradeCards[" + (idx - start) + "]";
            cmd.append("#TradeCards", TRADE_ROW_LAYOUT);

            buildTradeRowItems(cmd, cardSelector + " #InputItems", trade.getCostItems(), trade);
            buildTradeRowItems(cmd, cardSelector + " #OutputItems", trade.getRewardItems(), trade);

            if (trade.isMoneyTrade()) {
                cmd.set(cardSelector + " #StockStatus.Text", trade.isSellTrade() ? "Sell" : "Buy");
                if (trade.hasStockLimit()) {
                    cmd.set(cardSelector + " #StockCount.Text",
                            "Stock " + trade.getStockCurrent() + "/" + trade.getStockLimit());
                } else {
                    cmd.set(cardSelector + " #StockCount.Text", economy.formatAmount(trade.getMoneyCost()));
                }
            } else {
                cmd.set(cardSelector + " #StockStatus.Text", "Items");
                if (trade.hasStockLimit()) {
                    cmd.set(cardSelector + " #StockCount.Text",
                            "Stock " + trade.getStockCurrent() + "/" + trade.getStockLimit());
                } else {
                    cmd.set(cardSelector + " #StockCount.Text", "");
                }
            }

            evt.addEventBinding(CustomUIEventBindingType.Activating, cardSelector + " #DeleteButton",
                    new EventData().append("Action", "delete_trade").append("Trade", String.valueOf(idx)), false);
            evt.addEventBinding(CustomUIEventBindingType.Activating, cardSelector + " #EditButton",
                    new EventData().append("Action", "edit_trade").append("Trade", String.valueOf(idx)), false);
        }
    }

    private void buildTradeRowItems(@Nonnull UICommandBuilder cmd,
                                    @Nonnull String containerSelector,
                                    @Nonnull List<ShopItemModel> items,
                                    @Nonnull ShopTradeModel trade) {
        cmd.clear(containerSelector);
        if (trade.isMoneyTrade() && !trade.isSellTrade() && containerSelector.endsWith("InputItems")) {
            cmd.appendInline(containerSelector,
                    "Label { Anchor: (Height: 20); Style: (FontSize: 12, TextColor: #ffffff); Text: \"" +
                            economy.formatAmount(trade.getMoneyCost()) + "\"; }");
            return;
        }
        if (trade.isMoneyTrade() && trade.isSellTrade() && containerSelector.endsWith("OutputItems")) {
            cmd.appendInline(containerSelector,
                    "Label { Anchor: (Height: 20); Style: (FontSize: 12, TextColor: #ffffff); Text: \"" +
                            economy.formatAmount(trade.getMoneyCost()) + "\"; }");
            return;
        }
        if (items.isEmpty()) {
            cmd.appendInline(containerSelector,
                    "Label { Anchor: (Height: 20); Style: (FontSize: 12, TextColor: #888888); Text: \"None\"; }");
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            ShopItemModel item = items.get(i);
            String itemSelector = containerSelector + "[" + i + "]";
            cmd.append(containerSelector, ITEM_ICON_LAYOUT);
            cmd.set(itemSelector + " #ItemIcon.ItemId", item.getItemId());
            cmd.set(itemSelector + " #Quantity.Text", "x" + item.getQuantity());
            cmd.set(itemSelector + " #InfoLabel.Text", "");
        }
    }

    private void buildSelectedItems(@Nonnull UICommandBuilder cmd) {
        cmd.clear("#SelectedCostItems");
        cmd.clear("#SelectedOutputItems");

        if ((!useMoney || sellTrade) && pendingCostItem != null) {
            if (pendingCostQtyText == null || pendingCostQtyText.isBlank()) {
                pendingCostQtyText = String.valueOf(pendingCostItem.getQuantity());
            }
            cmd.append("#SelectedCostItems", SELECTED_ITEM_LAYOUT);
            String selector = "#SelectedCostItems[0]";
            cmd.set(selector + " #ItemIcon.ItemId", pendingCostItem.getItemId());
            cmd.set(selector + " #Quantity.Text", "x" + pendingCostItem.getQuantity());
            cmd.set(selector + " #EditQty.Visible", false);
        }

        if (pendingOutputItem != null) {
            if (pendingOutputQtyText == null || pendingOutputQtyText.isBlank()) {
                pendingOutputQtyText = String.valueOf(pendingOutputItem.getQuantity());
            }
            cmd.append("#SelectedOutputItems", SELECTED_ITEM_LAYOUT);
            String selector = "#SelectedOutputItems[0]";
            cmd.set(selector + " #ItemIcon.ItemId", pendingOutputItem.getItemId());
            cmd.set(selector + " #Quantity.Text", "x" + pendingOutputItem.getQuantity());
            cmd.set(selector + " #EditQty.Visible", false);
        }

        if (editingIndex >= 0) {
            cmd.set("#EditModeLabel.Text", "Editing trade #" + (editingIndex + 1));
        } else {
            cmd.set("#EditModeLabel.Text", "");
        }
    }

    private void buildSettings(@Nonnull UICommandBuilder cmd) {
        cmd.set("#CurrentShopName.Text", "Current: " + shop.getDisplayName());
        cmd.set("#ShopStatusLabel.Text", shop.isOpen() ? "Open" : "Closed");
        if (pendingStockResetDaysText == null || pendingStockResetDaysText.isBlank()) {
            pendingStockResetDaysText = String.valueOf(shop.getStockResetDays());
        }
        if (pendingMoneyStockLimitText == null || pendingMoneyStockLimitText.isBlank()) {
            pendingMoneyStockLimitText = String.valueOf(shop.getMoneyStockLimit());
        }
        if (shop.isPlayerShop()) {
            buildPlayerSettings(cmd);
        }
    }

    private void buildPlayerSettings(@Nonnull UICommandBuilder cmd) {
        int chestCount = shop.getChests().size();
        cmd.set("#LinkedChestsCount.Text", String.valueOf(chestCount));
        cmd.set("#LinkedChestsLabel.Text", "Linked chests: " + chestCount);
        cmd.set("#EditorsList.Text", formatEditors());
    }

    @Nonnull
    private String formatEditors() {
        if (shop.getEditors().isEmpty()) {
            return "Editors: none";
        }
        List<String> names = new ArrayList<>();
        for (String raw : shop.getEditors()) {
            if (raw == null || raw.isBlank()) continue;
            String display = raw;
            if (storage != null) {
                try {
                    java.util.UUID uuid = java.util.UUID.fromString(raw);
                    var data = storage.getPlayerData(uuid);
                    if (data != null && data.getLastKnownName() != null && !data.getLastKnownName().isBlank()) {
                        display = data.getLastKnownName();
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
            names.add(display);
        }
        return "Editors: " + (names.isEmpty() ? "none" : String.join(", ", names));
    }

    private void buildPreview(@Nonnull UICommandBuilder cmd) {
        cmd.set("#PreviewShopName.Text", shop.getDisplayName());
        cmd.set("#PreviewClosedLabel.Visible", !shop.isOpen());
        cmd.clear("#PreviewTradeCards");

        List<ShopTradeModel> trades = shop.getTrades();
        cmd.set("#PreviewNoTradesLabel.Visible", trades.isEmpty());

        for (int i = 0; i < trades.size(); i++) {
            ShopTradeModel trade = trades.get(i);
            String cardSelector = "#PreviewTradeCards[" + i + "]";
            cmd.append("#PreviewTradeCards", PREVIEW_ROW_LAYOUT);
            buildPreviewCost(cmd, cardSelector, trade);
            buildPreviewRewards(cmd, cardSelector, trade);
            cmd.set(cardSelector + " #TradeButton.Disabled", true);
            cmd.set(cardSelector + " #StatusLabel.Text", shop.isOpen() ? "Preview" : "Closed");
            cmd.set(cardSelector + " #StatusLabel.Style.TextColor", shop.isOpen() ? "#888888" : "#ff6666");
        }
    }

    private void buildPreviewCost(@Nonnull UICommandBuilder cmd,
                                  @Nonnull String cardSelector,
                                  @Nonnull ShopTradeModel trade) {
        cmd.clear(cardSelector + " #PayItems");
        if (trade.isMoneyTrade() && !trade.isSellTrade()) {
            cmd.appendInline(cardSelector + " #PayItems",
                    "Label { Anchor: (Height: 24); Style: (FontSize: 14, TextColor: #ffffff); Text: \"Price: " +
                            economy.formatAmount(trade.getMoneyCost()) + "\"; }");
            return;
        }
        List<ShopItemModel> items = trade.getCostItems();
        if (items.isEmpty()) {
            cmd.appendInline(cardSelector + " #PayItems",
                    "Label { Anchor: (Height: 24); Style: (FontSize: 14, TextColor: #888888); Text: \"No cost\"; }");
            return;
        }
        String rowSelector = cardSelector + " #PayItems[0]";
        cmd.appendInline(cardSelector + " #PayItems", "Group { LayoutMode: Left; Anchor: (Height: 75); }");
        for (int i = 0; i < items.size(); i++) {
            ShopItemModel item = items.get(i);
            String itemSelector = rowSelector + "[" + i + "]";
            cmd.append(rowSelector, ITEM_ICON_LAYOUT);
            cmd.set(itemSelector + " #ItemIcon.ItemId", item.getItemId());
            cmd.set(itemSelector + " #Quantity.Text", "x" + item.getQuantity());
            cmd.set(itemSelector + " #InfoLabel.Text", "");
        }
    }

    private void buildPreviewRewards(@Nonnull UICommandBuilder cmd,
                                     @Nonnull String cardSelector,
                                     @Nonnull ShopTradeModel trade) {
        cmd.clear(cardSelector + " #GetItems");
        if (trade.isMoneyTrade() && trade.isSellTrade()) {
            cmd.appendInline(cardSelector + " #GetItems",
                    "Label { Anchor: (Height: 24); Style: (FontSize: 14, TextColor: #ffffff); Text: \"You receive: " +
                            economy.formatAmount(trade.getMoneyCost()) + "\"; }");
            return;
        }
        List<ShopItemModel> rewards = trade.getRewardItems();
        if (rewards.isEmpty()) {
            cmd.appendInline(cardSelector + " #GetItems",
                    "Label { Anchor: (Height: 24); Style: (FontSize: 14, TextColor: #888888); Text: \"No rewards\"; }");
            return;
        }
        String rowSelector = cardSelector + " #GetItems[0]";
        cmd.appendInline(cardSelector + " #GetItems", "Group { LayoutMode: Left; Anchor: (Height: 75); }");
        for (int i = 0; i < rewards.size(); i++) {
            ShopItemModel item = rewards.get(i);
            String itemSelector = rowSelector + "[" + i + "]";
            cmd.append(rowSelector, ITEM_ICON_LAYOUT);
            cmd.set(itemSelector + " #ItemIcon.ItemId", item.getItemId());
            cmd.set(itemSelector + " #Quantity.Text", "x" + item.getQuantity());
            cmd.set(itemSelector + " #InfoLabel.Text", "");
        }
    }

    private void setCostFromHand(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.sendPrefixedKey(playerRef, "shop.admin.inventory_failed", java.util.Map.of());
            return;
        }
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            Messages.sendPrefixedKey(playerRef, "shop.admin.inventory_failed", java.util.Map.of());
            return;
        }
        ItemStack hand = inventory.getItemInHand();
        if (hand == null || hand.isEmpty()) {
            Messages.sendPrefixedKey(playerRef, "shop.admin.cost_hand_required", java.util.Map.of());
            return;
        }
        int qty = parsePositiveInt(pendingCostQtyText);
        if (qty <= 0) {
            qty = Math.max(1, hand.getQuantity());
        }
        pendingCostItem = new ShopItemModel(hand.getItemId(), qty);
        pendingCostQtyText = String.valueOf(qty);
        if (!useMoney) {
            useMoney = false;
        }
    }

    private void setOutputFromHand(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.sendPrefixedKey(playerRef, "shop.admin.inventory_failed", java.util.Map.of());
            return;
        }
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            Messages.sendPrefixedKey(playerRef, "shop.admin.inventory_failed", java.util.Map.of());
            return;
        }
        ItemStack hand = inventory.getItemInHand();
        if (hand == null || hand.isEmpty()) {
            Messages.sendPrefixedKey(playerRef, "shop.admin.output_hand_required", java.util.Map.of());
            return;
        }
        int qty = parsePositiveInt(pendingOutputQtyText);
        if (qty <= 0) {
            qty = Math.max(1, hand.getQuantity());
        }
        pendingOutputItem = new ShopItemModel(hand.getItemId(), qty);
        pendingOutputQtyText = String.valueOf(qty);
    }

    private void saveTrade() {
        syncPendingQuantitiesFromInputs();
        if (!useMoney || !sellTrade) {
            if (pendingOutputItem == null || pendingOutputItem.getItemId().isBlank()) {
            Messages.sendPrefixedKey(playerRef, "shop.admin.output_required", java.util.Map.of());
            return;
        }
        }
        ShopTradeModel trade;
        int previousLimit = 0;
        if (editingIndex >= 0 && editingIndex < shop.getTrades().size()) {
            trade = shop.getTrades().get(editingIndex);
            previousLimit = trade.getStockLimit();
        } else {
            trade = new ShopTradeModel(UUID.randomUUID().toString());
        }

        if (useMoney && sellTrade) {
            trade.setRewardItems(List.of());
        } else {
            trade.setRewardItems(List.of(pendingOutputItem));
        }
        trade.setEnabled(true);

        if (useMoney) {
            long price = parsePrice(pendingPriceText);
            if (price <= 0L) {
                Messages.sendPrefixedKey(playerRef, "shop.admin.price_invalid", java.util.Map.of());
                return;
            }
            if (!economy.isEnabled()) {
                Messages.sendPrefixedKey(playerRef, "shop.admin.economy_disabled", java.util.Map.of());
                return;
            }
            trade.setMoneyCost(price);
            if (sellTrade) {
                if (pendingCostItem == null || pendingCostItem.getItemId().isBlank()) {
                    Messages.sendPrefixedKey(playerRef, "shop.admin.cost_required", java.util.Map.of());
                    return;
                }
                trade.setCostItems(List.of(pendingCostItem));
            } else {
                trade.setCostItems(List.of());
            }
            trade.setSellTrade(sellTrade);
        } else {
            if (pendingCostItem == null || pendingCostItem.getItemId().isBlank()) {
                Messages.sendPrefixedKey(playerRef, "shop.admin.cost_required", java.util.Map.of());
                return;
            }
            trade.setMoneyCost(0L);
            trade.setCostItems(List.of(pendingCostItem));
            trade.setSellTrade(false);
        }

        if (editingIndex >= 0 && editingIndex < shop.getTrades().size()) {
            shop.getTrades().set(editingIndex, trade);
        } else {
            shop.getTrades().add(trade);
        }
        int newLimit = parsePositiveInt(pendingStockLimitText);
        if (shop.isPlayerShop()) {
            trade.setStockLimit(0);
            trade.setStockCurrent(0);
        } else {
            trade.setStockLimit(newLimit);
            if (newLimit <= 0) {
                trade.setStockCurrent(0);
            } else if (previousLimit != newLimit) {
                if (trade.isSellTrade()) {
                    trade.setStockCurrent(Math.min(trade.getStockCurrent(), newLimit));
                } else {
                    int current = trade.getStockCurrent();
                    if (current <= 0) {
                        trade.setStockCurrent(newLimit);
                    } else {
                        trade.setStockCurrent(Math.min(current, newLimit));
                    }
                }
            } else if (trade.getStockCurrent() <= 0 && !trade.isSellTrade()) {
                trade.setStockCurrent(newLimit);
            }
        }
        shopManager.saveShop(shop);
        clearPending();
        Messages.sendPrefixedKey(playerRef, "shop.admin.trade_saved", java.util.Map.of());
    }

    private void saveStockSettings() {
        if (shop.isPlayerShop()) {
            Messages.sendPrefixedKey(playerRef, "shop.admin.stock_not_applicable", java.util.Map.of());
            return;
        }
        int days = parsePositiveInt(pendingStockResetDaysText);
        shop.setStockResetDays(days);
        if (days > 0) {
            long millis = days * 24L * 60L * 60L * 1000L;
            shop.setStockResetAt(System.currentTimeMillis() + millis);
        } else {
            shop.setStockResetAt(0L);
        }
        int moneyLimit = parsePositiveInt(pendingMoneyStockLimitText);
        shop.setMoneyStockLimit(moneyLimit);
        if (moneyLimit <= 0) {
            shop.setMoneyStockCurrent(0L);
        } else {
            long current = shop.getMoneyStockCurrent();
            if (current <= 0L || current > moneyLimit) {
                shop.setMoneyStockCurrent(moneyLimit);
            }
        }
        shopManager.saveShop(shop);
        Messages.sendPrefixedKey(playerRef, "shop.admin.stock_saved", java.util.Map.of());
    }

    private void saveName() {
        String name = pendingNameText == null ? "" : pendingNameText.trim();
        if (name.isBlank()) {
            Messages.sendPrefixedKey(playerRef, "shop.admin.name_invalid", java.util.Map.of());
            return;
        }
        shop.setDisplayName(name);
        shopManager.saveShop(shop);
        updateNpcNameplates();
        Messages.sendPrefixedKey(playerRef, "shop.admin.name_updated", java.util.Map.of());
    }

    private void toggleOpen() {
        shop.setOpen(!shop.isOpen());
        shopManager.saveShop(shop);
        Messages.sendPrefixedKey(playerRef, shop.isOpen() ? "shop.admin.opened" : "shop.admin.closed", java.util.Map.of());
    }

    private void addEditor() {
        if (!canManagePlayerSettings()) return;
        String input = pendingEditorInputText == null ? "" : pendingEditorInputText.trim();
        if (input.isBlank()) {
            Messages.sendPrefixedKey(playerRef, "shop.player.editor.invalid", java.util.Map.of());
            return;
        }
        UUID uuid = resolvePlayerUuid(input);
        if (uuid == null) {
            Messages.sendPrefixedKey(playerRef, "shop.player.editor.not_found", java.util.Map.of());
            return;
        }
        String uuidStr = uuid.toString();
        if (shop.getOwnerUuid().equalsIgnoreCase(uuidStr)) {
            Messages.sendPrefixedKey(playerRef, "shop.player.editor.is_owner", java.util.Map.of());
            return;
        }
        if (shop.getEditors().stream().anyMatch(id -> id.equalsIgnoreCase(uuidStr))) {
            Messages.sendPrefixedKey(playerRef, "shop.player.editor.exists", java.util.Map.of());
            return;
        }
        shop.getEditors().add(uuidStr);
        shopManager.saveShop(shop);
        Messages.sendPrefixedKey(playerRef, "shop.player.editor.added", java.util.Map.of());
    }

    private void removeEditor() {
        if (!canManagePlayerSettings()) return;
        String input = pendingEditorInputText == null ? "" : pendingEditorInputText.trim();
        if (input.isBlank()) {
            Messages.sendPrefixedKey(playerRef, "shop.player.editor.invalid", java.util.Map.of());
            return;
        }
        UUID uuid = resolvePlayerUuid(input);
        String match = uuid != null ? uuid.toString() : input;
        boolean removed = shop.getEditors().removeIf(id -> id != null && id.equalsIgnoreCase(match));
        if (!removed && uuid != null) {
            removed = shop.getEditors().removeIf(id -> id != null && id.equalsIgnoreCase(input));
        }
        if (!removed) {
            Messages.sendPrefixedKey(playerRef, "shop.player.editor.not_found", java.util.Map.of());
            return;
        }
        shopManager.saveShop(shop);
        Messages.sendPrefixedKey(playerRef, "shop.player.editor.removed", java.util.Map.of());
    }

    private void linkChest(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (!canManagePlayerSettings()) return;
        World world = resolveWorld(store);
        if (world == null) {
            Messages.sendPrefixedKey(playerRef, "shop.player.chest.world_failed", java.util.Map.of());
            return;
        }
        int radius = config.getPlayerShopChestLinkRadius();
        Vector3i pos = ShopContainerUtil.findTargetedContainer(world, store, ref, Math.max(5, radius + 1));
        if (pos == null) {
            Messages.sendPrefixedKey(playerRef, "shop.player.chest.look_at", java.util.Map.of());
            return;
        }
        boolean already = shop.getChests().stream().anyMatch(chest ->
                chest != null
                        && chest.getWorldId().equalsIgnoreCase(world.getName())
                        && chest.getPosition().equals(pos));
        if (already) {
            Messages.sendPrefixedKey(playerRef, "shop.player.chest.already", java.util.Map.of());
            return;
        }
        if (!shop.getNpcs().isEmpty() && !ShopContainerUtil.isWithinRadius(pos, shop.getNpcs(), radius)) {
            Messages.sendPrefixedKey(playerRef, "shop.player.chest.too_far", java.util.Map.of());
            return;
        }
        shop.getChests().add(new ShopChestModel(pos, world.getName()));
        shopManager.saveShop(shop);
        Messages.sendPrefixedKey(playerRef, "shop.player.chest.linked", java.util.Map.of());
    }

    private void unlinkChest(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (!canManagePlayerSettings()) return;
        World world = resolveWorld(store);
        if (world == null) {
            Messages.sendPrefixedKey(playerRef, "shop.player.chest.world_failed", java.util.Map.of());
            return;
        }
        int radius = config.getPlayerShopChestLinkRadius();
        Vector3i pos = ShopContainerUtil.findTargetedContainer(world, store, ref, Math.max(5, radius + 1));
        if (pos == null) {
            Messages.sendPrefixedKey(playerRef, "shop.player.chest.look_at", java.util.Map.of());
            return;
        }
        boolean removed = shop.getChests().removeIf(chest ->
                chest != null
                        && chest.getWorldId().equalsIgnoreCase(world.getName())
                        && chest.getPosition().equals(pos));
        if (!removed) {
            Messages.sendPrefixedKey(playerRef, "shop.player.chest.not_found", java.util.Map.of());
            return;
        }
        shopManager.saveShop(shop);
        Messages.sendPrefixedKey(playerRef, "shop.player.chest.unlinked", java.util.Map.of());
    }

    private void clearChests() {
        if (!canManagePlayerSettings()) return;
        if (shop.getChests().isEmpty()) {
            Messages.sendPrefixedKey(playerRef, "shop.player.chest.not_found", java.util.Map.of());
            return;
        }
        shop.getChests().clear();
        shopManager.saveShop(shop);
        Messages.sendPrefixedKey(playerRef, "shop.player.chest.cleared", java.util.Map.of());
    }

    private boolean canManagePlayerSettings() {
        return shop.isPlayerShop() && storage != null && config != null;
    }

    @Nullable
    private UUID resolvePlayerUuid(@Nonnull String input) {
        if (input.isBlank()) return null;
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
        }
        if (storage == null) return null;
        return storage.resolvePlayerIdByName(input);
    }

    @Nullable
    private World resolveWorld(@Nonnull Store<EntityStore> store) {
        Object external = store.getExternalData();
        if (external instanceof EntityStore entityStore) {
            return entityStore.getWorld();
        }
        return null;
    }

    private void updateNpcNameplates() {
        String displayName = shop.getDisplayName();
        for (World world : Universe.get().getWorlds().values()) {
            world.execute(() -> applyNameplatesInWorld(world, displayName));
        }
    }

    private void applyNameplatesInWorld(@Nonnull World world, @Nonnull String displayName) {
        for (ShopNpcModel npcModel : shop.getNpcs()) {
            if (!npcModel.getWorldId().equalsIgnoreCase(world.getName())) continue;
            java.util.UUID npcUuid;
            try {
                npcUuid = java.util.UUID.fromString(npcModel.getNpcId());
            } catch (Exception ignored) {
                continue;
            }
            Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(npcUuid);
            if (ref == null) continue;
            Store<EntityStore> store = world.getEntityStore().getStore();
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            if (npc == null) continue;
            ShopNpcNameplateUtil.apply(store, ref, displayName);
        }
    }

    private void applyNameplate(@Nonnull Store<EntityStore> store,
                                @Nonnull Ref<EntityStore> ref) {
        ShopNpcNameplateUtil.apply(store, ref, shop.getDisplayName());
    }

    private void deleteTrade(String rawIndex) {
        int idx = parseIndex(rawIndex);
        if (idx < 0 || idx >= shop.getTrades().size()) return;
        shop.getTrades().remove(idx);
        if (editingIndex == idx) {
            clearPending();
        }
        shopManager.saveShop(shop);
        Messages.sendPrefixedKey(playerRef, "shop.admin.trade_removed", java.util.Map.of());
    }

    private void editTrade(String rawIndex) {
        int idx = parseIndex(rawIndex);
        if (idx < 0 || idx >= shop.getTrades().size()) return;
        ShopTradeModel trade = shop.getTrades().get(idx);
        editingIndex = idx;
        pendingStockLimitText = trade.hasStockLimit() ? String.valueOf(trade.getStockLimit()) : "";
        if (trade.isMoneyTrade()) {
            useMoney = true;
            sellTrade = trade.isSellTrade();
            pendingPriceText = String.valueOf(trade.getMoneyCost());
            pendingCostItem = trade.isSellTrade()
                    ? (trade.getCostItems().isEmpty() ? null : trade.getCostItems().get(0))
                    : null;
        } else {
            useMoney = false;
            sellTrade = false;
            pendingPriceText = "";
            pendingCostItem = trade.getCostItems().isEmpty() ? null : trade.getCostItems().get(0);
        }
        pendingOutputItem = trade.getRewardItems().isEmpty() ? null : trade.getRewardItems().get(0);
        pendingCostQtyText = pendingCostItem != null ? String.valueOf(pendingCostItem.getQuantity()) : "";
        pendingOutputQtyText = pendingOutputItem != null ? String.valueOf(pendingOutputItem.getQuantity()) : "";
    }

    private void clearPending() {
        pendingCostItem = null;
        pendingOutputItem = null;
        pendingPriceText = "";
        pendingStockLimitText = "";
        pendingCostQtyText = "";
        pendingOutputQtyText = "";
        editingIndex = -1;
    }

    private void ensureNpcRoles() {
        if (!npcRoles.isEmpty()) {
            return;
        }
        NPCPlugin plugin = NPCPlugin.get();
        if (plugin == null) {
            npcRoles = List.of();
            npcRoleSelected = "";
            return;
        }
        List<String> roles = new ArrayList<>(plugin.getRoleTemplateNames(true));
        roles.sort(String.CASE_INSENSITIVE_ORDER);
        npcRoles = roles;
        String preferred = shop.getNpcRole();
        if (!preferred.isBlank() && roles.contains(preferred)) {
            npcRoleIndex = roles.indexOf(preferred);
            npcRoleSelected = preferred;
            return;
        }
        if (!npcRoleSelected.isBlank() && roles.contains(npcRoleSelected)) {
            npcRoleIndex = roles.indexOf(npcRoleSelected);
            return;
        }
        if (!shop.getNpcs().isEmpty()) {
            String current = shop.getNpcs().get(0).getRoleName();
            if (!current.isBlank() && roles.contains(current)) {
                npcRoleIndex = roles.indexOf(current);
                npcRoleSelected = current;
                return;
            }
        }
        if (!roles.isEmpty()) {
            npcRoleIndex = 0;
            npcRoleSelected = roles.get(0);
        } else {
            npcRoleSelected = "";
        }
    }

    private void selectNpcRole(int delta) {
        if (npcRoles.isEmpty()) return;
        int size = npcRoles.size();
        npcRoleIndex = ((npcRoleIndex + delta) % size + size) % size;
        npcRoleSelected = npcRoles.get(npcRoleIndex);
    }

    private void applyNpcRole() {
        if (npcRoleSelected.isBlank()) {
            Messages.sendPrefixedKey(playerRef, "shop.admin.npc_role.none_selected", java.util.Map.of());
            return;
        }
        NPCPlugin plugin = NPCPlugin.get();
        if (plugin == null) {
            Messages.sendPrefixedKey(playerRef, "shop.admin.npc_role.system_unavailable", java.util.Map.of());
            return;
        }
        int roleIndex = plugin.getIndex(npcRoleSelected);
        if (roleIndex < 0) {
            Messages.sendPrefixedKey(playerRef, "shop.admin.npc_role.not_found", java.util.Map.of());
            return;
        }
        shop.setNpcRole(npcRoleSelected);
        for (ShopNpcModel npc : shop.getNpcs()) {
            npc.setRoleName(npcRoleSelected);
        }
        shopManager.saveShop(shop);

        for (World world : Universe.get().getWorlds().values()) {
            world.execute(() -> respawnNpcRoleInWorld(world, roleIndex));
        }
        Messages.sendPrefixedKey(playerRef, "shop.admin.npc_role.updated",
                java.util.Map.of("role", npcRoleSelected));
    }

    private void respawnNpcRoleInWorld(@Nonnull World world, int roleIndex) {
        for (ShopNpcModel npcModel : shop.getNpcs()) {
            if (!npcModel.getWorldId().equalsIgnoreCase(world.getName())) continue;
            Store<EntityStore> store = world.getEntityStore().getStore();
            java.util.UUID npcUuid;
            try {
                npcUuid = java.util.UUID.fromString(npcModel.getNpcId());
            } catch (Exception ignored) {
                continue;
            }
            Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(npcUuid);
            if (ref == null) continue;
            NPCEntity existing = store.getComponent(ref, NPCEntity.getComponentType());
            if (existing == null) continue;

            Vector3d pos = null;
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform != null) {
                pos = transform.getPosition();
            }
            if (pos == null) continue;

            Vector3f rotation = transform != null && transform.getRotation() != null
                    ? transform.getRotation()
                    : new Vector3f(0f, 0f, 0f);

            existing.setDespawning(true);
            existing.setDespawnTime(0f);
            store.removeEntity(ref, com.hypixel.hytale.component.RemoveReason.REMOVE);

            var pair = NPCPlugin.get().spawnEntity(store, roleIndex, pos, rotation, null, null);
            if (pair == null) continue;
            Ref<EntityStore> newRef = pair.first();
            NPCEntity newNpc = pair.second();
            ShopNpcInteractionRegistry.applyNpcInteractions(store, newRef);
            applyNameplate(store, newRef);
            applyNpcDefaults(store, newRef, newNpc, npcModel.getPosition(), rotation, shop.getDisplayName());
            npcModel.setNpcId(newNpc.getUuid().toString());
        }
    }

    private void applyNpcDefaults(@Nonnull Store<EntityStore> store,
                                  @Nonnull Ref<EntityStore> npcRef,
                                  @Nonnull NPCEntity npc,
                                  @Nonnull Vector3i blockPos,
                                  @Nonnull Vector3f rotation,
                                  @Nonnull String displayName) {
        if (store.getComponent(npcRef, Interactable.getComponentType()) == null) {
            store.addComponent(npcRef, Interactable.getComponentType(), Interactable.INSTANCE);
        }
        if (store.getComponent(npcRef, Invulnerable.getComponentType()) == null) {
            store.addComponent(npcRef, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
        }
        if (store.getComponent(npcRef, Frozen.getComponentType()) == null) {
            store.addComponent(npcRef, Frozen.getComponentType(), Frozen.get());
        }
        ShopNpcNameplateUtil.apply(store, npcRef, displayName);
        MovementStatesComponent movementStates = store.getComponent(npcRef, MovementStatesComponent.getComponentType());
        if (movementStates != null) {
            MovementStates states = movementStates.getMovementStates();
            states.idle = true;
            states.horizontalIdle = true;
            states.walking = false;
            states.running = false;
            states.sprinting = false;
            states.onGround = true;
        }
        npc.setDespawnTime(Float.MAX_VALUE);
        npc.setDespawning(false);
        npc.setLeashPoint(new Vector3d(blockPos.getX() + 0.5D, blockPos.getY(), blockPos.getZ() + 0.5D));
        npc.setLeashHeading(rotation.getY());
    }

    private void restoreDraft() {
        if (draftCache == null) return;
        ShopAdminDraftCache.Draft draft = draftCache.get(playerRef.getUuid());
        if (draft == null) return;
        if (!draft.shopName.equalsIgnoreCase(shop.getName())) return;
        tab = parseTab(draft.tab);
        tradePage = Math.max(0, draft.tradePage);
        useMoney = draft.useMoney;
        sellTrade = draft.sellTrade;
        pendingCostItem = draft.pendingCostItem;
        pendingOutputItem = draft.pendingOutputItem;
        pendingPriceText = draft.pendingPriceText == null ? "" : draft.pendingPriceText;
        pendingNameText = draft.pendingNameText == null ? "" : draft.pendingNameText;
        editingIndex = draft.editingIndex;
        if (draft.npcRole != null && !draft.npcRole.isBlank()) {
            npcRoleSelected = draft.npcRole;
        }
        if (draft.stockResetDaysText != null) {
            pendingStockResetDaysText = draft.stockResetDaysText;
        }
        if (draft.moneyStockLimitText != null) {
            pendingMoneyStockLimitText = draft.moneyStockLimitText;
        }
        if (draft.stockLimitText != null) {
            pendingStockLimitText = draft.stockLimitText;
        }
        if (draft.costQtyText != null) {
            pendingCostQtyText = draft.costQtyText;
        }
        if (draft.outputQtyText != null) {
            pendingOutputQtyText = draft.outputQtyText;
        }
    }

    private void persistDraft() {
        if (draftCache == null) return;
        ShopAdminDraftCache.Draft draft = new ShopAdminDraftCache.Draft();
        draft.shopName = shop.getName();
        draft.tab = tab.name();
        draft.tradePage = tradePage;
        draft.useMoney = useMoney;
        draft.sellTrade = sellTrade;
        draft.pendingCostItem = pendingCostItem;
        draft.pendingOutputItem = pendingOutputItem;
        draft.pendingPriceText = pendingPriceText;
        draft.pendingNameText = pendingNameText;
        draft.editingIndex = editingIndex;
        draft.npcRole = npcRoleSelected;
        draft.stockResetDaysText = pendingStockResetDaysText;
        draft.moneyStockLimitText = pendingMoneyStockLimitText;
        draft.stockLimitText = pendingStockLimitText;
        draft.costQtyText = pendingCostQtyText;
        draft.outputQtyText = pendingOutputQtyText;
        draftCache.save(playerRef.getUuid(), draft);
    }

    @Nonnull
    private Tab parseTab(@Nullable String raw) {
        if (raw == null) return Tab.TRADES;
        try {
            return Tab.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return Tab.TRADES;
        }
    }

    private long parsePrice(String raw) {
        if (raw == null) return -1L;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return -1L;
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private int parsePositiveInt(String raw) {
        if (raw == null) return 0;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try {
            return Math.max(0, Integer.parseInt(digits));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void syncPendingQuantitiesFromInputs() {
        if (pendingCostItem != null) {
            int qty = parsePositiveInt(pendingCostQtyText);
            if (qty > 0) {
                pendingCostItem = new ShopItemModel(pendingCostItem.getItemId(), qty);
            }
        }
        if (pendingOutputItem != null) {
            int qty = parsePositiveInt(pendingOutputQtyText);
            if (qty > 0) {
                pendingOutputItem = new ShopItemModel(pendingOutputItem.getItemId(), qty);
            }
        }
    }

    private int parseIndex(String raw) {
        if (raw == null) return -1;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private int getTotalPages() {
        int trades = shop.getTrades().size();
        return Math.max(1, (int) Math.ceil(trades / (double) TRADES_PER_PAGE));
    }

    private void reloadShop() {
        ShopModel latest = shopManager.getShop(shop.getName());
        if (latest != null) {
            shop = latest;
        }
    }

    public void open(@Nonnull Player player, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        player.getPageManager().openCustomPage(ref, store, this);
    }

    public static class UIEventData {
        public static final BuilderCodec<UIEventData> CODEC = BuilderCodec.builder(UIEventData.class, UIEventData::new)
                .addField(new KeyedCodec<>("Action", Codec.STRING), (e, v) -> e.action = v, e -> e.action)
                .addField(new KeyedCodec<>("Trade", Codec.STRING), (e, v) -> e.tradeIndex = v, e -> e.tradeIndex)
                .addField(new KeyedCodec<>("@PriceInput", Codec.STRING), (e, v) -> e.priceInput = v, e -> e.priceInput)
                .addField(new KeyedCodec<>("@ShopNameInput", Codec.STRING), (e, v) -> e.shopNameInput = v, e -> e.shopNameInput)
                .addField(new KeyedCodec<>("@StockLimitInput", Codec.STRING), (e, v) -> e.stockLimitInput = v, e -> e.stockLimitInput)
                .addField(new KeyedCodec<>("@StockResetInput", Codec.STRING), (e, v) -> e.stockResetInput = v, e -> e.stockResetInput)
                .addField(new KeyedCodec<>("@MoneyStockLimitInput", Codec.STRING), (e, v) -> e.moneyStockLimitInput = v, e -> e.moneyStockLimitInput)
                .addField(new KeyedCodec<>("@EditorInput", Codec.STRING), (e, v) -> e.editorInput = v, e -> e.editorInput)
                .addField(new KeyedCodec<>("@CostQtyInput", Codec.STRING), (e, v) -> e.costQtyInput = v, e -> e.costQtyInput)
                .addField(new KeyedCodec<>("@OutputQtyInput", Codec.STRING), (e, v) -> e.outputQtyInput = v, e -> e.outputQtyInput)
                .build();

        private String action;
        private String tradeIndex;
        private String priceInput;
        private String shopNameInput;
        private String stockLimitInput;
        private String stockResetInput;
        private String moneyStockLimitInput;
        private String editorInput;
        private String costQtyInput;
        private String outputQtyInput;
    }

    private enum Tab {
        TRADES,
        SETTINGS,
        PREVIEW
    }
}

