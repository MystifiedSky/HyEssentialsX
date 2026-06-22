package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nullable;

public final class KitItemModel {

    private short slot;
    private String itemId;
    private int quantity;
    private double durability;
    private double maxDurability;
    private String metadataJson;

    @SuppressWarnings("unused")
    public KitItemModel() {}

    public KitItemModel(short slot,
                        String itemId,
                        int quantity,
                        double durability,
                        double maxDurability,
                        @Nullable String metadataJson) {
        this.slot = slot;
        this.itemId = itemId;
        this.quantity = quantity;
        this.durability = durability;
        this.maxDurability = maxDurability;
        this.metadataJson = metadataJson;
    }

    public short getSlot() {
        return slot;
    }

    public String getItemId() {
        return itemId;
    }

    public int getQuantity() {
        return quantity;
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
}

