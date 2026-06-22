package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class AuctionListingModel {

    private String id;
    private String sellerUuid;
    private String sellerName;
    private String buyerUuid;
    private String buyerName;
    private String itemId;
    private int quantity;
    private double durability;
    private double maxDurability;
    private String metadataJson;
    private long price;
    private long listingCost;
    private long createdAt;
    private long expiresAt;
    private long soldAt;
    private String status = "active";

    public AuctionListingModel() {
    }

    public AuctionListingModel(@Nonnull String id,
                               @Nonnull String sellerUuid,
                               @Nonnull String sellerName,
                               @Nonnull String itemId,
                               int quantity,
                               double durability,
                               double maxDurability,
                               @Nullable String metadataJson,
                               long price,
                               long listingCost,
                               long createdAt,
                               long expiresAt) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.itemId = itemId;
        this.quantity = Math.max(0, quantity);
        this.durability = durability;
        this.maxDurability = maxDurability;
        this.metadataJson = metadataJson;
        this.price = Math.max(0L, price);
        this.listingCost = Math.max(0L, listingCost);
        this.createdAt = Math.max(0L, createdAt);
        this.expiresAt = Math.max(this.createdAt, expiresAt);
        this.status = "active";
    }

    @Nonnull
    public String getId() {
        return id == null ? "" : id;
    }

    @Nonnull
    public String getSellerUuid() {
        return sellerUuid == null ? "" : sellerUuid;
    }

    @Nonnull
    public String getSellerName() {
        return sellerName == null || sellerName.isBlank() ? "Unknown" : sellerName;
    }

    @Nonnull
    public String getBuyerUuid() {
        return buyerUuid == null ? "" : buyerUuid;
    }

    public void setBuyerUuid(@Nonnull String buyerUuid) {
        this.buyerUuid = buyerUuid;
    }

    @Nonnull
    public String getBuyerName() {
        return buyerName == null ? "" : buyerName;
    }

    public void setBuyerName(@Nonnull String buyerName) {
        this.buyerName = buyerName;
    }

    @Nonnull
    public String getItemId() {
        return itemId == null ? "" : itemId;
    }

    public int getQuantity() {
        return Math.max(0, quantity);
    }

    public double getDurability() {
        return durability;
    }

    public double getMaxDurability() {
        return maxDurability;
    }

    @Nullable
    public String getMetadataJson() {
        return metadataJson;
    }

    public long getPrice() {
        return Math.max(0L, price);
    }

    public long getListingCost() {
        return Math.max(0L, listingCost);
    }

    public long getCreatedAt() {
        return Math.max(0L, createdAt);
    }

    public long getExpiresAt() {
        return Math.max(0L, expiresAt);
    }

    public long getSoldAt() {
        return Math.max(0L, soldAt);
    }

    public void setSoldAt(long soldAt) {
        this.soldAt = Math.max(0L, soldAt);
    }

    @Nonnull
    public String getStatus() {
        return status == null || status.isBlank() ? "active" : status.toLowerCase(java.util.Locale.ROOT);
    }

    public void setStatus(@Nonnull String status) {
        this.status = status;
    }

    public boolean isActive(long now) {
        return "active".equals(getStatus()) && getExpiresAt() > now;
    }

    public boolean isExpired(long now) {
        return "active".equals(getStatus()) && getExpiresAt() <= now;
    }

}
