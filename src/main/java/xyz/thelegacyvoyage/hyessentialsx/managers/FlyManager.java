package xyz.thelegacyvoyage.hyessentialsx.managers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.SavedMovementStates;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

public final class FlyManager {

    private final ConcurrentHashMap<UUID, Boolean> enabled = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> pendingApply = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Float> flySpeedMultiplier = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Float> baseHorizontalFlySpeed = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Float> baseVerticalFlySpeed = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, java.util.Set<Integer>> sentExpiryWarnings = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;
    private StorageManager storage;
    private ConfigManager config;

    public void configure(@Nonnull StorageManager storage, @Nonnull ConfigManager config) {
        this.storage = storage;
        this.config = config;
    }

    public void start() {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HyEssentialsX-FlyExpiry");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::tickExpiriesSafe, 1L, 1L, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        sentExpiryWarnings.clear();
    }

    public boolean isEnabled(@Nonnull UUID playerId) {
        return enabled.containsKey(playerId);
    }

    public boolean setEnabled(@Nonnull UUID playerId, boolean value) {
        if (value) {
            return enabled.put(playerId, Boolean.TRUE) == null;
        }
        clearExpiry(playerId);
        return enabled.remove(playerId) != null;
    }

    public boolean toggle(@Nonnull UUID playerId) {
        if (enabled.remove(playerId) != null) return false;
        enabled.put(playerId, Boolean.TRUE);
        return true;
    }

    public void clear(@Nonnull UUID playerId) {
        enabled.remove(playerId);
        pendingApply.remove(playerId);
        flySpeedMultiplier.remove(playerId);
        baseHorizontalFlySpeed.remove(playerId);
        baseVerticalFlySpeed.remove(playerId);
        clearExpiry(playerId);
    }

    public void grantTimed(@Nonnull PlayerRef player, int minutes) {
        long expiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(Math.max(1, minutes));
        enabled.put(player.getUuid(), Boolean.TRUE);
        sentExpiryWarnings.remove(player.getUuid());
        if (storage != null) {
            PlayerDataModel data = storage.getPlayerData(player.getUuid());
            data.setFlyEnabled(true);
            data.setFlyExpiresAt(expiresAt);
            storage.savePlayerDataAsync(player.getUuid(), data);
        }
        if (!applyState(player, true)) {
            queueApply(player.getUuid(), true);
        }
    }

    public void clearExpiry(@Nonnull UUID playerId) {
        sentExpiryWarnings.remove(playerId);
        if (storage != null) {
            PlayerDataModel data = storage.getPlayerData(playerId);
            if (data.getFlyExpiresAt() > 0L) {
                data.setFlyExpiresAt(0L);
                storage.savePlayerDataAsync(playerId, data);
            }
        }
    }

    public void queueApply(@Nonnull UUID playerId) {
        queueApply(playerId, true);
    }

    public void queueApply(@Nonnull UUID playerId, boolean enabled) {
        pendingApply.put(playerId, enabled);
    }

    public boolean isApplyPending(@Nonnull UUID playerId) {
        return pendingApply.containsKey(playerId);
    }

    public void clearPending(@Nonnull UUID playerId) {
        pendingApply.remove(playerId);
    }

    public boolean tryApplyIfPending(@Nonnull PlayerRef target) {
        UUID playerId = target.getUuid();
        Boolean enabled = pendingApply.get(playerId);
        if (enabled == null) return false;
        if (!applyState(target, enabled)) return false;
        clearPending(playerId);
        return true;
    }

    public boolean applyState(@Nonnull PlayerRef target, boolean enabled) {
        UUID playerId = target.getUuid();
        Ref<EntityStore> targetRef = target.getReference();
        if (targetRef == null) return false;
        Store<EntityStore> targetStore = targetRef.getStore();
        if (targetStore == null) return false;

        MovementManager movementManager = targetStore.getComponent(targetRef, MovementManager.getComponentType());
        if (movementManager == null) return false;

        movementManager.applyDefaultSettings();
        MovementSettings settings = movementManager.getSettings();
        if (settings != null) settings.canFly = enabled;

        MovementSettings defaults = movementManager.getDefaultSettings();
        if (defaults != null) defaults.canFly = enabled;
        applyFlySpeedMultiplier(playerId, settings, defaults);

        MovementStatesComponent statesComponent = targetStore.getComponent(targetRef, MovementStatesComponent.getComponentType());
        if (statesComponent != null) {
            MovementStates current = statesComponent.getMovementStates();
            MovementStates updated = (current != null) ? new MovementStates(current) : new MovementStates();
            if (!enabled) {
                updated.flying = false;
                updated.jumping = false;
                updated.gliding = false;
            } else {
                updated.flying = false; // allow client to double-jump to begin flying
            }
            statesComponent.setMovementStates(updated);
            statesComponent.setSentMovementStates(updated);
        }

        if (!enabled) {
            Player player = targetStore.getComponent(targetRef, Player.getComponentType());
            if (player != null) {
                MovementStates current = (statesComponent != null) ? statesComponent.getMovementStates() : null;
                MovementStates updated = (current != null) ? new MovementStates(current) : new MovementStates();
                updated.flying = false;
                player.applyMovementStates(targetRef, new SavedMovementStates(false), updated, targetStore);
            }

            Velocity velocity = targetStore.getComponent(targetRef, Velocity.getComponentType());
            if (velocity != null) {
                velocity.setY(0d);
                velocity.setClient(0d, 0d, 0d);
            }
        }

        movementManager.update(target.getPacketHandler());
        return true;
    }

    public void setFlySpeedMultiplier(@Nonnull UUID playerId, float multiplier) {
        if (!Float.isFinite(multiplier) || multiplier <= 0.0F) {
            flySpeedMultiplier.remove(playerId);
            return;
        }
        flySpeedMultiplier.put(playerId, multiplier);
    }

    public float getFlySpeedMultiplier(@Nonnull UUID playerId) {
        Float value = flySpeedMultiplier.get(playerId);
        return value != null && Float.isFinite(value) && value > 0.0F ? value : 1.0F;
    }

    public boolean applySpeedOnly(@Nonnull PlayerRef target) {
        Ref<EntityStore> targetRef = target.getReference();
        if (targetRef == null) return false;
        Store<EntityStore> targetStore = targetRef.getStore();
        if (targetStore == null) return false;
        MovementManager movementManager = targetStore.getComponent(targetRef, MovementManager.getComponentType());
        if (movementManager == null) return false;

        movementManager.applyDefaultSettings();
        applyFlySpeedMultiplier(target.getUuid(), movementManager.getSettings(), movementManager.getDefaultSettings());
        movementManager.update(target.getPacketHandler());
        return true;
    }

    private void applyFlySpeedMultiplier(@Nonnull UUID playerId,
                                         MovementSettings settings,
                                         MovementSettings defaults) {
        if (settings == null || defaults == null) return;

        float multiplier = getFlySpeedMultiplier(playerId);
        float defaultHorizontal = sanitizePositive(defaults.horizontalFlySpeed, 0.05F);
        float defaultVertical = sanitizePositive(defaults.verticalFlySpeed, 0.05F);

        float baseHorizontal = sanitizePositive(
                baseHorizontalFlySpeed.getOrDefault(playerId, defaultHorizontal),
                defaultHorizontal
        );
        float baseVertical = sanitizePositive(
                baseVerticalFlySpeed.getOrDefault(playerId, defaultVertical),
                defaultVertical
        );
        baseHorizontalFlySpeed.put(playerId, baseHorizontal);
        baseVerticalFlySpeed.put(playerId, baseVertical);

        float horizontal = sanitizePositive(baseHorizontal * multiplier, baseHorizontal);
        float vertical = sanitizePositive(baseVertical * multiplier, baseVertical);

        settings.horizontalFlySpeed = horizontal;
        settings.verticalFlySpeed = vertical;
        defaults.horizontalFlySpeed = horizontal;
        defaults.verticalFlySpeed = vertical;
    }

    private static float sanitizePositive(float value, float fallback) {
        if (Float.isFinite(value) && value > 0.0F) {
            return value;
        }
        return Float.isFinite(fallback) && fallback > 0.0F ? fallback : 1.0F;
    }

    private void tickExpiriesSafe() {
        try {
            tickExpiries();
        } catch (Throwable ignored) {
        }
    }

    private void tickExpiries() {
        if (storage == null || config == null || !config.isTimedFlightEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (UUID playerId : storage.listPlayerIds()) {
            PlayerDataModel data = storage.getPlayerData(playerId);
            long expiresAt = data.getFlyExpiresAt();
            if (expiresAt <= 0L || !data.isFlyEnabled()) {
                continue;
            }
            PlayerRef player = Universe.get().getPlayer(playerId);
            if (expiresAt <= now) {
                data.setFlyEnabled(false);
                data.setFlyExpiresAt(0L);
                storage.savePlayerDataAsync(playerId, data);
                enabled.remove(playerId);
                sentExpiryWarnings.remove(playerId);
                if (player != null) {
                    applyState(player, false);
                    Messages.sendPrefixedKey(player, "fly.expired", java.util.Map.of());
                }
                continue;
            }
            if (player == null) {
                continue;
            }
            long remainingSeconds = Math.max(1L, (expiresAt - now) / 1000L);
            for (Integer warning : config.getFlightWarningSeconds()) {
                if (warning == null || warning <= 0 || remainingSeconds > warning) continue;
                java.util.Set<Integer> sent = sentExpiryWarnings.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet());
                if (sent.add(warning)) {
                    Messages.sendPrefixedKey(player, "fly.expiring", java.util.Map.of(
                            "time", TimeUtil.formatDurationSeconds(remainingSeconds)
                    ));
                }
            }
        }
    }
}

