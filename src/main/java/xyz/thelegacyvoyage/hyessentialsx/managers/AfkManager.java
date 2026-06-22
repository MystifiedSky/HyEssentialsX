package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class AfkManager {

    private static final double MOVE_EPSILON_SQ = 0.0001;

    private final ConfigManager config;
    private final ScheduledExecutorService scheduler;
    private final Map<UUID, Boolean> afkStatus = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> manualAfk = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Map<UUID, Vector3d> lastPositions = new ConcurrentHashMap<>();

    public AfkManager(@Nonnull ConfigManager config) {
        this.config = config;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HyEssentialsX-AFK");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::tick, 5, 5, TimeUnit.SECONDS);
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    public void handleConnect(@Nonnull PlayerRef player) {
        UUID uuid = player.getUuid();
        lastActivity.put(uuid, System.currentTimeMillis());
        Vector3d pos = safePosition(player);
        if (pos != null) lastPositions.put(uuid, pos);
        afkStatus.remove(uuid);
        manualAfk.remove(uuid);
    }

    public void handleDisconnect(@Nonnull PlayerRef player) {
        UUID uuid = player.getUuid();
        lastActivity.remove(uuid);
        lastPositions.remove(uuid);
        afkStatus.remove(uuid);
        manualAfk.remove(uuid);
    }

    public void handleActivity(@Nonnull PlayerRef player) {
        UUID uuid = player.getUuid();
        lastActivity.put(uuid, System.currentTimeMillis());
        if (isAfk(uuid)) {
            clearAfk(player, config.isAfkAnnounceOnReturn());
        }
    }

    public boolean isAfk(@Nonnull UUID uuid) {
        return afkStatus.getOrDefault(uuid, false);
    }

    public void toggleAfk(@Nonnull PlayerRef player) {
        if (!config.isAfkEnabled()) return;
        UUID uuid = player.getUuid();
        if (isAfk(uuid)) {
            clearAfk(player, true);
        } else {
            setAfk(player, true, true);
        }
    }

    private void tick() {
        if (!config.isAfkEnabled()) return;
        long now = System.currentTimeMillis();
        int timeoutMs = Math.max(0, config.getAfkTimeoutSeconds()) * 1000;

        for (PlayerRef player : Universe.get().getPlayers()) {
            if (player == null) continue;
            var worldId = player.getWorldUuid();
            if (worldId == null) continue;
            var world = Universe.get().getWorld(worldId);
            if (world == null) continue;
            world.execute(() -> handlePlayerTick(player, now, timeoutMs));
        }
    }

    private void handlePlayerTick(@Nonnull PlayerRef player, long now, int timeoutMs) {
        UUID uuid = player.getUuid();
        Vector3d pos = safePosition(player);
        if (pos != null) {
            Vector3d last = lastPositions.get(uuid);
            if (last == null || moved(last, pos)) {
                lastPositions.put(uuid, pos);
                lastActivity.put(uuid, now);
                if (isAfk(uuid)) {
                    clearAfk(player, config.isAfkAnnounceOnReturn());
                }
                return;
            }
        }

        if (timeoutMs > 0 && !isAfk(uuid)) {
            long lastSeen = lastActivity.getOrDefault(uuid, now);
            if (now - lastSeen >= timeoutMs) {
                setAfk(player, false, config.isAfkAnnounceOnAuto());
            }
        }
    }

    private void setAfk(@Nonnull PlayerRef player, boolean manual, boolean announce) {
        UUID uuid = player.getUuid();
        afkStatus.put(uuid, true);
        manualAfk.put(uuid, manual);
        lastActivity.put(uuid, System.currentTimeMillis());
        if (announce && (manual ? config.isAfkAnnounceOnManual() : config.isAfkAnnounceOnAuto())) {
            broadcast(config.getAfkMessage().replace("{player}", player.getUsername()));
        }
    }

    private void clearAfk(@Nonnull PlayerRef player, boolean announce) {
        UUID uuid = player.getUuid();
        afkStatus.remove(uuid);
        manualAfk.remove(uuid);
        lastActivity.put(uuid, System.currentTimeMillis());
        if (announce) {
            broadcast(config.getAfkBackMessage().replace("{player}", player.getUsername()));
        }
    }

    private void broadcast(@Nonnull String message) {
        for (PlayerRef target : Universe.get().getPlayers()) {
            Messages.send(target, message);
        }
    }

    private Vector3d safePosition(@Nonnull PlayerRef player) {
        try {
            Transform transform = player.getTransform();
            if (transform == null) return null;
            Vector3d pos = transform.getPosition();
            if (pos == null) return null;
            return new Vector3d(pos.getX(), pos.getY(), pos.getZ());
        } catch (Throwable t) {
            Log.warn("AFK position check failed: " + t.getMessage());
            return null;
        }
    }

    private boolean moved(@Nonnull Vector3d last, @Nonnull Vector3d current) {
        double dx = current.getX() - last.getX();
        double dy = current.getY() - last.getY();
        double dz = current.getZ() - last.getZ();
        return (dx * dx + dy * dy + dz * dz) > MOVE_EPSILON_SQ;
    }
}
