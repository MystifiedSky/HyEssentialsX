package xyz.thelegacyvoyage.hyessentialsx.managers;

import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopTradeModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ShopManager {

    public static final String DEFAULT_USE_PERMISSION = "hyessentialsx.adminshop.use";
    public static final String DEFAULT_EDIT_PERMISSION = "hyessentialsx.adminshop.edit";
    public static final String LEGACY_USE_PERMISSION = "hyessentialsx.shop.use";
    public static final String LEGACY_EDIT_PERMISSION = "hyessentialsx.shop.edit";
    public static final String DEFAULT_PLAYER_USE_PERMISSION = "hyessentialsx.playershop.use";
    public static final String DEFAULT_NPC_ROLE = "Klops_Merchant";
    private static final int DEFAULT_STOCK_RESET_DAYS = 0;

    private final StorageManager storage;
    private final ConfigManager config;

    public ShopManager(@Nonnull StorageManager storage, @Nonnull ConfigManager config) {
        this.storage = storage;
        this.config = config;
    }

    @Nonnull
    public StorageManager getStorage() {
        return storage;
    }

    @Nonnull
    public List<String> listShops() {
        List<String> names = new ArrayList<>(storage.getShops().keySet());
        names.sort(Comparator.naturalOrder());
        return names;
    }

    @Nonnull
    public List<String> listAdminShops() {
        List<String> names = new ArrayList<>();
        for (var entry : storage.getShops().entrySet()) {
            ShopModel shop = entry.getValue();
            if (shop != null && !shop.isPlayerShop()) {
                names.add(entry.getKey());
            }
        }
        names.sort(Comparator.naturalOrder());
        return names;
    }

    @Nonnull
    public List<String> listPlayerShops() {
        List<String> names = new ArrayList<>();
        for (var entry : storage.getShops().entrySet()) {
            ShopModel shop = entry.getValue();
            if (shop != null && shop.isPlayerShop()) {
                names.add(entry.getKey());
            }
        }
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

    @Nullable
    public ShopModel createPlayerShop(@Nonnull String name, @Nonnull String ownerUuid) {
        String trimmed = name.trim();
        if (trimmed.isBlank()) return null;
        String key = trimmed.toLowerCase();
        if (storage.getShop(key) != null) {
            return null;
        }
        ShopModel model = new ShopModel(key);
        model.setDisplayName(trimmed);
        model.setPlayerShop(true);
        model.setOwnerUuid(ownerUuid);
        model.setUsePermission(DEFAULT_PLAYER_USE_PERMISSION);
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

    public boolean renameShop(@Nonnull String oldName, @Nonnull String newName) {
        String oldKey = oldName.trim().toLowerCase();
        String newKey = newName.trim().toLowerCase();
        if (oldKey.isBlank() || newKey.isBlank()) {
            return false;
        }
        ShopModel shop = storage.getShop(oldKey);
        if (shop == null) {
            return false;
        }
        if (!oldKey.equals(newKey) && storage.getShop(newKey) != null) {
            return false;
        }
        shop.setName(newKey);
        shop.setDisplayName(newName.trim());
        for (var npc : shop.getNpcs()) {
            if (npc != null) {
                npc.setShopName(newKey);
            }
        }
        normalize(shop);
        return storage.renameShop(oldKey, newKey, shop);
    }

    private void normalize(@Nonnull ShopModel shop) {
        if (shop.getDisplayName().isBlank()) {
            shop.setDisplayName(shop.getName());
        }
        if (shop.getUsePermission().isBlank()) {
            shop.setUsePermission(shop.isPlayerShop() ? DEFAULT_PLAYER_USE_PERMISSION : DEFAULT_USE_PERMISSION);
        } else if (!shop.isPlayerShop() && shop.getUsePermission().equalsIgnoreCase(LEGACY_USE_PERMISSION)) {
            shop.setUsePermission(DEFAULT_USE_PERMISSION);
        }
        if (shop.getEditPermission().isBlank()) {
            shop.setEditPermission(DEFAULT_EDIT_PERMISSION);
        } else if (!shop.isPlayerShop() && shop.getEditPermission().equalsIgnoreCase(LEGACY_EDIT_PERMISSION)) {
            shop.setEditPermission(DEFAULT_EDIT_PERMISSION);
        }
        if (shop.getTrades() == null) {
            shop.setTrades(new java.util.ArrayList<>());
        }
        if (shop.getNpcs() == null) {
            shop.setNpcs(new java.util.ArrayList<>());
        }
        if (shop.getEditors() == null) {
            shop.setEditors(new java.util.ArrayList<>());
        }
        if (shop.getChests() == null) {
            shop.setChests(new java.util.ArrayList<>());
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
        normalizeMoneyStockScale(shop);
        if (shop.getMoneyStockLimit() > 0 && shop.getMoneyStockCurrent() > shop.getMoneyStockLimit()) {
            shop.setMoneyStockCurrent(shop.getMoneyStockLimit());
        }
        for (ShopTradeModel trade : shop.getTrades()) {
            normalizeTradeMoneyScale(trade);
        }
    }

    private void normalizeMoneyStockScale(@Nonnull ShopModel shop) {
        int targetScale = config.getEconomyDecimalPlaces();
        Integer storedScale = shop.getMoneyStockScale();
        if (storedScale != null && storedScale == targetScale) {
            return;
        }
        int sourceScale = storedScale == null ? 0 : Math.max(0, storedScale);
        shop.setMoneyStockLimit(rescale(shop.getMoneyStockLimit(), sourceScale, targetScale));
        shop.setMoneyStockCurrent(rescale(shop.getMoneyStockCurrent(), sourceScale, targetScale));
        shop.setMoneyStockScale(targetScale);
    }

    private void normalizeTradeMoneyScale(@Nonnull ShopTradeModel trade) {
        int targetScale = config.getEconomyDecimalPlaces();
        Integer storedScale = trade.getMoneyScale();
        if (storedScale != null && storedScale == targetScale) {
            return;
        }
        int sourceScale = storedScale == null ? 0 : Math.max(0, storedScale);
        trade.setMoneyCost(rescale(trade.getMoneyCost(), sourceScale, targetScale));
        trade.setMoneyScale(targetScale);
    }

    private static long rescale(long value, int sourceScale, int targetScale) {
        if (sourceScale == targetScale) {
            return value;
        }
        if (sourceScale < targetScale) {
            long factor = pow10(targetScale - sourceScale);
            try {
                return Math.multiplyExact(value, factor);
            } catch (ArithmeticException ignored) {
                return Long.MAX_VALUE;
            }
        }
        long factor = pow10(sourceScale - targetScale);
        return factor <= 0L ? value : value / factor;
    }

    private static long pow10(int exponent) {
        long value = 1L;
        for (int i = 0; i < exponent; i++) {
            value *= 10L;
        }
        return value;
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

