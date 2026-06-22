package xyz.thelegacyvoyage.hyessentialsx.managers;

import xyz.thelegacyvoyage.hyessentialsx.models.KitModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class KitManager {

    private final StorageManager storage;

    public KitManager(@Nonnull StorageManager storage) {
        this.storage = storage;
    }

    public void setKit(@Nonnull KitModel kit) {
        storage.setKit(kit.getName(), kit);
    }

    @Nullable
    public KitModel getKit(@Nonnull String name) {
        return storage.getKit(name);
    }

    public boolean deleteKit(@Nonnull String name) {
        return storage.deleteKit(name);
    }

    @Nonnull
    public List<String> listKits() {
        Map<String, KitModel> kits = storage.getKits();
        return new ArrayList<>(kits.keySet());
    }

    public long getRemainingCooldownSeconds(@Nonnull UUID playerId, @Nonnull KitModel kit) {
        if (kit.getCooldownSeconds() <= 0) return 0L;
        PlayerDataModel data = storage.getPlayerData(playerId);
        Long lastUsed = data.getKitCooldowns().get(kit.getName().toLowerCase());
        if (lastUsed == null) return 0L;
        long now = System.currentTimeMillis();
        long elapsed = (now - lastUsed) / 1000L;
        long remaining = kit.getCooldownSeconds() - elapsed;
        return Math.max(0L, remaining);
    }

    public void markUsed(@Nonnull UUID playerId, @Nonnull KitModel kit) {
        if (kit.getCooldownSeconds() <= 0) return;
        PlayerDataModel data = storage.getPlayerData(playerId);
        data.getKitCooldowns().put(kit.getName().toLowerCase(), System.currentTimeMillis());
        storage.savePlayerDataAsync(playerId, data);
    }
}
