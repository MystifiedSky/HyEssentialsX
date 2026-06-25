package xyz.thelegacyvoyage.hyessentialsx.models;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
    private long moneyStockLimit;
    private long moneyStockCurrent;
    private Integer moneyStockScale;
    private boolean playerShop;
    private String ownerUuid;
    private String playerWarpName;
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

    public long getMoneyStockLimit() {
        return Math.max(0L, moneyStockLimit);
    }

    public void setMoneyStockLimit(long moneyStockLimit) {
        this.moneyStockLimit = Math.max(0L, moneyStockLimit);
    }

    public long getMoneyStockCurrent() {
        return Math.max(0L, moneyStockCurrent);
    }

    public void setMoneyStockCurrent(long moneyStockCurrent) {
        this.moneyStockCurrent = Math.max(0L, moneyStockCurrent);
    }

    @Nullable
    public Integer getMoneyStockScale() {
        return moneyStockScale;
    }

    public void setMoneyStockScale(@Nullable Integer moneyStockScale) {
        this.moneyStockScale = moneyStockScale;
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
    public String getPlayerWarpName() {
        return playerWarpName == null ? "" : playerWarpName;
    }

    public void setPlayerWarpName(@Nullable String playerWarpName) {
        this.playerWarpName = playerWarpName == null ? "" : playerWarpName.trim().toLowerCase();
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

