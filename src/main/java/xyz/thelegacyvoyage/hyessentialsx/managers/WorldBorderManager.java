package xyz.thelegacyvoyage.hyessentialsx.managers;

import org.joml.Vector3d;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldBorderManager {

    private static final long WARNING_COOLDOWN_MS = 3000L;

    private final ConfigManager config;
    private final Map<UUID, Long> lastWarningMs = new ConcurrentHashMap<>();

    public WorldBorderManager(@Nonnull ConfigManager config) {
        this.config = config;
    }

    public boolean isEnabled() {
        return config.isWorldBorderEnabled();
    }

    public void setEnabled(boolean enabled) {
        config.setWorldBorderEnabled(enabled);
    }

    public int radius() {
        return config.getWorldBorderRadius();
    }

    public int centerX() {
        return config.getWorldBorderCenterX();
    }

    public int centerZ() {
        return config.getWorldBorderCenterZ();
    }

    public void setRadius(int radius) {
        config.setWorldBorderRadius(radius);
    }

    public void setCenter(int x, int z) {
        config.setWorldBorderCenter(x, z);
    }

    public boolean isOutside(@Nonnull Vector3d position) {
        int radius = radius();
        return Math.abs(position.x() - centerX()) > radius
                || Math.abs(position.z() - centerZ()) > radius;
    }

    @Nonnull
    public Vector3d clampInside(@Nonnull Vector3d position) {
        int radius = radius();
        int padding = Math.min(config.getWorldBorderTeleportPadding(), Math.max(0, radius - 1));
        double minX = centerX() - radius + padding;
        double maxX = centerX() + radius - padding;
        double minZ = centerZ() - radius + padding;
        double maxZ = centerZ() + radius - padding;
        return new Vector3d(
                clamp(position.x(), minX, maxX),
                position.y(),
                clamp(position.z(), minZ, maxZ)
        );
    }

    public boolean shouldWarn(@Nonnull UUID playerId) {
        long now = System.currentTimeMillis();
        Long previous = lastWarningMs.get(playerId);
        if (previous != null && now - previous < WARNING_COOLDOWN_MS) {
            return false;
        }
        lastWarningMs.put(playerId, now);
        return true;
    }

    public void clearWarningState() {
        lastWarningMs.clear();
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
