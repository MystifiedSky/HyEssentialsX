package xyz.thelegacyvoyage.hyessentialsx.managers;

import xyz.thelegacyvoyage.hyessentialsx.models.KitModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
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
        List<String> names = new ArrayList<>();
        for (KitModel kit : listKitModels()) {
            if (kit == null || kit.getName() == null) continue;
            names.add(kit.getName());
        }
        return names;
    }

    @Nonnull
    public List<KitModel> listKitModels() {
        Map<String, KitModel> kits = storage.getKits();
        List<KitModel> out = new ArrayList<>(kits.values());
        out.sort(Comparator
                .comparingInt(KitModel::getSortOrder)
                .thenComparing(k -> k.getName().toLowerCase()));
        return out;
    }

    public int nextSortOrder() {
        int max = 0;
        for (KitModel kit : storage.getKits().values()) {
            if (kit == null) continue;
            max = Math.max(max, kit.getSortOrder());
        }
        return max + 1;
    }

    public boolean reorderKit(@Nonnull String name, int oneBasedPosition) {
        if (name.isBlank()) return false;
        List<KitModel> kits = listKitModels();
        if (kits.isEmpty()) return false;

        KitModel target = null;
        for (KitModel kit : kits) {
            if (kit == null) continue;
            if (kit.getName().equalsIgnoreCase(name.trim())) {
                target = kit;
                break;
            }
        }
        if (target == null) return false;

        kits.remove(target);
        int index = Math.max(0, Math.min(kits.size(), oneBasedPosition - 1));
        kits.add(index, target);

        for (int i = 0; i < kits.size(); i++) {
            KitModel kit = kits.get(i);
            if (kit == null) continue;
            KitModel updated = new KitModel(
                    kit.getName(),
                    kit.getCooldownSeconds(),
                    kit.getMaxUses(),
                    i + 1,
                    kit.getItems()
            );
            setKit(updated);
        }
        return true;
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

    public int getRemainingUses(@Nonnull UUID playerId, @Nonnull KitModel kit) {
        int maxUses = kit.getMaxUses();
        if (maxUses <= 0) return Integer.MAX_VALUE;
        int used = getUsedCount(playerId, kit);
        return Math.max(0, maxUses - used);
    }

    public boolean hasRemainingUses(@Nonnull UUID playerId, @Nonnull KitModel kit) {
        return getRemainingUses(playerId, kit) > 0;
    }

    public int getUsedCount(@Nonnull UUID playerId, @Nonnull KitModel kit) {
        PlayerDataModel data = storage.getPlayerData(playerId);
        Integer used = data.getKitUseCounts().get(kit.getName().toLowerCase());
        return used == null ? 0 : Math.max(0, used);
    }

    public void markUsed(@Nonnull UUID playerId, @Nonnull KitModel kit) {
        PlayerDataModel data = storage.getPlayerData(playerId);
        String key = kit.getName().toLowerCase();
        if (kit.getCooldownSeconds() > 0) {
            data.getKitCooldowns().put(key, System.currentTimeMillis());
        }
        int next = Math.max(0, data.getKitUseCounts().getOrDefault(key, 0)) + 1;
        data.getKitUseCounts().put(key, next);
        storage.savePlayerDataAsync(playerId, data);
    }
}

