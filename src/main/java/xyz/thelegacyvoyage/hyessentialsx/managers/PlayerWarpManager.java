package xyz.thelegacyvoyage.hyessentialsx.managers;

import xyz.thelegacyvoyage.hyessentialsx.models.PlayerWarpModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PlayerWarpManager {

    private final StorageManager storage;

    public PlayerWarpManager(@Nonnull StorageManager storage) {
        this.storage = storage;
    }

    public void setWarp(@Nonnull UUID ownerId, @Nonnull PlayerWarpModel warp) {
        storage.setPlayerWarp(ownerId, warp);
    }

    @Nullable
    public PlayerWarpModel getOwnedWarp(@Nonnull UUID ownerId, @Nonnull String name) {
        return storage.getPlayerWarp(ownerId, name);
    }

    @Nullable
    public PlayerWarpModel findVisibleWarp(@Nonnull String name, @Nullable UUID viewerId, boolean admin) {
        String normalized = PlayerWarpModel.normalizeName(name);
        for (PlayerWarpModel warp : storage.listPlayerWarps()) {
            if (!warp.getName().equals(normalized)) continue;
            if (canView(warp, viewerId, admin)) {
                return warp;
            }
        }
        return null;
    }

    @Nullable
    public PlayerWarpModel findAnyWarp(@Nonnull String name) {
        String normalized = PlayerWarpModel.normalizeName(name);
        for (PlayerWarpModel warp : storage.listPlayerWarps()) {
            if (warp.getName().equals(normalized)) {
                return warp;
            }
        }
        return null;
    }

    public boolean deleteWarp(@Nonnull UUID ownerId, @Nonnull String name) {
        return storage.deletePlayerWarp(ownerId, name);
    }

    public boolean renameWarp(@Nonnull UUID ownerId, @Nonnull String oldName, @Nonnull String newName) {
        String oldNormalized = PlayerWarpModel.normalizeName(oldName);
        String newNormalized = PlayerWarpModel.normalizeName(newName);
        if (oldNormalized.isBlank() || newNormalized.isBlank()) return false;
        PlayerWarpModel warp = storage.getPlayerWarp(ownerId, oldNormalized);
        if (warp == null) return false;
        if (!oldNormalized.equals(newNormalized) && findAnyWarp(newNormalized) != null) return false;
        storage.deletePlayerWarp(ownerId, oldNormalized);
        warp.setName(newNormalized);
        storage.setPlayerWarp(ownerId, warp);
        updateLinkedPlayerShops(ownerId, oldNormalized, newNormalized);
        return true;
    }

    @Nonnull
    public List<PlayerWarpModel> listVisibleWarps(@Nullable UUID viewerId, boolean admin, @Nonnull String search) {
        String normalized = search.trim().toLowerCase(Locale.ROOT);
        List<PlayerWarpModel> result = new ArrayList<>();
        for (PlayerWarpModel warp : storage.listPlayerWarps()) {
            if (!canView(warp, viewerId, admin)) continue;
            if (!normalized.isBlank()
                    && !warp.getName().contains(normalized)
                    && !warp.getOwnerName().toLowerCase(Locale.ROOT).contains(normalized)
                    && !warp.getDescription().toLowerCase(Locale.ROOT).contains(normalized)) {
                continue;
            }
            result.add(warp);
        }
        result.sort(Comparator.comparingLong(PlayerWarpModel::getVisits).reversed()
                .thenComparing(PlayerWarpModel::getName));
        return List.copyOf(result);
    }

    public int countOwnedWarps(@Nonnull UUID ownerId) {
        return storage.getPlayerWarps(ownerId).size();
    }

    public boolean isNameAvailable(@Nonnull String name) {
        String normalized = PlayerWarpModel.normalizeName(name);
        if (normalized.isBlank()) return false;
        return findAnyWarp(normalized) == null;
    }

    private boolean canView(@Nonnull PlayerWarpModel warp, @Nullable UUID viewerId, boolean admin) {
        if (admin) return true;
        if (!warp.isEnabled() || !warp.isApproved()) return false;
        if (warp.isPublicWarp()) return true;
        return viewerId != null && warp.getOwnerUuid().equals(viewerId.toString());
    }

    private void updateLinkedPlayerShops(@Nonnull UUID ownerId,
                                         @Nonnull String oldName,
                                         @Nonnull String newName) {
        String owner = ownerId.toString();
        for (xyz.thelegacyvoyage.hyessentialsx.models.ShopModel shop : storage.getShops().values()) {
            if (shop == null || !shop.isPlayerShop()) continue;
            if (!owner.equalsIgnoreCase(shop.getOwnerUuid())) continue;
            if (!oldName.equalsIgnoreCase(shop.getPlayerWarpName())) continue;
            shop.setPlayerWarpName(newName);
            storage.setShop(shop.getName(), shop);
        }
    }
}
