package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import xyz.thelegacyvoyage.hyessentialsx.models.AuctionHouseDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.AuctionListingModel;
import xyz.thelegacyvoyage.hyessentialsx.models.AuctionNpcModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopNpcModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class AuctionHouseManager {

    public static final String LISTING_LIMIT_PERMISSION_PREFIX = "hyessentialsx.auctionhouse.listings.";
    public static final String DURATION_PERMISSION_PREFIX = "hyessentialsx.auctionhouse.duration.";
    public static final String AUCTION_SHOP_NAME = "__auctionhouse";
    private static final int MAX_LISTING_PERMISSION_SCAN = 1000;
    private static final int MAX_DURATION_PERMISSION_HOURS_SCAN = 24 * 365;

    private final StorageManager storage;
    private final ConfigManager config;
    private AuctionHouseDataModel data = new AuctionHouseDataModel();

    public AuctionHouseManager(@Nonnull StorageManager storage, @Nonnull ConfigManager config) {
        this.storage = storage;
        this.config = config;
        load();
    }

    public synchronized void load() {
        data = storage.getAuctionHouseData();
        expireListings(System.currentTimeMillis());
    }

    public synchronized void save() {
        storage.saveAuctionHouseData(data);
    }

    public boolean isEnabled() {
        return config.isAuctionHouseEnabled();
    }

    @Nonnull
    public synchronized List<AuctionListingModel> listActive() {
        long now = System.currentTimeMillis();
        expireListings(now);
        List<AuctionListingModel> out = new ArrayList<>();
        for (AuctionListingModel listing : data.getListings()) {
            if (listing != null && listing.isActive(now)) {
                out.add(listing);
            }
        }
        out.sort(Comparator.comparingLong(AuctionListingModel::getCreatedAt).reversed());
        return out;
    }

    @Nonnull
    public synchronized List<AuctionListingModel> listOwn(@Nonnull UUID sellerUuid) {
        long now = System.currentTimeMillis();
        expireListings(now);
        String uuid = sellerUuid.toString();
        List<AuctionListingModel> out = new ArrayList<>();
        for (AuctionListingModel listing : data.getListings()) {
            if (listing == null) continue;
            if (uuid.equalsIgnoreCase(listing.getSellerUuid())) {
                out.add(listing);
            }
        }
        out.sort(Comparator.comparingLong(AuctionListingModel::getCreatedAt).reversed());
        return out;
    }

    public synchronized int countActiveListings(@Nonnull UUID sellerUuid) {
        String uuid = sellerUuid.toString();
        int count = 0;
        long now = System.currentTimeMillis();
        expireListings(now);
        for (AuctionListingModel listing : data.getListings()) {
            if (listing != null && listing.isActive(now) && uuid.equalsIgnoreCase(listing.getSellerUuid())) {
                count++;
            }
        }
        return count;
    }

    @Nullable
    public synchronized AuctionListingModel getListing(@Nonnull String id) {
        for (AuctionListingModel listing : data.getListings()) {
            if (listing != null && listing.getId().equalsIgnoreCase(id)) {
                return listing;
            }
        }
        return null;
    }

    @Nonnull
    public synchronized CreateResult createListing(@Nonnull PlayerRef seller,
                                                   @Nonnull ItemStack stack,
                                                   long price,
                                                   long durationSeconds,
                                                   long listingCost) {
        if (!isEnabled()) return CreateResult.disabled();
        if (stack.isEmpty() || stack.getItemId() == null || stack.getItemId().isBlank() || stack.getQuantity() <= 0) {
            return CreateResult.noItem();
        }
        if (price <= 0L) return CreateResult.invalidPrice();
        long now = System.currentTimeMillis();
        long clampedDuration = Math.max(1L, durationSeconds);
        AuctionListingModel listing = new AuctionListingModel(
                UUID.randomUUID().toString(),
                seller.getUuid().toString(),
                seller.getUsername() == null ? "" : seller.getUsername(),
                stack.getItemId(),
                stack.getQuantity(),
                stack.getDurability(),
                stack.getMaxDurability(),
                stack.getMetadata() == null ? null : stack.getMetadata().toJson(),
                price,
                listingCost,
                now,
                now + clampedDuration * 1000L
        );
        data.getListings().add(listing);
        save();
        return CreateResult.created(listing);
    }

    public synchronized boolean markSold(@Nonnull AuctionListingModel listing,
                                         @Nonnull PlayerRef buyer,
                                         long now) {
        if (!listing.isActive(now)) {
            expireListings(now);
            return false;
        }
        listing.setStatus("sold");
        listing.setBuyerUuid(buyer.getUuid().toString());
        listing.setBuyerName(buyer.getUsername() == null ? "" : buyer.getUsername());
        listing.setSoldAt(now);
        save();
        return true;
    }

    public synchronized boolean cancel(@Nonnull String id, @Nonnull UUID sellerUuid) {
        AuctionListingModel listing = getListing(id);
        long now = System.currentTimeMillis();
        if (listing == null || !listing.isActive(now)) return false;
        if (!sellerUuid.toString().equalsIgnoreCase(listing.getSellerUuid())) return false;
        listing.setStatus("cancelled");
        save();
        return true;
    }

    public synchronized boolean markClaimed(@Nonnull String id, @Nonnull UUID sellerUuid) {
        AuctionListingModel listing = getListing(id);
        if (listing == null) return false;
        if (!sellerUuid.toString().equalsIgnoreCase(listing.getSellerUuid())) return false;
        String status = listing.getStatus();
        if (!"expired".equals(status) && !"cancelled".equals(status)) return false;
        listing.setStatus("claimed");
        save();
        return true;
    }

    public int resolveMaxListings(@Nonnull Object sender) {
        if (CommandPermissionUtil.hasPermission(sender, LISTING_LIMIT_PERMISSION_PREFIX + "*")
                || CommandPermissionUtil.hasPermission(sender, LISTING_LIMIT_PERMISSION_PREFIX + "unlimited")) {
            return -1;
        }
        int max = config.getAuctionHouseMaxListingsPerPlayer();
        for (int i = 1; i <= MAX_LISTING_PERMISSION_SCAN; i++) {
            if (CommandPermissionUtil.hasPermission(sender, LISTING_LIMIT_PERMISSION_PREFIX + i)) {
                max = i;
            }
        }
        return max;
    }

    public long resolveMaxDurationSeconds(@Nonnull Object sender) {
        if (CommandPermissionUtil.hasPermission(sender, DURATION_PERMISSION_PREFIX + "*")
                || CommandPermissionUtil.hasPermission(sender, DURATION_PERMISSION_PREFIX + "unlimited")) {
            return 315360000L;
        }
        long maxSeconds = config.getAuctionHouseMaxListingSeconds();
        for (int hours = 1; hours <= MAX_DURATION_PERMISSION_HOURS_SCAN; hours++) {
            if (CommandPermissionUtil.hasPermission(sender, DURATION_PERMISSION_PREFIX + hours)) {
                maxSeconds = Math.max(maxSeconds, hours * 3600L);
            }
        }
        return Math.max(1L, maxSeconds);
    }

    @Nonnull
    public synchronized List<ShopNpcModel> getNpcs() {
        List<ShopNpcModel> out = new ArrayList<>();
        for (AuctionNpcModel npc : data.getNpcs()) {
            if (npc != null) {
                out.add(toShopNpcModel(npc));
            }
        }
        return out;
    }

    public synchronized void saveNpcs(@Nonnull List<ShopNpcModel> npcs) {
        data.setNpcs(toAuctionNpcModels(npcs));
        save();
    }

    @Nonnull
    public synchronized ShopModel toNpcShopModel() {
        ShopModel shop = new ShopModel(AUCTION_SHOP_NAME);
        shop.setDisplayName("Auction House");
        shop.setUsePermission("hyessentialsx.auctionhouse.use");
        shop.setNpcRole(config.getAuctionHouseNpcRole());
        shop.setNpcs(getNpcs());
        return shop;
    }

    @Nullable
    public synchronized ShopNpcModel findNpcById(@Nonnull String npcId) {
        for (ShopNpcModel npc : getNpcs()) {
            if (npc != null && npc.getNpcId().equalsIgnoreCase(npcId)) {
                return npc;
            }
        }
        return null;
    }

    public synchronized void updateNpcsFromShop(@Nonnull ShopModel shop) {
        data.setNpcs(toAuctionNpcModels(shop.getNpcs()));
        save();
    }

    @Nonnull
    private List<AuctionNpcModel> toAuctionNpcModels(@Nonnull List<ShopNpcModel> npcs) {
        List<AuctionNpcModel> out = new ArrayList<>();
        for (ShopNpcModel npc : npcs) {
            if (npc == null) continue;
            npc.setShopName(AUCTION_SHOP_NAME);
            out.add(new AuctionNpcModel(npc));
        }
        return out;
    }

    @Nonnull
    private ShopNpcModel toShopNpcModel(@Nonnull AuctionNpcModel npc) {
        ShopNpcModel model = new ShopNpcModel();
        model.setNpcId(npc.getNpcId());
        model.setPosX(npc.getPosX());
        model.setPosY(npc.getPosY());
        model.setPosZ(npc.getPosZ());
        model.setWorldId(npc.getWorldId());
        model.setShopName(AUCTION_SHOP_NAME);
        model.setSpawnerUuid(npc.getSpawnerUuid());
        model.setSpawnerName(npc.getSpawnerName());
        model.setRoleName(npc.getRoleName());
        model.setSpawnedTime(npc.getSpawnedTime());
        return model;
    }

    private boolean expireListings(long now) {
        boolean changed = false;
        for (AuctionListingModel listing : data.getListings()) {
            if (listing != null && listing.isExpired(now)) {
                listing.setStatus("expired");
                changed = true;
            }
        }
        if (changed) {
            save();
        }
        return changed;
    }

    public record CreateResult(@Nonnull String status, @Nullable AuctionListingModel listing) {
        public static CreateResult created(@Nonnull AuctionListingModel listing) {
            return new CreateResult("created", listing);
        }

        public static CreateResult disabled() {
            return new CreateResult("disabled", null);
        }

        public static CreateResult noItem() {
            return new CreateResult("no_item", null);
        }

        public static CreateResult invalidPrice() {
            return new CreateResult("invalid_price", null);
        }
    }
}
