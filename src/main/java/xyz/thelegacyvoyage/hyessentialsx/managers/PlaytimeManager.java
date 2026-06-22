package xyz.thelegacyvoyage.hyessentialsx.managers;

import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlaytimeManager {

    private final StorageManager storage;
    private final Map<UUID, Long> joinTimes = new ConcurrentHashMap<>();

    public PlaytimeManager(@Nonnull StorageManager storage) {
        this.storage = storage;
    }

    public void onJoin(@Nonnull UUID uuid) {
        long now = System.currentTimeMillis();
        joinTimes.put(uuid, now);
        PlayerDataModel data = storage.getPlayerData(uuid);
        data.setLastJoinAt(now);
        storage.savePlayerDataAsync(uuid, data);
    }

    public void onQuit(@Nonnull UUID uuid) {
        long now = System.currentTimeMillis();
        Long removed = joinTimes.remove(uuid);
        long joinAt = removed != null ? removed : 0L;
        if (joinAt <= 0L) {
            PlayerDataModel data = storage.getPlayerData(uuid);
            joinAt = data.getLastJoinAt();
        }
        if (joinAt > 0L) {
            long sessionSeconds = Math.max(0L, (now - joinAt) / 1000L);
            PlayerDataModel data = storage.getPlayerData(uuid);
            data.setPlaytimeSeconds(data.getPlaytimeSeconds() + sessionSeconds);
            data.setLastJoinAt(0L);
            storage.savePlayerDataAsync(uuid, data);
        }
    }

    public long getPlaytimeSeconds(@Nonnull UUID uuid) {
        PlayerDataModel data = storage.getPlayerData(uuid);
        long total = data.getPlaytimeSeconds();
        Long joinAt = joinTimes.get(uuid);
        if (joinAt != null && joinAt > 0L) {
            total += Math.max(0L, (System.currentTimeMillis() - joinAt) / 1000L);
        }
        return total;
    }
}
