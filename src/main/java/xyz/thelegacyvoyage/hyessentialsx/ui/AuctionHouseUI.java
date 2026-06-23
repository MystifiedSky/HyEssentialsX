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
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.bson.BsonDocument;
import xyz.thelegacyvoyage.hyessentialsx.managers.AuctionHouseManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.models.AuctionListingModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.InventoryUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("removal")
public final class AuctionHouseUI extends InteractiveCustomUIPage<AuctionHouseUI.UIEventData> {

    private static final String LAYOUT = "hyessentialsx/AuctionHousePage.ui";
    private static final String ROW_LAYOUT = "hyessentialsx/AuctionHouseRow.ui";
    private static final int PAGE_SIZE = 7;
    private static final String SELL_PERMISSION = "hyessentialsx.auctionhouse.sell";

    private final PlayerRef playerRef;
    private final AuctionHouseManager manager;
    private final EconomyManager economy;
    private final ConfigManager config;
    @Nullable
    private final UiBackTarget backTarget;
    private String tab = "browse";
    private int page;
    private String priceInput = "";
    private String hoursInput = "";
    private String search = "";
    private String sort = "ending";

    public AuctionHouseUI(@Nonnull PlayerRef playerRef,
                          @Nonnull AuctionHouseManager manager,
                          @Nonnull EconomyManager economy,
                          @Nonnull ConfigManager config) {
        this(playerRef, manager, economy, config, null);
    }

    public AuctionHouseUI(@Nonnull PlayerRef playerRef,
                          @Nonnull AuctionHouseManager manager,
                          @Nonnull EconomyManager economy,
                          @Nonnull ConfigManager config,
                          @Nullable UiBackTarget backTarget) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.playerRef = playerRef;
        this.manager = manager;
        this.economy = economy;
        this.config = config;
        this.backTarget = backTarget;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt,
                      @Nonnull Store<EntityStore> store) {
        cmd.append(LAYOUT);
        rebuild(cmd, evt);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull UIEventData data) {
        if (data.action == null) return;
        switch (data.action) {
            case "close" -> close();
            case "back" -> openBack(ref, store);
            case "tab" -> {
                tab = switch (data.tab) {
                    case "mine" -> "mine";
                    case "list" -> "list";
                    default -> "browse";
                };
                page = 0;
                refresh();
            }
            case "price" -> {
                priceInput = safe(data.price);
            }
            case "hours" -> {
                hoursInput = safe(data.hours);
            }
            case "search" -> {
                search = safe(data.search);
                page = 0;
                refresh();
            }
            case "clear-search" -> {
                search = "";
                page = 0;
                refresh();
            }
            case "sort" -> {
                sort = switch (safe(data.sort).toLowerCase(Locale.ROOT)) {
                    case "price-low" -> "price-low";
                    case "price-high" -> "price-high";
                    default -> "ending";
                };
                page = 0;
                refresh();
            }
            case "list-held" -> listHeld(ref, store);
            case "prev" -> {
                if (page > 0) {
                    page--;
                    refresh();
                }
            }
            case "next" -> {
                if (page < getTotalPages(currentListings().size()) - 1) {
                    page++;
                    refresh();
                }
            }
            case "buy" -> buy(ref, store, data.id);
            case "cancel" -> cancel(ref, store, data.id);
            case "claim" -> claim(ref, store, data.id);
            default -> {
            }
        }
    }

    private void refresh() {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        rebuild(cmd, evt);
        sendUpdate(cmd, evt, false);
    }

    private void rebuild(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt) {
        List<AuctionListingModel> listings = currentListings();
        int totalPages = getTotalPages(listings.size());
        if (page >= totalPages) page = totalPages - 1;
        int start = page * PAGE_SIZE;
        int end = Math.min(listings.size(), start + PAGE_SIZE);

        cmd.set("#TabBrowse.Disabled", tab.equals("browse"));
        cmd.set("#TabMine.Disabled", tab.equals("mine"));
        cmd.set("#TabList.Disabled", tab.equals("list"));
        cmd.set("#ListingPanel.Visible", tab.equals("list"));
        cmd.set("#RowsContainer.Visible", !tab.equals("list"));
        cmd.set("#Pager.Visible", !tab.equals("list"));
        cmd.set("#AuctionSearchInput.Value", search);
        cmd.set("#SortEndingButton.Disabled", sort.equals("ending"));
        cmd.set("#SortPriceLowButton.Disabled", sort.equals("price-low"));
        cmd.set("#SortPriceHighButton.Disabled", sort.equals("price-high"));
        cmd.set("#PriceInput.Value", priceInput);
        cmd.set("#HoursInput.Value", hoursInput);
        cmd.set("#AuctionBalanceValue.Text", economy.isEnabled() ? economy.formatAmount(economy.getBalance(playerRef.getUuid())) : "Disabled");
        cmd.set("#BackToParentButton.Visible", backTarget != null);
        cmd.set("#ListCost.Text", "Listing cost: " + economy.formatAmount(config.getAuctionHouseListingCost()));
        cmd.set("#DefaultDuration.Text", "Default: " + Math.max(1L, config.getAuctionHouseDefaultListingSeconds() / 3600L) + "h");
        cmd.set("#PageInfo.Text", "Page " + (page + 1) + "/" + totalPages);
        cmd.set("#PrevPage.Disabled", page <= 0);
        cmd.set("#NextPage.Disabled", page >= totalPages - 1);
        cmd.set("#ResultInfo.Text", listings.size() + (listings.size() == 1 ? " listing" : " listings"));
        cmd.clear("#AuctionRows");
        cmd.set("#EmptyLabel.Visible", listings.isEmpty());
        cmd.set("#EmptyLabel.Text", tab.equals("mine") ? "No listings from you yet" : "No active listings");

        if (!tab.equals("list")) {
            for (int i = start; i < end; i++) {
                String selector = "#AuctionRows[" + (i - start) + "]";
                cmd.append("#AuctionRows", ROW_LAYOUT);
                renderRow(cmd, evt, selector, listings.get(i));
            }
        }

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("Action", "close"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackToParentButton", EventData.of("Action", "back"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabBrowse", EventData.of("Action", "tab").append("Tab", "browse"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabMine", EventData.of("Action", "tab").append("Tab", "mine"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabList", EventData.of("Action", "tab").append("Tab", "list"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PriceInput",
                EventData.of("Action", "price").append("@Price", "#PriceInput.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#HoursInput",
                EventData.of("Action", "hours").append("@Hours", "#HoursInput.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#AuctionSearchInput",
                EventData.of("Action", "search").append("@Search", "#AuctionSearchInput.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ClearAuctionSearchButton",
                EventData.of("Action", "clear-search"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SortEndingButton",
                EventData.of("Action", "sort").append("Sort", "ending"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SortPriceLowButton",
                EventData.of("Action", "sort").append("Sort", "price-low"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SortPriceHighButton",
                EventData.of("Action", "sort").append("Sort", "price-high"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ListHeldButton", EventData.of("Action", "list-held"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PrevPage", EventData.of("Action", "prev"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NextPage", EventData.of("Action", "next"), false);
    }

    private void openBack(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (backTarget == null) {
            close();
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            close();
            return;
        }
        backTarget.open(player, ref, store);
    }

    private void renderRow(@Nonnull UICommandBuilder cmd,
                           @Nonnull UIEventBuilder evt,
                           @Nonnull String selector,
                           @Nonnull AuctionListingModel listing) {
        cmd.set(selector + " #ItemIcon.ItemId", listing.getItemId());
        cmd.set(selector + " #ItemName.Text", simpleName(listing.getItemId()) + " x" + listing.getQuantity());
        cmd.set(selector + " #Seller.Text", "Seller: " + listing.getSellerName());
        cmd.set(selector + " #Price.Text", economy.formatAmount(listing.getPrice()));
        cmd.set(selector + " #Time.Text", formatTimeLeft(listing));

        String status = listing.getStatus();
        boolean mine = tab.equals("mine");
        boolean canBuy = !mine && "active".equals(status);
        boolean canCancel = mine && "active".equals(status);
        boolean canClaim = mine && ("expired".equals(status) || "cancelled".equals(status));
        cmd.set(selector + " #BuyButton.Visible", canBuy);
        cmd.set(selector + " #CancelButton.Visible", canCancel);
        cmd.set(selector + " #ClaimButton.Visible", canClaim);
        cmd.set(selector + " #StatusLabel.Visible", mine && !canCancel && !canClaim);
        cmd.set(selector + " #StatusLabel.Text", status);

        evt.addEventBinding(CustomUIEventBindingType.Activating, selector + " #BuyButton",
                EventData.of("Action", "buy").append("Id", listing.getId()), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, selector + " #CancelButton",
                EventData.of("Action", "cancel").append("Id", listing.getId()), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, selector + " #ClaimButton",
                EventData.of("Action", "claim").append("Id", listing.getId()), false);
    }

    private void buy(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String id) {
        AuctionListingModel listing = manager.getListing(id);
        long now = System.currentTimeMillis();
        if (listing == null || !listing.isActive(now)) {
            Messages.sendPrefixedKey(playerRef, "auction.buy.unavailable", Map.of());
            refresh();
            return;
        }
        if (playerRef.getUuid().toString().equalsIgnoreCase(listing.getSellerUuid())) {
            Messages.sendPrefixedKey(playerRef, "auction.buy.own_listing", Map.of());
            return;
        }
        if (!economy.withdraw(playerRef.getUuid(), listing.getPrice())) {
            Messages.sendPrefixedKey(playerRef, "economy.insufficient_funds", Map.of());
            return;
        }
        if (!manager.markSold(listing, playerRef, now)) {
            economy.deposit(playerRef.getUuid(), listing.getPrice());
            Messages.sendPrefixedKey(playerRef, "auction.buy.unavailable", Map.of());
            refresh();
            return;
        }
        economy.deposit(UUID.fromString(listing.getSellerUuid()), listing.getPrice());
        Player player = store.getComponent(ref, Player.getComponentType());
        ItemStack stack = toItemStack(listing);
        if (player != null && player.getInventory() != null && stack != null) {
            List<ItemStack> overflow = InventoryUtil.addItemStacksWithOverflow(player.getInventory(), List.of(stack));
            if (!overflow.isEmpty()) {
                dropOverflow(player, overflow);
                Messages.sendPrefixedKey(playerRef, "auction.inventory_full", Map.of());
            }
        }
        Messages.sendPrefixedKey(playerRef, "auction.buy.success", Map.of(
                "item", listing.getItemId(),
                "amount", String.valueOf(listing.getQuantity()),
                "price", economy.formatAmount(listing.getPrice())
        ));
        refresh();
    }

    private void listHeld(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (!CommandPermissionUtil.hasPermission(playerRef, SELL_PERMISSION)) {
            Messages.sendPrefixedKey(playerRef, "auction.no_permission", Map.of());
            return;
        }
        if (!economy.isEnabled()) {
            Messages.sendPrefixedKey(playerRef, "economy.disabled", Map.of());
            return;
        }
        long price = economy.parseAmount(priceInput);
        if (price <= 0L) {
            Messages.sendPrefixedKey(playerRef, "auction.sell.invalid_price", Map.of());
            return;
        }
        int maxListings = manager.resolveMaxListings(playerRef);
        if (maxListings >= 0 && manager.countActiveListings(playerRef.getUuid()) >= maxListings) {
            Messages.sendPrefixedKey(playerRef, "auction.sell.limit_reached", Map.of("limit", String.valueOf(maxListings)));
            return;
        }
        long maxDuration = manager.resolveMaxDurationSeconds(playerRef);
        long duration = config.getAuctionHouseDefaultListingSeconds();
        if (!hoursInput.isBlank()) {
            try {
                int hours = Integer.parseInt(hoursInput.trim());
                if (hours <= 0) {
                    Messages.sendPrefixedKey(playerRef, "auction.sell.invalid_duration", Map.of());
                    return;
                }
                duration = hours * 3600L;
            } catch (NumberFormatException ignored) {
                Messages.sendPrefixedKey(playerRef, "auction.sell.invalid_duration", Map.of());
                return;
            }
        }
        if (duration > maxDuration) {
            Messages.sendPrefixedKey(playerRef, "auction.sell.duration_too_long", Map.of("hours", String.valueOf(maxDuration / 3600L)));
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || player.getInventory() == null) {
            Messages.sendPrefixedKey(playerRef, "error.inventory_access", Map.of());
            return;
        }
        ItemStack held = player.getInventory().getItemInHand();
        if (held == null || held.isEmpty() || held.getQuantity() <= 0) {
            Messages.sendPrefixedKey(playerRef, "auction.sell.no_item", Map.of());
            return;
        }
        long listingCost = config.getAuctionHouseListingCost();
        if (listingCost > 0L && !economy.withdraw(playerRef.getUuid(), listingCost)) {
            Messages.sendPrefixedKey(playerRef, "economy.insufficient_funds", Map.of());
            return;
        }
        ItemStack listedStack = cloneStack(held);
        if (!InventoryUtil.clearActiveHand(player.getInventory())) {
            if (listingCost > 0L) economy.deposit(playerRef.getUuid(), listingCost);
            Messages.sendPrefixedKey(playerRef, "auction.sell.remove_failed", Map.of());
            return;
        }
        AuctionHouseManager.CreateResult result = manager.createListing(playerRef, listedStack, price, duration, listingCost);
        if (!"created".equals(result.status()) || result.listing() == null) {
            InventoryUtil.addItemStacksWithOverflow(player.getInventory(), List.of(listedStack));
            if (listingCost > 0L) economy.deposit(playerRef.getUuid(), listingCost);
            Messages.sendPrefixedKey(playerRef, "auction.sell.failed", Map.of());
            return;
        }
        AuctionListingModel listing = result.listing();
        priceInput = "";
        hoursInput = "";
        tab = "mine";
        Messages.sendPrefixedKey(playerRef, "auction.sell.created", Map.of(
                "item", listing.getItemId(),
                "amount", String.valueOf(listing.getQuantity()),
                "price", economy.formatAmount(listing.getPrice()),
                "hours", String.valueOf(Math.max(1L, duration / 3600L))
        ));
        refresh();
    }

    private void cancel(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String id) {
        AuctionListingModel listing = manager.getListing(id);
        if (listing == null || !manager.cancel(id, playerRef.getUuid())) {
            Messages.sendPrefixedKey(playerRef, "auction.cancel.failed", Map.of());
            refresh();
            return;
        }
        if (!deliverToPlayer(ref, store, listing)) {
            Messages.sendPrefixedKey(playerRef, "auction.claim.failed", Map.of());
            refresh();
            return;
        }
        manager.markClaimed(id, playerRef.getUuid());
        Messages.sendPrefixedKey(playerRef, "auction.cancel.success", Map.of());
        refresh();
    }

    private void claim(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String id) {
        AuctionListingModel listing = manager.getListing(id);
        if (listing == null
                || !playerRef.getUuid().toString().equalsIgnoreCase(listing.getSellerUuid())
                || (!"expired".equals(listing.getStatus()) && !"cancelled".equals(listing.getStatus()))) {
            Messages.sendPrefixedKey(playerRef, "auction.claim.failed", Map.of());
            refresh();
            return;
        }
        if (!deliverToPlayer(ref, store, listing)) {
            Messages.sendPrefixedKey(playerRef, "auction.claim.failed", Map.of());
            refresh();
            return;
        }
        manager.markClaimed(id, playerRef.getUuid());
        Messages.sendPrefixedKey(playerRef, "auction.claim.success", Map.of());
        refresh();
    }

    private boolean deliverToPlayer(@Nonnull Ref<EntityStore> ref,
                                    @Nonnull Store<EntityStore> store,
                                    @Nonnull AuctionListingModel listing) {
        Player player = store.getComponent(ref, Player.getComponentType());
        ItemStack stack = toItemStack(listing);
        if (player == null || player.getInventory() == null || stack == null) return false;
        List<ItemStack> overflow = InventoryUtil.addItemStacksWithOverflow(player.getInventory(), List.of(stack));
        if (!overflow.isEmpty()) {
            dropOverflow(player, overflow);
            Messages.sendPrefixedKey(playerRef, "auction.inventory_full", Map.of());
        }
        return true;
    }

    private void dropOverflow(@Nonnull Player player, @Nonnull List<ItemStack> overflow) {
        for (ItemStack stack : overflow) {
            if (stack == null || stack.isEmpty()) continue;
            if (!tryDropItem(player, stack)) return;
        }
    }

    private boolean tryDropItem(@Nonnull Player player, @Nonnull ItemStack stack) {
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

    @Nonnull
    private List<AuctionListingModel> currentListings() {
        List<AuctionListingModel> listings;
        if (tab.equals("mine")) {
            listings = manager.listOwn(playerRef.getUuid());
        } else if (tab.equals("list")) {
            return List.of();
        } else {
            listings = manager.listActive();
        }
        String needle = search.trim().toLowerCase(Locale.ROOT);
        List<AuctionListingModel> filtered = new ArrayList<>();
        for (AuctionListingModel listing : listings) {
            if (needle.isBlank() || auctionSearchText(listing).contains(needle)) {
                filtered.add(listing);
            }
        }
        Comparator<AuctionListingModel> comparator = switch (sort) {
            case "price-low" -> Comparator.comparingLong(AuctionListingModel::getPrice);
            case "price-high" -> Comparator.comparingLong(AuctionListingModel::getPrice).reversed();
            default -> Comparator.comparingLong(AuctionListingModel::getExpiresAt);
        };
        filtered.sort(comparator.thenComparing(AuctionListingModel::getItemId));
        return filtered;
    }

    @Nonnull
    private String auctionSearchText(@Nonnull AuctionListingModel listing) {
        return (listing.getItemId() + " " + simpleName(listing.getItemId()) + " " + listing.getSellerName() + " " + listing.getStatus())
                .toLowerCase(Locale.ROOT);
    }

    private int getTotalPages(int size) {
        return Math.max(1, (int) Math.ceil(size / (double) PAGE_SIZE));
    }

    @Nonnull
    private String formatTimeLeft(@Nonnull AuctionListingModel listing) {
        String status = listing.getStatus();
        if (!"active".equals(status)) return status;
        long remaining = Math.max(0L, listing.getExpiresAt() - System.currentTimeMillis()) / 1000L;
        long hours = remaining / 3600L;
        long minutes = (remaining % 3600L) / 60L;
        return hours > 0 ? hours + "h " + minutes + "m" : Math.max(1L, minutes) + "m";
    }

    @Nonnull
    private String simpleName(@Nonnull String itemId) {
        int idx = Math.max(itemId.lastIndexOf(':'), itemId.lastIndexOf('/'));
        return idx >= 0 && idx < itemId.length() - 1 ? itemId.substring(idx + 1) : itemId;
    }

    @Nonnull
    private String safe(String raw) {
        return raw == null ? "" : raw.trim();
    }

    @Nonnull
    private static ItemStack cloneStack(@Nonnull ItemStack stack) {
        return new ItemStack(stack.getItemId(), stack.getQuantity(), stack.getDurability(), stack.getMaxDurability(), stack.getMetadata());
    }

    private static ItemStack toItemStack(@Nonnull AuctionListingModel listing) {
        if (listing.getItemId().isBlank() || listing.getQuantity() <= 0) return null;
        BsonDocument meta = null;
        String metadataJson = listing.getMetadataJson();
        if (metadataJson != null && !metadataJson.isBlank()) {
            try {
                meta = BsonDocument.parse(metadataJson);
            } catch (Exception ignored) {
            }
        }
        return new ItemStack(
                listing.getItemId(),
                listing.getQuantity(),
                listing.getDurability(),
                listing.getMaxDurability(),
                meta
        );
    }

    public static final class UIEventData {
        public static final BuilderCodec<UIEventData> CODEC = BuilderCodec
                .builder(UIEventData.class, UIEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>("Tab", Codec.STRING), (d, v) -> d.tab = v, d -> d.tab).add()
                .append(new KeyedCodec<>("Id", Codec.STRING), (d, v) -> d.id = v, d -> d.id).add()
                .append(new KeyedCodec<>("@Price", Codec.STRING), (d, v) -> d.price = v, d -> d.price).add()
                .append(new KeyedCodec<>("@Hours", Codec.STRING), (d, v) -> d.hours = v, d -> d.hours).add()
                .append(new KeyedCodec<>("@Search", Codec.STRING), (d, v) -> d.search = v, d -> d.search).add()
                .append(new KeyedCodec<>("Sort", Codec.STRING), (d, v) -> d.sort = v, d -> d.sort).add()
                .build();

        private String action;
        private String tab;
        private String id;
        private String price;
        private String hours;
        private String search;
        private String sort;
    }
}
