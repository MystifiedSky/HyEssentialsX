package xyz.thelegacyvoyage.hyessentialsx.managers;

import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ShopManager {

    public static final String DEFAULT_USE_PERMISSION = "hyessentialsx.shop.use";
    public static final String DEFAULT_EDIT_PERMISSION = "hyessentialsx.shop.edit";
    public static final String DEFAULT_NPC_ROLE = "Klops_Merchant";
    private static final int DEFAULT_STOCK_RESET_DAYS = 0;

    private final StorageManager storage;

    public ShopManager(@Nonnull StorageManager storage) {
        this.storage = storage;
    }

    @Nonnull
    public List<String> listShops() {
        List<String> names = new ArrayList<>(storage.getShops().keySet());
        names.sort(Comparator.naturalOrder());
        return names;
    }

    @Nullable
    public ShopModel getShop(@Nonnull String name) {
        if (name.isBlank()) return null;
        String key = name.trim().toLowerCase();
        ShopModel shop = storage.getShop(key);
        if (shop != null) {
            if (shop.getName().isBlank()) {
                shop.setName(key);
            }
            normalize(shop);
        }
        return shop;
    }

    @Nullable
    public ShopModel createShop(@Nonnull String name) {
        String trimmed = name.trim();
        if (trimmed.isBlank()) return null;
        String key = trimmed.toLowerCase();
        if (storage.getShop(key) != null) {
            return null;
        }
        ShopModel model = new ShopModel(key);
        model.setDisplayName(trimmed);
        normalize(model);
        storage.setShop(key, model);
        return model;
    }

    public boolean deleteShop(@Nonnull String name) {
        return storage.deleteShop(name);
    }

    public void saveShop(@Nonnull ShopModel shop) {
        String key = shop.getName().trim().toLowerCase();
        if (key.isBlank()) {
            return;
        }
        normalize(shop);
        storage.setShop(key, shop);
    }

    private void normalize(@Nonnull ShopModel shop) {
        if (shop.getDisplayName().isBlank()) {
            shop.setDisplayName(shop.getName());
        }
        if (shop.getUsePermission().isBlank()) {
            shop.setUsePermission(DEFAULT_USE_PERMISSION);
        }
        if (shop.getEditPermission().isBlank()) {
            shop.setEditPermission(DEFAULT_EDIT_PERMISSION);
        }
        if (shop.getTrades() == null) {
            shop.setTrades(new java.util.ArrayList<>());
        }
        if (shop.getNpcs() == null) {
            shop.setNpcs(new java.util.ArrayList<>());
        }
        if (shop.getNpcRole().isBlank()) {
            shop.setNpcRole(DEFAULT_NPC_ROLE);
        }
        if (shop.getStockResetDays() < 0) {
            shop.setStockResetDays(DEFAULT_STOCK_RESET_DAYS);
        }
        if (shop.getStockResetDays() > 0 && shop.getStockResetAt() <= 0L) {
            long millis = shop.getStockResetDays() * 24L * 60L * 60L * 1000L;
            shop.setStockResetAt(System.currentTimeMillis() + millis);
        }
        if (shop.getMoneyStockLimit() < 0) {
            shop.setMoneyStockLimit(0);
        }
        if (shop.getMoneyStockLimit() > 0 && shop.getMoneyStockCurrent() > shop.getMoneyStockLimit()) {
            shop.setMoneyStockCurrent(shop.getMoneyStockLimit());
        }
    }

    @Nonnull
    public List<xyz.thelegacyvoyage.hyessentialsx.models.ShopNpcModel> listAllNpcs() {
        List<xyz.thelegacyvoyage.hyessentialsx.models.ShopNpcModel> out = new ArrayList<>();
        for (ShopModel shop : storage.getShops().values()) {
            out.addAll(shop.getNpcs());
        }
        return out;
    }

    @Nullable
    public ShopModel findShopByNpcId(@Nonnull String npcId) {
        if (npcId.isBlank()) return null;
        for (ShopModel shop : storage.getShops().values()) {
            for (var npc : shop.getNpcs()) {
                if (npcId.equalsIgnoreCase(npc.getNpcId())) {
                    normalize(shop);
                    return shop;
                }
            }
        }
        return null;
    }

    public boolean removeNpc(@Nonnull String npcId) {
        if (npcId.isBlank()) return false;
        for (ShopModel shop : storage.getShops().values()) {
            boolean removed = shop.getNpcs().removeIf(npc -> npcId.equalsIgnoreCase(npc.getNpcId()));
            if (removed) {
                saveShop(shop);
                return true;
            }
        }
        return false;
    }
}
