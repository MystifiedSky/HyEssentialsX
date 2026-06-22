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
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopAdminDraftCache;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopNpcInteractionRegistry;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopItemModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopTradeModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopNpcModel;
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
    private int editingIndex = -1;

    public ShopAdminUI(@Nonnull PlayerRef playerRef,
                       @Nonnull ShopManager shopManager,
                       @Nonnull EconomyManager economy,
                       @Nonnull ShopModel shop,
                       @Nonnull ShopAdminDraftCache draftCache) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.playerRef = playerRef;
        this.shopManager = shopManager;
        this.economy = economy;
        this.shop = shop;
        this.draftCache = draftCache;
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
            default -> {
            }
        }
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
        cmd.set("#StockLimitSection.Visible", !(useMoney && sellTrade));
        cmd.set("#NpcRoleLabel.Text", npcRoleSelected.isBlank() ? "None" : npcRoleSelected);
        cmd.set("#NpcRolePrev.Disabled", npcRoles.size() <= 1);
        cmd.set("#NpcRoleNext.Disabled", npcRoles.size() <= 1);

        cmd.set("#PriceInput #SearchInput.Value", pendingPriceText == null ? "" : pendingPriceText);
        cmd.set("#ShopNameInput #SearchInput.Value", pendingNameText == null ? "" : pendingNameText);
        cmd.set("#StockLimitInput #SearchInput.Value", pendingStockLimitText == null ? "" : pendingStockLimitText);
        cmd.set("#StockResetInput #SearchInput.Value", pendingStockResetDaysText == null ? "" : pendingStockResetDaysText);
        cmd.set("#MoneyStockLimitInput #SearchInput.Value", pendingMoneyStockLimitText == null ? "" : pendingMoneyStockLimitText);

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
            cmd.append("#SelectedCostItems", SELECTED_ITEM_LAYOUT);
            String selector = "#SelectedCostItems[0]";
            cmd.set(selector + " #ItemIcon.ItemId", pendingCostItem.getItemId());
            cmd.set(selector + " #Quantity.Text", "x" + pendingCostItem.getQuantity());
            cmd.set(selector + " #EditQty.Visible", false);
        }

        if (pendingOutputItem != null) {
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
            Messages.sendPrefixed(playerRef, "&cCould not access inventory.");
            return;
        }
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            Messages.sendPrefixed(playerRef, "&cCould not access inventory.");
            return;
        }
        ItemStack hand = inventory.getItemInHand();
        if (hand == null || hand.isEmpty()) {
            Messages.sendPrefixed(playerRef, "&cHold an item in your hand to set the cost.");
            return;
        }
        pendingCostItem = new ShopItemModel(hand.getItemId(), Math.max(1, hand.getQuantity()));
        if (!useMoney) {
            useMoney = false;
        }
    }

    private void setOutputFromHand(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.sendPrefixed(playerRef, "&cCould not access inventory.");
            return;
        }
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            Messages.sendPrefixed(playerRef, "&cCould not access inventory.");
            return;
        }
        ItemStack hand = inventory.getItemInHand();
        if (hand == null || hand.isEmpty()) {
            Messages.sendPrefixed(playerRef, "&cHold an item in your hand to set the output.");
            return;
        }
        pendingOutputItem = new ShopItemModel(hand.getItemId(), Math.max(1, hand.getQuantity()));
    }

    private void saveTrade() {
        if (!useMoney || !sellTrade) {
            if (pendingOutputItem == null || pendingOutputItem.getItemId().isBlank()) {
                Messages.sendPrefixed(playerRef, "&cSelect an output item first.");
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
                Messages.sendPrefixed(playerRef, "&cEnter a valid price.");
                return;
            }
            if (!economy.isEnabled()) {
                Messages.sendPrefixed(playerRef, "&cEconomy is disabled.");
                return;
            }
            trade.setMoneyCost(price);
            if (sellTrade) {
                if (pendingCostItem == null || pendingCostItem.getItemId().isBlank()) {
                    Messages.sendPrefixed(playerRef, "&cSelect a cost item first.");
                    return;
                }
                trade.setCostItems(List.of(pendingCostItem));
            } else {
                trade.setCostItems(List.of());
            }
            trade.setSellTrade(sellTrade);
        } else {
            if (pendingCostItem == null || pendingCostItem.getItemId().isBlank()) {
                Messages.sendPrefixed(playerRef, "&cSelect a cost item first.");
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
        shopManager.saveShop(shop);
        clearPending();
        Messages.sendPrefixed(playerRef, "&aTrade saved.");
    }

    private void saveStockSettings() {
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
        Messages.sendPrefixed(playerRef, "&aStock settings saved.");
    }

    private void saveName() {
        String name = pendingNameText == null ? "" : pendingNameText.trim();
        if (name.isBlank()) {
            Messages.sendPrefixed(playerRef, "&cEnter a valid shop name.");
            return;
        }
        shop.setDisplayName(name);
        shopManager.saveShop(shop);
        updateNpcNameplates();
        Messages.sendPrefixed(playerRef, "&aShop name updated.");
    }

    private void toggleOpen() {
        shop.setOpen(!shop.isOpen());
        shopManager.saveShop(shop);
        Messages.sendPrefixed(playerRef, shop.isOpen() ? "&aShop opened." : "&cShop closed.");
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
            applyNameplate(store, ref, displayName);
        }
    }

    private void applyNameplate(@Nonnull Store<EntityStore> store,
                                @Nonnull Ref<EntityStore> ref) {
        applyNameplate(store, ref, shop.getDisplayName());
    }

    private void applyNameplate(@Nonnull Store<EntityStore> store,
                                @Nonnull Ref<EntityStore> ref,
                                @Nonnull String displayName) {
        Nameplate nameplate = store.getComponent(ref, Nameplate.getComponentType());
        if (nameplate == null) {
            store.addComponent(ref, Nameplate.getComponentType(), new Nameplate(displayName));
        } else {
            nameplate.setText(displayName);
        }
    }

    private void deleteTrade(String rawIndex) {
        int idx = parseIndex(rawIndex);
        if (idx < 0 || idx >= shop.getTrades().size()) return;
        shop.getTrades().remove(idx);
        if (editingIndex == idx) {
            clearPending();
        }
        shopManager.saveShop(shop);
        Messages.sendPrefixed(playerRef, "&cTrade removed.");
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
    }

    private void clearPending() {
        pendingCostItem = null;
        pendingOutputItem = null;
        pendingPriceText = "";
        pendingStockLimitText = "";
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
            Messages.sendPrefixed(playerRef, "&cNo NPC role selected.");
            return;
        }
        NPCPlugin plugin = NPCPlugin.get();
        if (plugin == null) {
            Messages.sendPrefixed(playerRef, "&cNPC system not available.");
            return;
        }
        int roleIndex = plugin.getIndex(npcRoleSelected);
        if (roleIndex < 0) {
            Messages.sendPrefixed(playerRef, "&cNPC role not found.");
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
        Messages.sendPrefixed(playerRef, "&aUpdated NPC role to &f" + npcRoleSelected + "&a.");
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
            npcModel.setNpcId(newNpc.getUuid().toString());
        }
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
                .build();

        private String action;
        private String tradeIndex;
        private String priceInput;
        private String shopNameInput;
        private String stockLimitInput;
        private String stockResetInput;
        private String moneyStockLimitInput;
    }

    private enum Tab {
        TRADES,
        SETTINGS,
        PREVIEW
    }
}
