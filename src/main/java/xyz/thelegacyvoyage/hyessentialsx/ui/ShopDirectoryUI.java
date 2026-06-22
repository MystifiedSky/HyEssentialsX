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
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.AuctionHouseManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopAdminDraftCache;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopItemModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopTradeModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@SuppressWarnings("removal")
public final class ShopDirectoryUI extends InteractiveCustomUIPage<ShopDirectoryUI.UIEventData> {

    private static final String LAYOUT = "hyessentialsx/ShopDirectory.ui";
    private static final String ROW_LAYOUT = "hyessentialsx/ShopDirectoryRow.ui";
    private static final String ICON_LAYOUT = "hyessentialsx/ShopTradeItemIconSmall.ui";
    private static final int SHOPS_PER_PAGE = 6;
    private static final int PREVIEW_ITEM_LIMIT = 5;
    private static final String ADMIN_SHOP_USE_PERMISSION = "hyessentialsx.adminshop.use";
    private static final String LEGACY_ADMIN_SHOP_USE_PERMISSION = "hyessentialsx.shop";
    private static final String PLAYER_SHOP_USE_PERMISSION = "hyessentialsx.playershop.use";
    private static final String PLAYER_SHOP_ADMIN_PERMISSION = "hyessentialsx.playershop.admin";
    private static final String LEGACY_PLAYER_SHOP_USE_PERMISSION = "hyessentialsx.playershop";
    private static final String AUCTION_HOUSE_USE_PERMISSION = "hyessentialsx.auctionhouse.use";

    private final PlayerRef playerRef;
    private final ShopManager shopManager;
    private final EconomyManager economy;
    private final ShopAdminDraftCache draftCache;
    private final ConfigManager config;
    private final StorageManager storage;
    private final AuctionHouseManager auctionHouseManager;
    private String tab = "player";
    private String search = "";
    private int page;

    public ShopDirectoryUI(@Nonnull PlayerRef playerRef,
                           @Nonnull ShopManager shopManager,
                           @Nonnull EconomyManager economy,
                           @Nonnull ShopAdminDraftCache draftCache,
                           @Nonnull ConfigManager config,
                           @Nonnull StorageManager storage,
                           @Nonnull AuctionHouseManager auctionHouseManager) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.playerRef = playerRef;
        this.shopManager = shopManager;
        this.economy = economy;
        this.draftCache = draftCache;
        this.config = config;
        this.storage = storage;
        this.auctionHouseManager = auctionHouseManager;
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
            case "tab" -> {
                String next = normalizeTab(data.tab);
                if ("auction".equals(next)) {
                    openAuctionHouse(ref, store);
                    return;
                }
                if (!tab.equals(next)) {
                    tab = next;
                    page = 0;
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
            case "prev" -> {
                if (page > 0) {
                    page--;
                    refresh(ref, store);
                }
            }
            case "next" -> {
                int totalPages = getTotalPages(getVisibleShops(store, ref).size());
                if (page < totalPages - 1) {
                    page++;
                    refresh(ref, store);
                }
            }
            case "open" -> {
                if (data.shopName != null) {
                    openShop(ref, store, data.shopName);
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
        List<ShopModel> shops = getVisibleShops(store, ref);
        int totalPages = getTotalPages(shops.size());
        if (page >= totalPages) page = totalPages - 1;

        int start = page * SHOPS_PER_PAGE;
        int end = Math.min(shops.size(), start + SHOPS_PER_PAGE);

        cmd.set("#SearchInput.Value", search);
        cmd.set("#TabPlayer.Disabled", tab.equals("player"));
        cmd.set("#TabAdmin.Disabled", tab.equals("admin"));
        cmd.set("#TabAuction.Disabled", false);
        cmd.set("#ShopRows.Visible", !shops.isEmpty());
        cmd.set("#EmptyLabel.Visible", shops.isEmpty());
        cmd.set("#EmptyLabel.Text", "No shops found");
        cmd.set("#PageInfo.Text", "Page " + (page + 1) + "/" + totalPages);
        cmd.set("#PrevPage.Disabled", page <= 0);
        cmd.set("#NextPage.Disabled", page >= totalPages - 1);
        cmd.set("#ResultInfo.Text", formatResultInfo(shops.size()));
        cmd.clear("#ShopRows");

        for (int idx = start; idx < end; idx++) {
            ShopModel shop = shops.get(idx);
            String selector = "#ShopRows[" + (idx - start) + "]";
            cmd.append("#ShopRows", ROW_LAYOUT);
            renderShopRow(cmd, evt, selector, shop);
        }

        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SearchInput",
                EventData.of("Action", "search").append("@Search", "#SearchInput.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ClearSearchButton",
                EventData.of("Action", "clear-search"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabPlayer",
                EventData.of("Action", "tab").append("Tab", "player"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabAdmin",
                EventData.of("Action", "tab").append("Tab", "admin"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabAuction",
                EventData.of("Action", "tab").append("Tab", "auction"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PrevPage",
                EventData.of("Action", "prev"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NextPage",
                EventData.of("Action", "next"), false);
    }

    private void renderShopRow(@Nonnull UICommandBuilder cmd,
                               @Nonnull UIEventBuilder evt,
                               @Nonnull String selector,
                               @Nonnull ShopModel shop) {
        List<ShopItemModel> items = collectPreviewItems(shop);
        String icon = firstItemId(items);
        cmd.set(selector + " #MainIcon.Visible", !icon.isBlank());
        if (!icon.isBlank()) {
            cmd.set(selector + " #MainIcon.ItemId", icon);
        }
        cmd.set(selector + " #ShopName.Text", shop.getDisplayName());
        cmd.set(selector + " #ShopMeta.Text", (shop.isPlayerShop() ? "Player shop" : "Admin shop")
                + " - " + shop.getTrades().size() + " trade" + (shop.getTrades().size() == 1 ? "" : "s")
                + (shop.isOpen() ? " - Open" : " - Closed"));
        cmd.set(selector + " #ShopItems.Text", previewText(items));

        cmd.clear(selector + " #PreviewItems");
        for (int i = 0; i < Math.min(items.size(), PREVIEW_ITEM_LIMIT); i++) {
            ShopItemModel item = items.get(i);
            String itemSelector = selector + " #PreviewItems[" + i + "]";
            cmd.append(selector + " #PreviewItems", ICON_LAYOUT);
            cmd.set(itemSelector + " #ItemIcon.ItemId", item.getItemId());
            cmd.set(itemSelector + " #Quantity.Text", "x" + item.getQuantity());
            cmd.set(itemSelector + " #InfoLabel.Text", "");
        }

        evt.addEventBinding(CustomUIEventBindingType.Activating, selector + " #OpenButton",
                EventData.of("Action", "open").append("Shop", shop.getName()), false);
    }

    @Nonnull
    private List<ShopModel> getVisibleShops(@Nonnull Store<EntityStore> store,
                                            @Nonnull Ref<EntityStore> ref) {
        List<String> names = tab.equals("admin") ? shopManager.listAdminShops() : shopManager.listPlayerShops();
        List<ShopModel> out = new ArrayList<>();
        for (String name : names) {
            ShopModel shop = shopManager.getShop(name);
            if (shop == null) continue;
            if (!canUseShop(store, ref, shop)) continue;
            if (!matchesSearch(shop)) continue;
            out.add(shop);
        }
        return out;
    }

    private boolean canUseShop(@Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull ShopModel shop) {
        if (shop.isPlayerShop()) {
            return hasPermission(store, ref, PLAYER_SHOP_ADMIN_PERMISSION)
                    || hasPermission(store, ref, PLAYER_SHOP_USE_PERMISSION)
                    || hasPermission(store, ref, LEGACY_PLAYER_SHOP_USE_PERMISSION);
        }
        String usePermission = shop.getUsePermission();
        if (usePermission.isBlank()) return true;
        if (hasPermission(store, ref, usePermission)) return true;
        return usePermission.equalsIgnoreCase(ShopManager.DEFAULT_USE_PERMISSION)
                && (hasPermission(store, ref, ADMIN_SHOP_USE_PERMISSION)
                || hasPermission(store, ref, LEGACY_ADMIN_SHOP_USE_PERMISSION)
                || hasPermission(store, ref, ShopManager.LEGACY_USE_PERMISSION));
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
        boolean moduleHas = CommandPermissionUtil.hasPermission(playerRef, permission)
                || PermissionsModule.get().hasPermission(playerRef.getUuid(), permission, false);
        if (!CommandPermissionUtil.isPermissionsSystemEnabled()) {
            return moduleHas;
        }
        if (PermissionsModule.get().getFirstPermissionProvider() == null) {
            return componentHas != null ? componentHas : moduleHas;
        }
        return moduleHas || Boolean.TRUE.equals(componentHas);
    }

    private boolean matchesSearch(@Nonnull ShopModel shop) {
        String query = search.trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) return true;
        if (shop.getName().toLowerCase(Locale.ROOT).contains(query)) return true;
        if (shop.getDisplayName().toLowerCase(Locale.ROOT).contains(query)) return true;
        for (ShopTradeModel trade : shop.getTrades()) {
            if (matchesItems(trade.getCostItems(), query) || matchesItems(trade.getRewardItems(), query)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesItems(@Nonnull List<ShopItemModel> items, @Nonnull String query) {
        for (ShopItemModel item : items) {
            if (item == null) continue;
            String id = item.getItemId();
            if (id != null && id.toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private List<ShopItemModel> collectPreviewItems(@Nonnull ShopModel shop) {
        Set<String> seen = new LinkedHashSet<>();
        List<ShopItemModel> out = new ArrayList<>();
        for (ShopTradeModel trade : shop.getTrades()) {
            addPreviewItems(out, seen, trade.getRewardItems());
            addPreviewItems(out, seen, trade.getCostItems());
            if (out.size() >= PREVIEW_ITEM_LIMIT) break;
        }
        return out;
    }

    private void addPreviewItems(@Nonnull List<ShopItemModel> out,
                                 @Nonnull Set<String> seen,
                                 @Nonnull List<ShopItemModel> items) {
        for (ShopItemModel item : items) {
            if (item == null) continue;
            String id = item.getItemId();
            if (id == null || id.isBlank()) continue;
            String key = id.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) continue;
            out.add(item);
            if (out.size() >= PREVIEW_ITEM_LIMIT) return;
        }
    }

    @Nonnull
    private String previewText(@Nonnull List<ShopItemModel> items) {
        if (items.isEmpty()) return "No item preview";
        List<String> ids = new ArrayList<>();
        for (ShopItemModel item : items) {
            if (item == null || item.getItemId().isBlank()) continue;
            ids.add(shortItemName(item.getItemId()));
        }
        return ids.isEmpty() ? "No item preview" : String.join(", ", ids);
    }

    @Nonnull
    private String shortItemName(@Nonnull String itemId) {
        int slash = itemId.lastIndexOf('/');
        int colon = itemId.lastIndexOf(':');
        int underscore = itemId.lastIndexOf('_');
        int idx = Math.max(Math.max(slash, colon), underscore);
        return idx >= 0 && idx + 1 < itemId.length() ? itemId.substring(idx + 1) : itemId;
    }

    @Nonnull
    private String firstItemId(@Nonnull List<ShopItemModel> items) {
        for (ShopItemModel item : items) {
            if (item == null) continue;
            if (!item.getItemId().isBlank()) return item.getItemId();
        }
        return "";
    }

    private void openShop(@Nonnull Ref<EntityStore> ref,
                          @Nonnull Store<EntityStore> store,
                          @Nonnull String shopName) {
        ShopModel shop = shopManager.getShop(shopName);
        if (shop == null || !canUseShop(store, ref, shop)) {
            Messages.sendPrefixedKey(playerRef, "shop.use.no_permission", java.util.Map.of());
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.sendPrefixedKey(playerRef, "shop.admin.ui_failed", java.util.Map.of());
            return;
        }
        if (shop.isPlayerShop()) {
            new PlayerShopBrowseUI(playerRef, shopManager, economy, config, shop, storage, draftCache)
                    .open(player, ref, store);
        } else {
            new ShopBrowseUI(playerRef, shopManager, economy, shop, draftCache)
                    .open(player, ref, store);
        }
    }

    private void openAuctionHouse(@Nonnull Ref<EntityStore> ref,
                                  @Nonnull Store<EntityStore> store) {
        if (!config.isAuctionHouseEnabled() || !economy.isEnabled()) {
            Messages.sendPrefixedKey(playerRef, "auction.disabled", java.util.Map.of());
            return;
        }
        if (!CommandPermissionUtil.hasPermission(playerRef, AUCTION_HOUSE_USE_PERMISSION)) {
            Messages.sendPrefixedKey(playerRef, "auction.no_permission", java.util.Map.of());
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.sendPrefixedKey(playerRef, "auction.ui_failed", java.util.Map.of());
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new AuctionHouseUI(playerRef, auctionHouseManager, economy, config));
    }

    private int getTotalPages(int count) {
        return Math.max(1, (int) Math.ceil(count / (double) SHOPS_PER_PAGE));
    }

    @Nonnull
    private String formatResultInfo(int count) {
        String type = tab.equals("admin") ? "admin shop" : "player shop";
        return count + " " + type + (count == 1 ? "" : "s");
    }

    @Nonnull
    private String normalizeTab(String raw) {
        if (raw == null) return "player";
        String normalized = raw.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "admin", "auction" -> normalized;
            default -> "player";
        };
    }

    @Nonnull
    private String safe(String raw) {
        return raw == null ? "" : raw.trim();
    }

    public void open(@Nonnull Player player, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        player.getPageManager().openCustomPage(ref, store, this);
    }

    public static class UIEventData {
        public static final BuilderCodec<UIEventData> CODEC = BuilderCodec.builder(UIEventData.class, UIEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (e, v) -> e.action = v, e -> e.action).add()
                .append(new KeyedCodec<>("Tab", Codec.STRING), (e, v) -> e.tab = v, e -> e.tab).add()
                .append(new KeyedCodec<>("Shop", Codec.STRING), (e, v) -> e.shopName = v, e -> e.shopName).add()
                .append(new KeyedCodec<>("@Search", Codec.STRING), (e, v) -> e.search = v, e -> e.search).add()
                .build();

        private String action;
        private String tab;
        private String shopName;
        private String search;
    }
}
