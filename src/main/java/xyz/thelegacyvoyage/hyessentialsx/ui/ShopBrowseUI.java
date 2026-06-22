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
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopManager;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopItemModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopTradeModel;
import xyz.thelegacyvoyage.hyessentialsx.util.InventoryUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public final class ShopBrowseUI extends InteractiveCustomUIPage<ShopBrowseUI.UIEventData> {

    private static final String LAYOUT = "hyessentialsx/ShopBrowse.ui";
    private static final String ROW_LAYOUT = "hyessentialsx/ShopBrowseTradeItem.ui";
    private static final String ICON_LAYOUT = "hyessentialsx/ShopTradeItemIcon.ui";
    private static final String ICON_SMALL_LAYOUT = "hyessentialsx/ShopTradeItemIconSmall.ui";
    private static final int TRADES_PER_PAGE = 5;

    private final PlayerRef playerRef;
    private final ShopManager shopManager;
    private final EconomyManager economy;
    private ShopModel shop;
    private int page;

    public ShopBrowseUI(@Nonnull PlayerRef playerRef,
                        @Nonnull ShopManager shopManager,
                        @Nonnull EconomyManager economy,
                        @Nonnull ShopModel shop) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.playerRef = playerRef;
        this.shopManager = shopManager;
        this.economy = economy;
        this.shop = shop;
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
                int totalPages = getTotalPages();
                if (page < totalPages - 1) {
                    page++;
                    refresh(ref, store);
                }
            }
            case "buy" -> {
                if (data.tradeIndex != null) {
                    int idx = parseIndex(data.tradeIndex);
                    if (idx >= 0) {
                        executeTrade(ref, store, idx);
                        refresh(ref, store);
                    }
                }
            }
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
        updateFundsLabel(cmd);
        buildTradeList(ref, store, cmd, evt);

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PrevPage",
                EventData.of("Action", "prev"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NextPage",
                EventData.of("Action", "next"), false);
    }

    private void buildTradeList(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull UICommandBuilder cmd,
                                @Nonnull UIEventBuilder evt) {
        List<ShopTradeModel> trades = new ArrayList<>(shop.getTrades());
        int totalPages = Math.max(1, (int) Math.ceil(trades.size() / (double) TRADES_PER_PAGE));
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
            ShopTradeModel trade = trades.get(idx);
            String cardSelector = "#TradeCards[" + (idx - start) + "]";
            cmd.append("#TradeCards", ROW_LAYOUT);

            buildCostSection(cmd, cardSelector, trade, inventory);
            buildRewardSection(cmd, cardSelector, trade);

            boolean moneyStockOk = hasMoneyStock(trade);
            boolean canTrade = shop.isOpen() && trade.isEnabled() && canAfford(trade, inventory) && hasStock(trade) && moneyStockOk;
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
            } else if (!hasStock(trade)) {
                statusText = "Out of stock";
                statusColor = "#888888";
                canTrade = false;
            } else if (trade.isMoneyTrade() && !economy.isEnabled()) {
                statusText = "Economy disabled";
                statusColor = "#888888";
                canTrade = false;
            } else if (!canAfford(trade, inventory)) {
                statusText = (trade.isMoneyTrade() && !trade.isSellTrade()) ? "Need funds" : "Need items";
                statusColor = "#ffaa66";
            } else {
                statusText = "Ready";
                statusColor = "#66ff66";
            }

            cmd.set(cardSelector + " #StatusLabel.Text", statusText);
            cmd.set(cardSelector + " #StatusLabel.Style.TextColor", statusColor);
            cmd.set(cardSelector + " #TradeButton.Disabled", !canTrade);

            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    cardSelector + " #TradeButton",
                    new EventData().append("Action", "buy").append("Trade", String.valueOf(idx)),
                    false
            );
        }
    }

    private void buildCostSection(@Nonnull UICommandBuilder cmd,
                                  @Nonnull String cardSelector,
                                  @Nonnull ShopTradeModel trade,
                                  Inventory inventory) {
        cmd.clear(cardSelector + " #PayItems");
        if (trade.isMoneyTrade() && !trade.isSellTrade()) {
            String text = "Price: " + economy.formatAmount(trade.getMoneyCost());
            cmd.appendInline(cardSelector + " #PayItems",
                    "Label { Anchor: (Height: 24); Style: (FontSize: 14, TextColor: #ffffff); Text: \"" + text + "\"; }");
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
                                    @Nonnull ShopTradeModel trade) {
        cmd.clear(cardSelector + " #GetItems");
        if (trade.isMoneyTrade() && trade.isSellTrade()) {
            String text = "You receive: " + economy.formatAmount(trade.getMoneyCost());
            cmd.appendInline(cardSelector + " #GetItems",
                    "Label { Anchor: (Height: 24); Style: (FontSize: 14, TextColor: #ffffff); Text: \"" + text + "\"; }");
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
            cmd.append(rowSelector, ICON_LAYOUT);
            cmd.set(itemSelector + " #ItemIcon.ItemId", item.getItemId());
            cmd.set(itemSelector + " #Quantity.Text", "x" + item.getQuantity());
            cmd.set(itemSelector + " #InfoLabel.Text", "Stock: \u221E");
            cmd.set(itemSelector + " #InfoLabel.Style.TextColor", "#66ffff");
        }
    }

    private boolean canAfford(@Nonnull ShopTradeModel trade, Inventory inventory) {
        if (!trade.isEnabled()) return false;
        if (trade.isMoneyTrade() && !trade.isSellTrade()) {
            if (!economy.isEnabled()) return false;
            return economy.getBalance(playerRef.getUuid()) >= trade.getMoneyCost();
        }
        if (inventory == null) return false;
        return InventoryUtil.hasItems(inventory, trade.getCostItems());
    }

    private void executeTrade(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, int tradeIndex) {
        if (tradeIndex < 0 || tradeIndex >= shop.getTrades().size()) return;
        ShopTradeModel trade = shop.getTrades().get(tradeIndex);
        if (shouldResetStock(shop)) {
            resetStock(shop);
        }
        if (!hasStock(trade)) {
            Messages.sendPrefixed(playerRef, "&cOut of stock.");
            return;
        }
        if (!shop.isOpen()) {
            Messages.sendPrefixed(playerRef, "&cThis shop is currently closed.");
            return;
        }
        if (!trade.isEnabled()) {
            Messages.sendPrefixed(playerRef, "&cThis trade is disabled.");
            return;
        }

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

        if (trade.isMoneyTrade() && trade.isSellTrade()) {
            if (!economy.isEnabled()) {
                Messages.sendPrefixed(playerRef, "&cEconomy is disabled.");
                return;
            }
            if (!hasMoneyStock(trade)) {
                Messages.sendPrefixed(playerRef, "&cThis shop is out of funds.");
                return;
            }
            if (!InventoryUtil.hasItems(inventory, trade.getCostItems())) {
                Messages.sendPrefixed(playerRef, "&cYou don't have the required items.");
                return;
            }
            if (!InventoryUtil.removeItems(inventory, trade.getCostItems())) {
                Messages.sendPrefixed(playerRef, "&cFailed to remove items from inventory.");
                return;
            }
            economy.deposit(playerRef.getUuid(), trade.getMoneyCost());
            applyMoneyStockChange(trade, false);
            applyStockChange(trade, true);
            restockMatchingTrades(trade);
            shopManager.saveShop(shop);
            Messages.sendPrefixed(playerRef, formatTradeMessage(trade));
            return;
        }

        if (trade.isMoneyTrade()) {
            if (!economy.isEnabled()) {
                Messages.sendPrefixed(playerRef, "&cEconomy is disabled.");
                return;
            }
            long cost = trade.getMoneyCost();
            if (economy.getBalance(playerRef.getUuid()) < cost) {
                Messages.sendPrefixed(playerRef, "&cYou do not have enough money.");
                return;
            }
            if (!economy.withdraw(playerRef.getUuid(), cost)) {
                Messages.sendPrefixed(playerRef, "&cPayment failed.");
                return;
            }
            applyMoneyStockChange(trade, true);
            List<com.hypixel.hytale.server.core.inventory.ItemStack> overflow =
                    InventoryUtil.addItemsWithOverflow(inventory, trade.getRewardItems());
            if (!overflow.isEmpty()) {
                dropOverflow(player, overflow);
                Messages.sendPrefixed(playerRef, "&eYour inventory was full. Items were dropped.");
            }
            applyStockChange(trade, false);
            shopManager.saveShop(shop);
            Messages.sendPrefixed(playerRef, formatTradeMessage(trade));
            return;
        }

        if (!InventoryUtil.hasItems(inventory, trade.getCostItems())) {
            Messages.sendPrefixed(playerRef, "&cYou don't have the required items.");
            return;
        }
        if (!InventoryUtil.removeItems(inventory, trade.getCostItems())) {
            Messages.sendPrefixed(playerRef, "&cFailed to remove items from inventory.");
            return;
        }

        List<com.hypixel.hytale.server.core.inventory.ItemStack> overflow =
                InventoryUtil.addItemsWithOverflow(inventory, trade.getRewardItems());
        if (!overflow.isEmpty()) {
            dropOverflow(player, overflow);
            Messages.sendPrefixed(playerRef, "&eYour inventory was full. Items were dropped.");
        }
        applyStockChange(trade, false);
        shopManager.saveShop(shop);
        Messages.sendPrefixed(playerRef, formatTradeMessage(trade));
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
        return "&aTrade completed, you paid &f" + paid + " &aand received &f" + received + "&a.";
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

    private int getTotalPages() {
        int trades = shop.getTrades().size();
        return Math.max(1, (int) Math.ceil(trades / (double) TRADES_PER_PAGE));
    }

    private void updateFundsLabel(@Nonnull UICommandBuilder cmd) {
        int limit = shop.getMoneyStockLimit();
        if (limit <= 0) {
            cmd.set("#ShopFunds.Visible", false);
            return;
        }
        long current = shop.getMoneyStockCurrent();
        String text = "Funds: " + economy.formatAmount(current) + " / " + economy.formatAmount(limit);
        cmd.set("#ShopFunds.Text", text);
        cmd.set("#ShopFunds.Visible", true);
    }

    private boolean hasMoneyStock(@Nonnull ShopTradeModel trade) {
        if (!trade.isMoneyTrade() || !trade.isSellTrade()) return true;
        int limit = shop.getMoneyStockLimit();
        if (limit <= 0) return true;
        return shop.getMoneyStockCurrent() >= trade.getMoneyCost();
    }

    private void applyMoneyStockChange(@Nonnull ShopTradeModel trade, boolean playerPaidMoney) {
        if (!trade.isMoneyTrade()) return;
        int limit = shop.getMoneyStockLimit();
        if (limit <= 0) return;
        long current = shop.getMoneyStockCurrent();
        long delta = trade.getMoneyCost();
        if (playerPaidMoney) {
            shop.setMoneyStockCurrent(Math.min(limit, current + delta));
        } else {
            shop.setMoneyStockCurrent(Math.max(0L, current - delta));
        }
    }

    private boolean hasStock(@Nonnull ShopTradeModel trade) {
        if (trade.isMoneyTrade() && trade.isSellTrade()) {
            return true;
        }
        if (!trade.hasStockLimit()) return true;
        int current = trade.getStockCurrent();
        int limit = trade.getStockLimit();
        return current > 0;
    }

    private void applyStockChange(@Nonnull ShopTradeModel trade, boolean isSell) {
        if (trade.isMoneyTrade() && trade.isSellTrade()) {
            return;
        }
        if (!trade.hasStockLimit()) return;
        int current = trade.getStockCurrent();
        int limit = trade.getStockLimit();
        if (isSell) {
            int add = sumQuantities(trade.getCostItems());
            trade.setStockCurrent(Math.min(limit, current + add));
        } else {
            int remove = sumQuantities(trade.getRewardItems());
            trade.setStockCurrent(Math.max(0, current - remove));
        }
    }

    private void restockMatchingTrades(@Nonnull ShopTradeModel sellTrade) {
        if (sellTrade.getCostItems().isEmpty()) return;
        for (ShopTradeModel trade : shop.getTrades()) {
            if (trade == sellTrade) continue;
            if (!trade.hasStockLimit()) continue;
            if (trade.isSellTrade()) continue;
            int add = matchingQuantity(trade.getRewardItems(), sellTrade.getCostItems());
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
                .addField(new KeyedCodec<>("Action", Codec.STRING), (e, v) -> e.action = v, e -> e.action)
                .addField(new KeyedCodec<>("Trade", Codec.STRING), (e, v) -> e.tradeIndex = v, e -> e.tradeIndex)
                .build();

        private String action;
        private String tradeIndex;
    }
}
