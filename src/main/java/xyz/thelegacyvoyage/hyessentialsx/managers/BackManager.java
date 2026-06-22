package xyz.thelegacyvoyage.hyessentialsx.managers;

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

    public void recordLocation(@Nonnull UUID playerId,
                               @Nonnull String worldName,
                               double x, double y, double z,
                               float yaw, float pitch) {
        backPoints.put(playerId, new BackPoint(worldName, x, y, z, yaw, pitch, System.currentTimeMillis()));
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
        return backPoints.get(playerId);
    }

    /** Consume (returns and clears). */
    @Nullable
    public BackPoint consume(@Nonnull UUID playerId) {
        return backPoints.remove(playerId);
    }

    public void clear(@Nonnull UUID playerId) {
        backPoints.remove(playerId);
    }

    public void onPlayerQuit(@Nonnull UUID playerId) {
        backPoints.remove(playerId);
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
