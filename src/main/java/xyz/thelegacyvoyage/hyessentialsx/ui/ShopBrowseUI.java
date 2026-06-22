package xyz.thelegacyvoyage.hyessentialsx.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopAdminDraftCache;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopManager;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopItemModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopTradeModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.InventoryUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.ShopTradeQuantityUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("removal")
public final class ShopBrowseUI extends InteractiveCustomUIPage<ShopBrowseUI.UIEventData> {

    private static final String LAYOUT = "hyessentialsx/ShopBrowse.ui";
    private static final String ROW_LAYOUT = "hyessentialsx/ShopBrowseTradeItem.ui";
    private static final String ICON_LAYOUT = "hyessentialsx/ShopTradeItemIcon.ui";
    private static final String ICON_SMALL_LAYOUT = "hyessentialsx/ShopTradeItemIconSmall.ui";
    private static final int TRADES_PER_PAGE = 5;
    private static final String ADMIN_PERMISSION = "hyessentialsx.adminshop.admin";
    private static final String LEGACY_ADMIN_PERMISSION = "hyessentialsx.shop.admin";

    private final PlayerRef playerRef;
    private final ShopManager shopManager;
    private final EconomyManager economy;
    private final ShopAdminDraftCache draftCache;
    private ShopModel shop;
    private int page;
    private String search = "";
    private String filter = "all";

    public ShopBrowseUI(@Nonnull PlayerRef playerRef,
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
        if (data.action == null) return;
        switch (data.action) {
            case "prev" -> {
                if (page > 0) {
                    page--;
                    refresh(ref, store);
                }
            }
            case "next" -> {
                int totalPages = getTotalPages(filteredTradeViews().size());
                if (page < totalPages - 1) {
                    page++;
                    refresh(ref, store);
                }
            }
            case "search" -> {
                search = safe(data.search);
                page = 0;
                refresh(ref, store);
            }
            case "clear-search" -> {
                search = "";
                page = 0;
                refresh(ref, store);
            }
            case "filter" -> {
                filter = switch (safe(data.filter).toLowerCase(Locale.ROOT)) {
                    case "buy" -> "buy";
                    case "sell" -> "sell";
                    default -> "all";
                };
                page = 0;
                refresh(ref, store);
            }
            case "buy" -> {
                if (data.tradeIndex != null) {
                    int idx = parseIndex(data.tradeIndex);
                    if (idx >= 0) {
                        int quantity = parseQuantity(data.quantity);
                        if (quantity < 0) {
                            sendInvalidQuantity();
                            return;
                        }
                        executeTrade(ref, store, idx, quantity);
                        refresh(ref, store);
                    }
                }
            }
            case "edit" -> openEditor(ref, store);
            default -> {
            }
        }
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
        cmd.set("#ShopTitle.Text", shop.getDisplayName());
        cmd.set("#WalletBalance.Text", economy.isEnabled() ? economy.formatAmount(economy.getBalance(playerRef.getUuid())) : "Disabled");
        cmd.set("#ShopSearchInput.Value", search);
        cmd.set("#FilterAllButton.Disabled", filter.equals("all"));
        cmd.set("#FilterBuyButton.Disabled", filter.equals("buy"));
        cmd.set("#FilterSellButton.Disabled", filter.equals("sell"));
        updateFundsLabel(cmd);
        buildTradeList(ref, store, cmd, evt);

        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ShopSearchInput",
                EventData.of("Action", "search").append("@Search", "#ShopSearchInput.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ClearShopSearchButton",
                EventData.of("Action", "clear-search"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#FilterAllButton",
                EventData.of("Action", "filter").append("Filter", "all"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#FilterBuyButton",
                EventData.of("Action", "filter").append("Filter", "buy"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#FilterSellButton",
                EventData.of("Action", "filter").append("Filter", "sell"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PrevPage",
                EventData.of("Action", "prev"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NextPage",
                EventData.of("Action", "next"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#EditShopButton",
                EventData.of("Action", "edit"), false);

        cmd.set("#EditShopButton.Visible", canEdit(store, ref));
    }

    private void openEditor(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (!canEdit(store, ref)) {
            Messages.sendPrefixedKey(playerRef, "shop.use.no_permission", java.util.Map.of());
            return;
        }
        ShopAdminDraftCache.Draft draft = new ShopAdminDraftCache.Draft();
        draft.shopName = shop.getName();
        draft.tab = "TRADES";
        draftCache.save(playerRef.getUuid(), draft);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.sendPrefixedKey(playerRef, "shop.admin.ui_failed", java.util.Map.of());
            return;
        }
        ShopAdminUI ui = new ShopAdminUI(playerRef, shopManager, economy, shop, draftCache);
        ui.open(player, ref, store);
    }

    private boolean canEdit(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        if (hasPermission(store, ref, ADMIN_PERMISSION)
                || hasPermission(store, ref, LEGACY_ADMIN_PERMISSION)) {
            return true;
        }
        String editPermission = shop.getEditPermission();
        if (editPermission.isBlank()) return false;
        if (hasPermission(store, ref, editPermission)) return true;
        if (editPermission.equalsIgnoreCase(ShopManager.DEFAULT_EDIT_PERMISSION)
                && hasPermission(store, ref, ShopManager.LEGACY_EDIT_PERMISSION)) {
            return true;
        }
        return false;
    }

    private boolean hasPermission(@Nonnull Store<EntityStore> store,
                                  @Nonnull Ref<EntityStore> ref,
                                  @Nonnull String permission) {
        Boolean componentHas = null;
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                componentHas = CommandPermissionUtil.hasPermission(player, permission);
            }
        } catch (Exception ignored) {
        }
        boolean moduleHas = CommandPermissionUtil.hasPermission(playerRef, permission);
        if (!CommandPermissionUtil.isPermissionsSystemEnabled()) {
            return moduleHas;
        }
        if (com.hypixel.hytale.server.core.permissions.PermissionsModule.get().getFirstPermissionProvider() == null) {
            return componentHas != null ? componentHas : moduleHas;
        }
        if (componentHas == null) {
            return moduleHas;
        }
        return moduleHas || componentHas;
    }

    private void buildTradeList(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull UICommandBuilder cmd,
                                @Nonnull UIEventBuilder evt) {
        List<TradeView> trades = filteredTradeViews();
        int totalPages = getTotalPages(trades.size());
        if (page >= totalPages) page = totalPages - 1;
        int start = page * TRADES_PER_PAGE;
        int end = Math.min(trades.size(), start + TRADES_PER_PAGE);

        cmd.clear("#TradeCards");
        cmd.set("#NoTradesLabel.Visible", trades.isEmpty());
        cmd.set("#PageInfo.Text", "Page " + (page + 1) + "/" + totalPages);
        cmd.set("#PrevPage.Disabled", page <= 0);
        cmd.set("#NextPage.Disabled", page >= totalPages - 1);

        Player player = store.getComponent(ref, Player.getComponentType());
        Inventory inventory = player != null ? player.getInventory() : null;

        if (shouldResetStock(shop)) {
            resetStock(shop);
        }

        for (int idx = start; idx < end; idx++) {
            TradeView view = trades.get(idx);
            ShopTradeModel trade = view.trade();
            String cardSelector = "#TradeCards[" + (idx - start) + "]";
            cmd.append("#TradeCards", ROW_LAYOUT);

            buildCostSection(cmd, cardSelector, trade, inventory, 1);
            buildRewardSection(cmd, cardSelector, trade, 1);

            boolean moneyStockOk = hasMoneyStock(trade, 1);
            boolean canTrade = shop.isOpen() && trade.isEnabled() && canAfford(trade, inventory, 1) && hasStock(trade, 1) && moneyStockOk;
            String statusText;
            String statusColor;
            if (!shop.isOpen()) {
                statusText = "Closed";
                statusColor = "#888888";
                canTrade = false;
            } else if (!moneyStockOk) {
                statusText = "Out of funds";
                statusColor = "#888888";
                canTrade = false;
            } else if (!hasStock(trade, 1)) {
                statusText = "Out of stock";
                statusColor = "#888888";
                canTrade = false;
            } else if (trade.isMoneyTrade() && !economy.isEnabled()) {
                statusText = "Economy disabled";
                statusColor = "#888888";
                canTrade = false;
            } else if (!canAfford(trade, inventory, 1)) {
                statusText = (trade.isMoneyTrade() && !trade.isSellTrade()) ? "Need funds" : "Need items";
                statusColor = "#ffaa66";
            } else {
                statusText = "Ready";
                statusColor = "#66ff66";
            }

            cmd.set(cardSelector + " #StatusLabel.Text", statusText);
            cmd.set(cardSelector + " #StatusLabel.Style.TextColor", statusColor);
            cmd.set(cardSelector + " #TradeButton.Disabled", !canTrade);
            cmd.set(cardSelector + " #TradeOneButton.Disabled", !canTrade);
            cmd.set(cardSelector + " #TradeTenButton.Disabled", !canTrade);
            cmd.set(cardSelector + " #TradeHundredButton.Disabled", !canTrade);

            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    cardSelector + " #TradeButton",
                    new EventData()
                            .append("Action", "buy")
                            .append("Trade", String.valueOf(view.index()))
                            .append("Quantity", "1"),
                    false
            );
            bindQuickTrade(evt, cardSelector, view.index(), "TradeOneButton", 1);
            bindQuickTrade(evt, cardSelector, view.index(), "TradeTenButton", 10);
            bindQuickTrade(evt, cardSelector, view.index(), "TradeHundredButton", 100);
        }
    }

    @Nonnull
    private List<TradeView> filteredTradeViews() {
        String needle = search.trim().toLowerCase(Locale.ROOT);
        List<TradeView> out = new ArrayList<>();
        List<ShopTradeModel> trades = shop.getTrades();
        for (int i = 0; i < trades.size(); i++) {
            ShopTradeModel trade = trades.get(i);
            if (!matchesFilter(trade)) continue;
            if (!needle.isBlank() && !tradeSearchText(trade).contains(needle)) continue;
            out.add(new TradeView(i, trade));
        }
        return out;
    }

    private boolean matchesFilter(@Nonnull ShopTradeModel trade) {
        if (filter.equals("buy")) return !trade.isSellTrade();
        if (filter.equals("sell")) return trade.isSellTrade();
        return true;
    }

    @Nonnull
    private String tradeSearchText(@Nonnull ShopTradeModel trade) {
        StringBuilder sb = new StringBuilder();
        appendItems(sb, trade.getCostItems());
        appendItems(sb, trade.getRewardItems());
        sb.append(' ').append(trade.isSellTrade() ? "sell" : "buy");
        sb.append(' ').append(trade.isMoneyTrade() ? "money" : "items");
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private void appendItems(@Nonnull StringBuilder sb, @Nonnull List<ShopItemModel> items) {
        for (ShopItemModel item : items) {
            if (item != null && item.getItemId() != null) {
                sb.append(' ').append(item.getItemId());
            }
        }
    }

    private void buildCostSection(@Nonnull UICommandBuilder cmd,
                                  @Nonnull String cardSelector,
                                  @Nonnull ShopTradeModel trade,
                                  Inventory inventory,
                                  int quantity) {
        ShopTradeModel scaled = ShopTradeQuantityUtil.scaleTrade(trade, quantity);
        cmd.clear(cardSelector + " #PayItems");
        if (scaled.isMoneyTrade() && !scaled.isSellTrade()) {
            String text = "Price: " + economy.formatAmount(scaled.getMoneyCost());
            cmd.appendInline(cardSelector + " #PayItems",
                    "Label { Anchor: (Height: 24); Style: (FontSize: 14, TextColor: #ffffff); Text: \"" + text + "\"; }");
            return;
        }

        List<ShopItemModel> items = scaled.getCostItems();
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
            cmd.append(rowSelector, ICON_LAYOUT);
            cmd.set(itemSelector + " #ItemIcon.ItemId", item.getItemId());
            cmd.set(itemSelector + " #Quantity.Text", "x" + item.getQuantity());
            int have = inventory == null ? 0 : InventoryUtil.countItem(inventory, item.getItemId());
            String infoText = "Have: " + have;
            String color = have >= item.getQuantity() ? "#88ff88" : "#ff6666";
            cmd.set(itemSelector + " #InfoLabel.Text", infoText);
            cmd.set(itemSelector + " #InfoLabel.Style.TextColor", color);
        }
    }

    private void buildRewardSection(@Nonnull UICommandBuilder cmd,
                                    @Nonnull String cardSelector,
                                    @Nonnull ShopTradeModel trade,
                                    int quantity) {
        ShopTradeModel scaled = ShopTradeQuantityUtil.scaleTrade(trade, quantity);
        cmd.clear(cardSelector + " #GetItems");
        if (scaled.isMoneyTrade() && scaled.isSellTrade()) {
            String text = "You receive: " + economy.formatAmount(scaled.getMoneyCost());
            cmd.appendInline(cardSelector + " #GetItems",
                    "Label { Anchor: (Height: 24); Style: (FontSize: 14, TextColor: #ffffff); Text: \"" + text + "\"; }");
            return;
        }
        List<ShopItemModel> rewards = scaled.getRewardItems();
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
            cmd.append(rowSelector, ICON_LAYOUT);
            cmd.set(itemSelector + " #ItemIcon.ItemId", item.getItemId());
            cmd.set(itemSelector + " #Quantity.Text", "x" + item.getQuantity());
            if (trade.hasStockLimit()) {
                cmd.set(itemSelector + " #InfoLabel.Text",
                        "Stock: " + trade.getStockCurrent() + "/" + trade.getStockLimit());
                cmd.set(itemSelector + " #InfoLabel.Style.TextColor", "#66ffff");
            } else {
                cmd.set(itemSelector + " #InfoLabel.Text", "Stock: Unlimited");
                cmd.set(itemSelector + " #InfoLabel.Style.TextColor", "#66ffff");
            }
        }
    }

    private void bindQuickTrade(@Nonnull UIEventBuilder evt,
                                @Nonnull String cardSelector,
                                int tradeIndex,
                                @Nonnull String buttonId,
                                int quantity) {
        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                cardSelector + " #" + buttonId,
                new EventData()
                        .append("Action", "buy")
                        .append("Trade", String.valueOf(tradeIndex))
                        .append("Quantity", String.valueOf(Math.min(quantity, getMaxTradeQuantity()))),
                false
        );
    }

    private boolean canAfford(@Nonnull ShopTradeModel trade, Inventory inventory, int quantity) {
        if (!trade.isEnabled()) return false;
        ShopTradeModel scaled = ShopTradeQuantityUtil.scaleTrade(trade, quantity);
        if (trade.isMoneyTrade() && !trade.isSellTrade()) {
            if (!economy.isEnabled()) return false;
            return economy.getBalance(playerRef.getUuid()) >= scaled.getMoneyCost();
        }
        if (inventory == null) return false;
        return InventoryUtil.hasItems(inventory, scaled.getCostItems());
    }

    private void executeTrade(@Nonnull Ref<EntityStore> ref,
                              @Nonnull Store<EntityStore> store,
                              int tradeIndex,
                              int quantity) {
        if (tradeIndex < 0 || tradeIndex >= shop.getTrades().size()) return;
        ShopTradeModel trade = shop.getTrades().get(tradeIndex);
        ShopTradeModel scaledTrade = ShopTradeQuantityUtil.scaleTrade(trade, quantity);
        if (shouldResetStock(shop)) {
            resetStock(shop);
        }
        if (!hasStock(trade, quantity)) {
            Messages.sendPrefixedKey(playerRef, "shop.trade.out_of_stock", java.util.Map.of());
            return;
        }
        if (!shop.isOpen()) {
            Messages.sendPrefixedKey(playerRef, "shop.trade.closed", java.util.Map.of());
            return;
        }
        if (!trade.isEnabled()) {
            Messages.sendPrefixedKey(playerRef, "shop.trade.disabled", java.util.Map.of());
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.sendPrefixedKey(playerRef, "shop.trade.inventory_failed", java.util.Map.of());
            return;
        }
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            Messages.sendPrefixedKey(playerRef, "shop.trade.inventory_failed", java.util.Map.of());
            return;
        }

        if (trade.isMoneyTrade() && trade.isSellTrade()) {
            if (!economy.isEnabled()) {
                Messages.sendPrefixedKey(playerRef, "shop.trade.economy_disabled", java.util.Map.of());
                return;
            }
            if (!hasMoneyStock(trade, quantity)) {
                Messages.sendPrefixedKey(playerRef, "shop.trade.out_of_funds", java.util.Map.of());
                return;
            }
            if (!InventoryUtil.hasItems(inventory, scaledTrade.getCostItems())) {
                Messages.sendPrefixedKey(playerRef, "shop.trade.missing_items", java.util.Map.of());
                return;
            }
            if (!InventoryUtil.removeItems(inventory, scaledTrade.getCostItems())) {
                Messages.sendPrefixedKey(playerRef, "shop.trade.remove_failed", java.util.Map.of());
                return;
            }
            economy.deposit(playerRef.getUuid(), scaledTrade.getMoneyCost());
            applyMoneyStockChange(trade, false, quantity);
            applyStockChange(trade, true, quantity);
            restockMatchingTrades(trade, quantity);
            shopManager.saveShop(shop);
            Messages.sendPrefixed(playerRef, formatTradeMessage(scaledTrade));
            return;
        }

        if (trade.isMoneyTrade()) {
            if (!economy.isEnabled()) {
                Messages.sendPrefixedKey(playerRef, "shop.trade.economy_disabled", java.util.Map.of());
                return;
            }
            long cost = scaledTrade.getMoneyCost();
            if (economy.getBalance(playerRef.getUuid()) < cost) {
                Messages.sendPrefixedKey(playerRef, "shop.trade.insufficient_funds", java.util.Map.of());
                return;
            }
            if (!economy.withdraw(playerRef.getUuid(), cost)) {
                Messages.sendPrefixedKey(playerRef, "shop.trade.payment_failed", java.util.Map.of());
                return;
            }
            applyMoneyStockChange(trade, true, quantity);
            List<com.hypixel.hytale.server.core.inventory.ItemStack> overflow =
                    InventoryUtil.addItemsWithOverflow(inventory, scaledTrade.getRewardItems());
            if (!overflow.isEmpty()) {
                dropOverflow(player, overflow);
                Messages.sendPrefixedKey(playerRef, "shop.trade.inventory_full", java.util.Map.of());
            }
            applyStockChange(trade, false, quantity);
            shopManager.saveShop(shop);
            Messages.sendPrefixed(playerRef, formatTradeMessage(scaledTrade));
            return;
        }

        if (!InventoryUtil.hasItems(inventory, scaledTrade.getCostItems())) {
            Messages.sendPrefixedKey(playerRef, "shop.trade.missing_items", java.util.Map.of());
            return;
        }
        if (!InventoryUtil.removeItems(inventory, scaledTrade.getCostItems())) {
            Messages.sendPrefixedKey(playerRef, "shop.trade.remove_failed", java.util.Map.of());
            return;
        }

        List<com.hypixel.hytale.server.core.inventory.ItemStack> overflow =
                InventoryUtil.addItemsWithOverflow(inventory, scaledTrade.getRewardItems());
        if (!overflow.isEmpty()) {
            dropOverflow(player, overflow);
            Messages.sendPrefixedKey(playerRef, "shop.trade.inventory_full", java.util.Map.of());
        }
        applyStockChange(trade, false, quantity);
        shopManager.saveShop(shop);
        Messages.sendPrefixed(playerRef, formatTradeMessage(scaledTrade));
    }

    private void dropOverflow(@Nonnull Player player,
                              @Nonnull List<com.hypixel.hytale.server.core.inventory.ItemStack> overflow) {
        for (com.hypixel.hytale.server.core.inventory.ItemStack stack : overflow) {
            if (stack == null || stack.isEmpty()) continue;
            if (!tryDropItem(player, stack)) {
                return;
            }
        }
    }

    private boolean tryDropItem(@Nonnull Player player,
                                @Nonnull com.hypixel.hytale.server.core.inventory.ItemStack stack) {
        String[] methods = {"dropItemStack", "dropItem", "dropItemAt", "spawnItemStack"};
        for (String name : methods) {
            for (var method : player.getClass().getMethods()) {
                if (!method.getName().equals(name)) continue;
                if (method.getParameterCount() != 1) continue;
                if (!method.getParameterTypes()[0].isAssignableFrom(stack.getClass())) continue;
                try {
                    method.invoke(player, stack);
                    return true;
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    private int parseIndex(@Nonnull String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private int parseQuantity(String raw) {
        return ShopTradeQuantityUtil.parseQuantity(raw, getMaxTradeQuantity());
    }

    private int getMaxTradeQuantity() {
        return ShopTradeQuantityUtil.normalizeMaxQuantity(shopManager.getAdminShopMaxTradeQuantity());
    }

    private void sendInvalidQuantity() {
        Messages.sendPrefixedKey(playerRef, "shop.trade.invalid_quantity",
                java.util.Map.of("max", String.valueOf(getMaxTradeQuantity())));
    }

    private String formatTradeMessage(@Nonnull ShopTradeModel trade) {
        String paid;
        String received;
        if (trade.isMoneyTrade()) {
            if (trade.isSellTrade()) {
                paid = formatItems(trade.getCostItems());
                received = economy.formatAmount(trade.getMoneyCost());
            } else {
                paid = economy.formatAmount(trade.getMoneyCost());
                received = formatItems(trade.getRewardItems());
            }
        } else {
            paid = formatItems(trade.getCostItems());
            received = formatItems(trade.getRewardItems());
        }
        if (paid.isBlank()) paid = "nothing";
        if (received.isBlank()) received = "nothing";
        return Messages.tr(playerRef, "shop.trade.completed",
                java.util.Map.of("paid", paid, "received", received));
    }

    private String formatItems(@Nonnull List<ShopItemModel> items) {
        if (items.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        for (ShopItemModel item : items) {
            if (item == null) continue;
            String id = item.getItemId();
            int qty = item.getQuantity();
            if (id == null || id.isBlank() || qty <= 0) continue;
            parts.add("x" + qty + " " + id);
        }
        return String.join(", ", parts);
    }

    private int getTotalPages(int trades) {
        return Math.max(1, (int) Math.ceil(trades / (double) TRADES_PER_PAGE));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private void updateFundsLabel(@Nonnull UICommandBuilder cmd) {
        long limit = shop.getMoneyStockLimit();
        if (limit <= 0) {
            cmd.set("#ShopFunds.Visible", false);
            return;
        }
        long current = shop.getMoneyStockCurrent();
        String text = "Funds: " + economy.formatAmount(current) + " / " + economy.formatAmount(limit);
        cmd.set("#ShopFunds.Text", text);
        cmd.set("#ShopFunds.Visible", true);
    }

    private boolean hasMoneyStock(@Nonnull ShopTradeModel trade, int quantity) {
        if (!trade.isMoneyTrade() || !trade.isSellTrade()) return true;
        long limit = shop.getMoneyStockLimit();
        if (limit <= 0) return true;
        return shop.getMoneyStockCurrent() >= ShopTradeQuantityUtil.multiplyMoney(trade.getMoneyCost(), quantity);
    }

    private void applyMoneyStockChange(@Nonnull ShopTradeModel trade, boolean playerPaidMoney, int quantity) {
        if (!trade.isMoneyTrade()) return;
        long limit = shop.getMoneyStockLimit();
        if (limit <= 0) return;
        long current = shop.getMoneyStockCurrent();
        long delta = ShopTradeQuantityUtil.multiplyMoney(trade.getMoneyCost(), quantity);
        if (playerPaidMoney) {
            shop.setMoneyStockCurrent(Math.min(limit, current + delta));
        } else {
            shop.setMoneyStockCurrent(Math.max(0L, current - delta));
        }
    }

    private boolean hasStock(@Nonnull ShopTradeModel trade, int quantity) {
        if (trade.isMoneyTrade() && trade.isSellTrade()) {
            return true;
        }
        if (!trade.hasStockLimit()) return true;
        int current = trade.getStockCurrent();
        long required = (long) sumQuantities(trade.getRewardItems()) * Math.max(1, quantity);
        return required <= 0L || current >= required;
    }

    private void applyStockChange(@Nonnull ShopTradeModel trade, boolean isSell, int quantity) {
        if (trade.isMoneyTrade() && trade.isSellTrade()) {
            return;
        }
        if (!trade.hasStockLimit()) return;
        int current = trade.getStockCurrent();
        int limit = trade.getStockLimit();
        if (isSell) {
            int add = multiplyQuantity(sumQuantities(trade.getCostItems()), quantity);
            trade.setStockCurrent(Math.min(limit, current + add));
        } else {
            int remove = multiplyQuantity(sumQuantities(trade.getRewardItems()), quantity);
            trade.setStockCurrent(Math.max(0, current - remove));
        }
    }

    private void restockMatchingTrades(@Nonnull ShopTradeModel sellTrade, int quantity) {
        if (sellTrade.getCostItems().isEmpty()) return;
        for (ShopTradeModel trade : shop.getTrades()) {
            if (trade == sellTrade) continue;
            if (!trade.hasStockLimit()) continue;
            if (trade.isSellTrade()) continue;
            int add = multiplyQuantity(matchingQuantity(trade.getRewardItems(), sellTrade.getCostItems()), quantity);
            if (add <= 0) continue;
            int updated = Math.min(trade.getStockLimit(), trade.getStockCurrent() + add);
            trade.setStockCurrent(updated);
        }
    }

    private int matchingQuantity(@Nonnull List<ShopItemModel> rewards, @Nonnull List<ShopItemModel> sold) {
        int total = 0;
        for (ShopItemModel reward : rewards) {
            if (reward == null) continue;
            String id = reward.getItemId();
            if (id == null || id.isBlank()) continue;
            for (ShopItemModel cost : sold) {
                if (cost == null) continue;
                if (!id.equals(cost.getItemId())) continue;
                total += cost.getQuantity();
            }
        }
        return total;
    }

    private int sumQuantities(@Nonnull List<ShopItemModel> items) {
        int total = 0;
        for (ShopItemModel item : items) {
            if (item == null) continue;
            int qty = item.getQuantity();
            if (qty > 0) total += qty;
        }
        return total;
    }

    private int multiplyQuantity(int amount, int quantity) {
        long total = (long) Math.max(0, amount) * Math.max(1, quantity);
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private boolean shouldResetStock(@Nonnull ShopModel shop) {
        int days = shop.getStockResetDays();
        long next = shop.getStockResetAt();
        return days > 0 && next > 0L && System.currentTimeMillis() >= next;
    }

    private void resetStock(@Nonnull ShopModel shop) {
        for (ShopTradeModel trade : shop.getTrades()) {
            if (!trade.hasStockLimit()) continue;
            trade.setStockCurrent(trade.isSellTrade() ? 0 : trade.getStockLimit());
        }
        if (shop.getMoneyStockLimit() > 0) {
            shop.setMoneyStockCurrent(shop.getMoneyStockLimit());
        }
        long millis = shop.getStockResetDays() * 24L * 60L * 60L * 1000L;
        shop.setStockResetAt(System.currentTimeMillis() + millis);
        shopManager.saveShop(shop);
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
                .append(new KeyedCodec<>("Action", Codec.STRING), (e, v) -> e.action = v, e -> e.action).add()
                .append(new KeyedCodec<>("Trade", Codec.STRING), (e, v) -> e.tradeIndex = v, e -> e.tradeIndex).add()
                .append(new KeyedCodec<>("Quantity", Codec.STRING), (e, v) -> e.quantity = v, e -> e.quantity).add()
                .append(new KeyedCodec<>("@Search", Codec.STRING), (e, v) -> e.search = v, e -> e.search).add()
                .append(new KeyedCodec<>("Filter", Codec.STRING), (e, v) -> e.filter = v, e -> e.filter).add()
                .build();

        private String action;
        private String tradeIndex;
        private String quantity;
        private String search;
        private String filter;
    }

    private record TradeView(int index, ShopTradeModel trade) {
    }
}

