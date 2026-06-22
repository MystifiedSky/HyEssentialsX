package xyz.thelegacyvoyage.hyessentialsx.models;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

public final class ShopTradeModel {

    private String id;
    private List<ShopItemModel> costItems = new ArrayList<>();
    private List<ShopItemModel> rewardItems = new ArrayList<>();
    private long moneyCost;
    private boolean enabled = true;
    private boolean sellTrade;
    private int stockLimit;
    private int stockCurrent;

    public ShopTradeModel() {
    }

    public ShopTradeModel(@Nonnull String id) {
        this.id = id;
    }

    @Nonnull
    public String getId() {
        return id == null ? "" : id;
    }

    public void setId(@Nonnull String id) {
        this.id = id;
    }

    @Nonnull
    public List<ShopItemModel> getCostItems() {
        if (costItems == null) {
            costItems = new ArrayList<>();
        }
        return costItems;
    }

    public void setCostItems(@Nonnull List<ShopItemModel> costItems) {
        this.costItems = costItems;
    }

    @Nonnull
    public List<ShopItemModel> getRewardItems() {
        if (rewardItems == null) {
            rewardItems = new ArrayList<>();
        }
        return rewardItems;
    }

    public void setRewardItems(@Nonnull List<ShopItemModel> rewardItems) {
        this.rewardItems = rewardItems;
    }

    public long getMoneyCost() {
        return Math.max(0L, moneyCost);
    }

    public void setMoneyCost(long moneyCost) {
        this.moneyCost = Math.max(0L, moneyCost);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSellTrade() {
        return sellTrade;
    }

    public void setSellTrade(boolean sellTrade) {
        this.sellTrade = sellTrade;
    }

    public boolean isMoneyTrade() {
        return getMoneyCost() > 0L;
    }

    public int getStockLimit() {
        return Math.max(0, stockLimit);
    }

    public void setStockLimit(int stockLimit) {
        this.stockLimit = Math.max(0, stockLimit);
    }

    public int getStockCurrent() {
        return Math.max(0, stockCurrent);
    }

    public void setStockCurrent(int stockCurrent) {
        this.stockCurrent = Math.max(0, stockCurrent);
    }

    public boolean hasStockLimit() {
        return getStockLimit() > 0;
    }
}
