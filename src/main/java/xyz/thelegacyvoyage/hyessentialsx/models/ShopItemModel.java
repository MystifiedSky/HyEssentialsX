package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;

public final class ShopItemModel {

    private String itemId;
    private int quantity;

    public ShopItemModel() {
    }

    public ShopItemModel(@Nonnull String itemId, int quantity) {
        this.itemId = itemId;
        this.quantity = quantity;
    }

    @Nonnull
    public String getItemId() {
        return itemId == null ? "" : itemId;
    }

    public void setItemId(@Nonnull String itemId) {
        this.itemId = itemId;
    }

    public int getQuantity() {
        return Math.max(0, quantity);
    }

    public void setQuantity(int quantity) {
        this.quantity = Math.max(0, quantity);
    }
}

