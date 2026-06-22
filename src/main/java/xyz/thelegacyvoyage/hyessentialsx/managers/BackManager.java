package xyz.thelegacyvoyage.hyessentialsx.managers;

import xyz.thelegacyvoyage.hyessentialsx.models.BackPointModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores a player's last "return point" (usually death location) for /back.
 * In-memory only (not persisted).
 */
public final class BackManager {

    private final ConcurrentHashMap<UUID, BackPoint> backPoints = new ConcurrentHashMap<>();
    private final StorageManager storage;

    public BackManager(@Nonnull StorageManager storage) {
        this.storage = storage;
    }

    public void recordLocation(@Nonnull UUID playerId,
                               @Nonnull String worldName,
                               double x, double y, double z,
                               float yaw, float pitch) {
        BackPoint point = new BackPoint(worldName, x, y, z, yaw, pitch, System.currentTimeMillis());
        backPoints.put(playerId, point);
        persist(playerId, point);
    }

    public void recordDeath(@Nonnull UUID playerId,
                            @Nonnull String worldName,
                            double x, double y, double z,
                            float yaw, float pitch) {
        recordLocation(playerId, worldName, x, y, z, yaw, pitch);
    }

    /** Peek without clearing. */
    @Nullable
    public BackPoint peek(@Nonnull UUID playerId) {
        BackPoint cached = backPoints.get(playerId);
        if (cached != null) return cached;
        BackPointModel model = storage.getPlayerData(playerId).getBack();
        if (model == null) return null;
        BackPoint point = new BackPoint(
                model.getWorldName(),
                model.getX(), model.getY(), model.getZ(),
                model.getYaw(), model.getPitch(),
                model.getRecordedAt()
        );
        backPoints.put(playerId, point);
        return point;
    }

    /** Consume (returns and clears). */
    @Nullable
    public BackPoint consume(@Nonnull UUID playerId) {
        BackPoint removed = backPoints.remove(playerId);
        if (removed != null) {
            clearPersisted(playerId);
            return removed;
        }
        BackPointModel model = storage.getPlayerData(playerId).getBack();
        if (model != null) {
            clearPersisted(playerId);
            return new BackPoint(
                    model.getWorldName(),
                    model.getX(), model.getY(), model.getZ(),
                    model.getYaw(), model.getPitch(),
                    model.getRecordedAt()
            );
        }
        return null;
    }

    public void clear(@Nonnull UUID playerId) {
        backPoints.remove(playerId);
        clearPersisted(playerId);
    }

    public void onPlayerQuit(@Nonnull UUID playerId) {
        backPoints.remove(playerId);
    }

    private void persist(@Nonnull UUID playerId, @Nonnull BackPoint point) {
        PlayerDataModel data = storage.getPlayerData(playerId);
        data.setBack(new BackPointModel(
                point.worldName,
                point.x, point.y, point.z,
                point.yaw, point.pitch,
                point.recordedAt
        ));
        storage.savePlayerDataAsync(playerId, data);
    }

    private void clearPersisted(@Nonnull UUID playerId) {
        PlayerDataModel data = storage.getPlayerData(playerId);
        if (data.getBack() != null) {
            data.setBack(null);
            storage.savePlayerDataAsync(playerId, data);
        }
    }

    public static final class BackPoint {
        private final String worldName;
        private final double x, y, z;
        private final float yaw, pitch;
        private final long recordedAt;

        private BackPoint(String worldName, double x, double y, double z, float yaw, float pitch, long recordedAt) {
            this.worldName = worldName;
            this.x = x; this.y = y; this.z = z;
            this.yaw = yaw; this.pitch = pitch;
            this.recordedAt = recordedAt;
        }

        public String getWorldName() { return worldName; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
        public long getRecordedAt() { return recordedAt; }
    }
}

