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
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopAdminDraftCache;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopItemModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopTradeModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.InventoryUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.ShopContainerUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayerShopBrowseUI extends InteractiveCustomUIPage<PlayerShopBrowseUI.UIEventData> {

    private static final String LAYOUT = "hyessentialsx/ShopBrowse.ui";
    private static final String ROW_LAYOUT = "hyessentialsx/ShopBrowseTradeItem.ui";
    private static final String ICON_LAYOUT = "hyessentialsx/ShopTradeItemIcon.ui";
    private static final int TRADES_PER_PAGE = 5;
    private static final String ADMIN_PERMISSION = "hyessentialsx.playershop.admin";

    private final PlayerRef playerRef;
    private final ShopManager shopManager;
    private final EconomyManager economy;
    private final ConfigManager config;
    private final StorageManager storage;
    private final ShopAdminDraftCache draftCache;
    private ShopModel shop;
    private int page;

    public PlayerShopBrowseUI(@Nonnull PlayerRef playerRef,
                              @Nonnull ShopManager shopManager,
                              @Nonnull EconomyManager economy,
                              @Nonnull ConfigManager config,
                              @Nonnull ShopModel shop,
                              @Nonnull StorageManager storage,
                              @Nonnull ShopAdminDraftCache draftCache) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.playerRef = playerRef;
        this.shopManager = shopManager;
        this.economy = economy;
        this.config = config;
        this.shop = shop;
        this.storage = storage;
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
        updateFundsLabel(cmd);
        buildTradeList(ref, store, cmd, evt);

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
        ShopAdminDraftCache.Draft draft = draftCache.get(playerRef.getUuid());
        if (draft == null || !draft.shopName.equalsIgnoreCase(shop.getName())) {
            draft = new ShopAdminDraftCache.Draft();
        }
        draft.shopName = shop.getName();
        draft.tab = "TRADES";
        draftCache.save(playerRef.getUuid(), draft);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.sendPrefixedKey(playerRef, "shop.admin.ui_failed", java.util.Map.of());
            return;
        }
        ShopAdminUI ui = new ShopAdminUI(playerRef, shopManager, economy, shop, draftCache, storage, config);
        ui.open(player, ref, store);
    }

    private boolean canEdit(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        if (hasPermission(store, ref, ADMIN_PERMISSION)) {
            return true;
        }
        if (isOwner()) {
            return true;
        }
        String uuid = playerRef.getUuid().toString();
        String username = playerRef.getUsername();
        for (String editor : shop.getEditors()) {
            if (editor == null) continue;
            if (editor.equalsIgnoreCase(uuid)) return true;
            if (username != null && editor.equalsIgnoreCase(username)) return true;
        }
        return false;
    }

    private boolean isOwner() {
        String owner = shop.getOwnerUuid();
        return !owner.isBlank() && owner.equalsIgnoreCase(playerRef.getUuid().toString());
    }

    private boolean hasPermission(@Nonnull Store<EntityStore> store,
                                  @Nonnull Ref<EntityStore> ref,
                                  @Nonnull String permission) {
        Boolean componentHas = null;
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                componentHas = player.hasPermission(permission);
            }
        } catch (Exception ignored) {
        }
        boolean moduleHas = PermissionsModule.get().hasPermission(playerRef.getUuid(), permission, false);
        if (PermissionsModule.get().getFirstPermissionProvider() == null) {
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
        List<ItemContainer> containers = resolveContainers(store);
        boolean hasStorage = !containers.isEmpty();

        for (int idx = start; idx < end; idx++) {
            ShopTradeModel trade = trades.get(idx);
            String cardSelector = "#TradeCards[" + (idx - start) + "]";
            cmd.append("#TradeCards", ROW_LAYOUT);

            buildCostSection(cmd, cardSelector, trade, inventory);
            buildRewardSection(cmd, cardSelector, trade, containers);

            boolean needsRewardItems = requiresRewardItems(trade);
            boolean needsCostStorage = requiresCostStorage(trade);
            boolean rewardStockOk = !needsRewardItems || (hasStorage && ShopContainerUtil.hasItems(containers, trade.getRewardItems()));
            boolean storageOk = !needsCostStorage || (hasStorage && ShopContainerUtil.canAddItems(containers, trade.getCostItems()));
            boolean ownerFundsOk = !isMoneySell(trade) || hasOwnerFunds(trade.getMoneyCost());

            boolean canTrade = shop.isOpen()
                    && trade.isEnabled()
                    && canAfford(trade, inventory)
                    && rewardStockOk
                    && storageOk
                    && ownerFundsOk;

            String statusText;
            String statusColor;
            if (!shop.isOpen()) {
                statusText = "Closed";
                statusColor = "#888888";
                canTrade = false;
            } else if (trade.isMoneyTrade() && !economy.isEnabled()) {
                statusText = "Economy disabled";
                statusColor = "#888888";
                canTrade = false;
            } else if ((needsRewardItems || needsCostStorage) && !hasStorage) {
                statusText = "No storage";
                statusColor = "#888888";
                canTrade = false;
            } else if (!ownerFundsOk) {
                statusText = "Out of funds";
                statusColor = "#888888";
                canTrade = false;
            } else if (!rewardStockOk) {
                statusText = "Out of stock";
                statusColor = "#888888";
                canTrade = false;
            } else if (!storageOk) {
                statusText = "Storage full";
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
                                    @Nonnull ShopTradeModel trade,
                                    @Nonnull List<ItemContainer> containers) {
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
            int stock = ShopContainerUtil.countItem(containers, item.getItemId());
            cmd.set(itemSelector + " #InfoLabel.Text", "Stock: " + stock);
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

        World world = resolveWorld(store);
        if (world == null) {
            Messages.sendPrefixedKey(playerRef, "shop.trade.world_failed", java.util.Map.of());
            return;
        }
        List<ItemContainer> containers = ShopContainerUtil.resolveContainers(
                world,
                shop,
                config.getPlayerShopChestLinkRadius()
        );
        boolean needsStorage = requiresRewardItems(trade) || requiresCostStorage(trade);
        if (needsStorage && containers.isEmpty()) {
            Messages.sendPrefixedKey(playerRef, "shop.trade.no_storage", java.util.Map.of());
            return;
        }

        if (trade.isMoneyTrade() && trade.isSellTrade()) {
            if (!economy.isEnabled()) {
                Messages.sendPrefixedKey(playerRef, "shop.trade.economy_disabled", java.util.Map.of());
                return;
            }
            if (!hasOwnerFunds(trade.getMoneyCost())) {
                Messages.sendPrefixedKey(playerRef, "shop.trade.out_of_funds", java.util.Map.of());
                return;
            }
            if (!InventoryUtil.hasItems(inventory, trade.getCostItems())) {
                Messages.sendPrefixedKey(playerRef, "shop.trade.missing_items", java.util.Map.of());
                return;
            }
            if (!ShopContainerUtil.canAddItems(containers, trade.getCostItems())) {
                Messages.sendPrefixedKey(playerRef, "shop.trade.storage_full", java.util.Map.of());
                return;
            }
            if (!InventoryUtil.removeItems(inventory, trade.getCostItems())) {
                Messages.sendPrefixedKey(playerRef, "shop.trade.remove_failed", java.util.Map.of());
                return;
            }
            if (!ShopContainerUtil.addItems(containers, trade.getCostItems())) {
                InventoryUtil.addItemsWithOverflow(inventory, trade.getCostItems());
                Messages.sendPrefixedKey(playerRef, "shop.trade.storage_full", java.util.Map.of());
                return;
            }
            if (!withdrawOwner(trade.getMoneyCost())) {
                ShopContainerUtil.removeItemsById(containers, trade.getCostItems());
                InventoryUtil.addItemsWithOverflow(inventory, trade.getCostItems());
                Messages.sendPrefixedKey(playerRef, "shop.trade.out_of_funds", java.util.Map.of());
                return;
            }
            economy.deposit(playerRef.getUuid(), trade.getMoneyCost());
            Messages.sendPrefixed(playerRef, formatTradeMessage(trade));
            return;
        }

        if (trade.isMoneyTrade()) {
            if (!economy.isEnabled()) {
                Messages.sendPrefixedKey(playerRef, "shop.trade.economy_disabled", java.util.Map.of());
                return;
            }
            long cost = trade.getMoneyCost();
            if (economy.getBalance(playerRef.getUuid()) < cost) {
                Messages.sendPrefixedKey(playerRef, "shop.trade.insufficient_funds", java.util.Map.of());
                return;
            }
            if (!ShopContainerUtil.hasItems(containers, trade.getRewardItems())) {
                Messages.sendPrefixedKey(playerRef, "shop.trade.out_of_stock", java.util.Map.of());
                return;
            }
            if (!economy.withdraw(playerRef.getUuid(), cost)) {
                Messages.sendPrefixedKey(playerRef, "shop.trade.payment_failed", java.util.Map.of());
                return;
            }
            if (!ShopContainerUtil.removeItemsById(containers, trade.getRewardItems())) {
                economy.deposit(playerRef.getUuid(), cost);
                Messages.sendPrefixedKey(playerRef, "shop.trade.out_of_stock", java.util.Map.of());
                return;
            }
            depositOwner(cost);
            List<com.hypixel.hytale.server.core.inventory.ItemStack> overflow =
                    InventoryUtil.addItemsWithOverflow(inventory, trade.getRewardItems());
            if (!overflow.isEmpty()) {
                dropOverflow(player, overflow);
                Messages.sendPrefixedKey(playerRef, "shop.trade.inventory_full", java.util.Map.of());
            }
            Messages.sendPrefixed(playerRef, formatTradeMessage(trade));
            return;
        }

        if (!InventoryUtil.hasItems(inventory, trade.getCostItems())) {
            Messages.sendPrefixedKey(playerRef, "shop.trade.missing_items", java.util.Map.of());
            return;
        }
        if (!ShopContainerUtil.hasItems(containers, trade.getRewardItems())) {
            Messages.sendPrefixedKey(playerRef, "shop.trade.out_of_stock", java.util.Map.of());
            return;
        }
        if (!ShopContainerUtil.canAddItems(containers, trade.getCostItems())) {
            Messages.sendPrefixedKey(playerRef, "shop.trade.storage_full", java.util.Map.of());
            return;
        }
        if (!InventoryUtil.removeItems(inventory, trade.getCostItems())) {
            Messages.sendPrefixedKey(playerRef, "shop.trade.remove_failed", java.util.Map.of());
            return;
        }
        if (!ShopContainerUtil.removeItemsById(containers, trade.getRewardItems())) {
            InventoryUtil.addItemsWithOverflow(inventory, trade.getCostItems());
            Messages.sendPrefixedKey(playerRef, "shop.trade.out_of_stock", java.util.Map.of());
            return;
        }
        if (!ShopContainerUtil.addItems(containers, trade.getCostItems())) {
            ShopContainerUtil.addItems(containers, trade.getRewardItems());
            InventoryUtil.addItemsWithOverflow(inventory, trade.getCostItems());
            Messages.sendPrefixedKey(playerRef, "shop.trade.storage_full", java.util.Map.of());
            return;
        }

        List<com.hypixel.hytale.server.core.inventory.ItemStack> overflow =
                InventoryUtil.addItemsWithOverflow(inventory, trade.getRewardItems());
        if (!overflow.isEmpty()) {
            dropOverflow(player, overflow);
            Messages.sendPrefixedKey(playerRef, "shop.trade.inventory_full", java.util.Map.of());
        }
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

    private boolean hasOwnerFunds(long amount) {
        UUID owner = resolveOwnerUuid();
        if (owner == null) return false;
        return economy.getBalance(owner) >= amount;
    }

    private boolean withdrawOwner(long amount) {
        UUID owner = resolveOwnerUuid();
        if (owner == null) return false;
        return economy.withdraw(owner, amount);
    }

    private void depositOwner(long amount) {
        UUID owner = resolveOwnerUuid();
        if (owner == null) return;
        economy.deposit(owner, amount);
    }

    @Nullable
    private UUID resolveOwnerUuid() {
        String raw = shop.getOwnerUuid();
        if (raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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

    private int getTotalPages() {
        int trades = shop.getTrades().size();
        return Math.max(1, (int) Math.ceil(trades / (double) TRADES_PER_PAGE));
    }

    private void updateFundsLabel(@Nonnull UICommandBuilder cmd) {
        if (!economy.isEnabled()) {
            cmd.set("#ShopFunds.Visible", false);
            return;
        }
        UUID owner = resolveOwnerUuid();
        long funds = owner != null ? economy.getBalance(owner) : 0L;
        String text = "Funds: " + economy.formatAmount(funds);
        cmd.set("#ShopFunds.Text", text);
        cmd.set("#ShopFunds.Visible", true);
    }

    private boolean requiresRewardItems(@Nonnull ShopTradeModel trade) {
        return !(trade.isMoneyTrade() && trade.isSellTrade());
    }

    private boolean requiresCostStorage(@Nonnull ShopTradeModel trade) {
        if (trade.getCostItems().isEmpty()) return false;
        return trade.isSellTrade() || !trade.isMoneyTrade();
    }

    private boolean isMoneySell(@Nonnull ShopTradeModel trade) {
        return trade.isMoneyTrade() && trade.isSellTrade();
    }

    @Nonnull
    private List<ItemContainer> resolveContainers(@Nonnull Store<EntityStore> store) {
        World world = resolveWorld(store);
        if (world == null) return List.of();
        return ShopContainerUtil.resolveContainers(world, shop, config.getPlayerShopChestLinkRadius());
    }

    @Nullable
    private World resolveWorld(@Nonnull Store<EntityStore> store) {
        Object external = store.getExternalData();
        if (external instanceof EntityStore entityStore) {
            return entityStore.getWorld();
        }
        return null;
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

