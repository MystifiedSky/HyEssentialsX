package xyz.thelegacyvoyage.hyessentialsx.models;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

public final class ShopModel {

    private String name;
    private String displayName;
    private boolean open = true;
    private String usePermission = "hyessentialsx.adminshop.use";
    private String editPermission = "hyessentialsx.adminshop.edit";
    private List<ShopTradeModel> trades = new ArrayList<>();
    private List<ShopNpcModel> npcs = new ArrayList<>();
    private String npcRole;
    private int stockResetDays;
    private long stockResetAt;
    private int moneyStockLimit;
    private long moneyStockCurrent;
    private boolean playerShop;
    private String ownerUuid;
    private List<String> editors = new ArrayList<>();
    private List<ShopChestModel> chests = new ArrayList<>();

    public ShopModel() {
    }

    public ShopModel(@Nonnull String name) {
        this.name = name;
        this.displayName = name;
    }

    @Nonnull
    public String getName() {
        return name == null ? "" : name;
    }

    public void setName(@Nonnull String name) {
        this.name = name;
    }

    @Nonnull
    public String getDisplayName() {
        if (displayName == null || displayName.isBlank()) {
            return getName();
        }
        return displayName;
    }

    public void setDisplayName(@Nonnull String displayName) {
        this.displayName = displayName;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    @Nonnull
    public String getUsePermission() {
        return usePermission == null ? "" : usePermission;
    }

    public void setUsePermission(@Nonnull String usePermission) {
        this.usePermission = usePermission;
    }

    @Nonnull
    public String getEditPermission() {
        return editPermission == null ? "" : editPermission;
    }

    public void setEditPermission(@Nonnull String editPermission) {
        this.editPermission = editPermission;
    }

    @Nonnull
    public List<ShopTradeModel> getTrades() {
        if (trades == null) {
            trades = new ArrayList<>();
        }
        return trades;
    }

    public void setTrades(@Nonnull List<ShopTradeModel> trades) {
        this.trades = trades;
    }

    @Nonnull
    public List<ShopNpcModel> getNpcs() {
        if (npcs == null) {
            npcs = new ArrayList<>();
        }
        return npcs;
    }

    public void setNpcs(@Nonnull List<ShopNpcModel> npcs) {
        this.npcs = npcs;
    }

    @Nonnull
    public String getNpcRole() {
        return npcRole == null ? "" : npcRole;
    }

    public void setNpcRole(@Nonnull String npcRole) {
        this.npcRole = npcRole;
    }

    public int getStockResetDays() {
        return Math.max(0, stockResetDays);
    }

    public void setStockResetDays(int stockResetDays) {
        this.stockResetDays = Math.max(0, stockResetDays);
    }

    public long getStockResetAt() {
        return stockResetAt;
    }

    public void setStockResetAt(long stockResetAt) {
        this.stockResetAt = Math.max(0L, stockResetAt);
    }

    public int getMoneyStockLimit() {
        return Math.max(0, moneyStockLimit);
    }

    public void setMoneyStockLimit(int moneyStockLimit) {
        this.moneyStockLimit = Math.max(0, moneyStockLimit);
    }

    public long getMoneyStockCurrent() {
        return Math.max(0L, moneyStockCurrent);
    }

    public void setMoneyStockCurrent(long moneyStockCurrent) {
        this.moneyStockCurrent = Math.max(0L, moneyStockCurrent);
    }

    public boolean isPlayerShop() {
        return playerShop;
    }

    public void setPlayerShop(boolean playerShop) {
        this.playerShop = playerShop;
    }

    @Nonnull
    public String getOwnerUuid() {
        return ownerUuid == null ? "" : ownerUuid;
    }

    public void setOwnerUuid(@Nonnull String ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    @Nonnull
    public List<String> getEditors() {
        if (editors == null) {
            editors = new ArrayList<>();
        }
        return editors;
    }

    public void setEditors(@Nonnull List<String> editors) {
        this.editors = editors;
    }

    @Nonnull
    public List<ShopChestModel> getChests() {
        if (chests == null) {
            chests = new ArrayList<>();
        }
        return chests;
    }

    public void setChests(@Nonnull List<ShopChestModel> chests) {
        this.chests = chests;
    }
}
