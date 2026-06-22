package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.ui.economy.EconomyHud;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;
import xyz.thelegacyvoyage.hyessentialsx.util.MultipleHudBridge;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class EconomyHudManager {

    private static final int INITIAL_DELAY_MS = 500;

    private final ConfigManager config;
    private final StorageManager storage;
    private final EconomyManager economy;
    private final ScheduledExecutorService scheduler;
    private final Map<UUID, EconomyHud> huds = new ConcurrentHashMap<>();
    private final Set<UUID> suppressedPlayers = ConcurrentHashMap.newKeySet();
    @Nullable
    private volatile ScheduledFuture<?> tickTask;
    private volatile int scheduledIntervalMs = -1;

    public EconomyHudManager(@Nonnull ConfigManager config,
                             @Nonnull StorageManager storage,
                             @Nonnull EconomyManager economy) {
        this.config = config;
        this.storage = storage;
        this.economy = economy;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HyEssentialsX-EconomyHud");
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized void start() {
        if (scheduler.isShutdown() || scheduler.isTerminated()) {
            return;
        }

        if (!economy.isEnabled() || !config.isEconomyHudEnabled()) {
            cancelTickerLocked();
            return;
        }

        int interval = Math.max(250, config.getEconomyHudUpdateIntervalMs());
        ScheduledFuture<?> task = tickTask;
        if (task != null && !task.isCancelled() && !task.isDone() && scheduledIntervalMs == interval) {
            return;
        }

        cancelTickerLocked();
        tickTask = scheduler.scheduleAtFixedRate(this::tick, interval, interval, TimeUnit.MILLISECONDS);
        scheduledIntervalMs = interval;
    }

    public synchronized void shutdown() {
        cancelTickerLocked();
        scheduler.shutdownNow();
        huds.clear();
        suppressedPlayers.clear();
    }

    public void scheduleInitial(@Nonnull PlayerRef playerRef) {
        if (scheduler.isShutdown() || scheduler.isTerminated()) {
            return;
        }
        scheduler.schedule(() -> refreshPlayer(playerRef), INITIAL_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    public void refreshAll() {
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            refreshPlayer(playerRef);
        }
    }

    public void refreshPlayer(@Nonnull PlayerRef playerRef) {
        if (!economy.isEnabled() || !config.isEconomyHudEnabled()) {
            hidePlayer(playerRef);
            return;
        }
        PlayerDataModel data = storage.getPlayerData(playerRef.getUuid());
        if (data.getEconomyHudHidden() == null) {
            data.setEconomyHudHidden(config.isEconomyHudDefaultHidden());
            storage.savePlayerDataAsync(playerRef.getUuid(), data);
        }
        if (data.isEconomyHudHidden()) {
            hidePlayer(playerRef);
            return;
        }
        runOnWorldThread(playerRef, () -> updatePlayer(playerRef));
    }

    public void onPlayerDisconnect(@Nonnull PlayerRef playerRef) {
        hidePlayer(playerRef);
        huds.remove(playerRef.getUuid());
        suppressedPlayers.remove(playerRef.getUuid());
    }

    public void setPlayerHidden(@Nonnull UUID uuid, boolean hidden) {
        PlayerDataModel data = storage.getPlayerData(uuid);
        data.setEconomyHudHidden(hidden);
        storage.savePlayerDataAsync(uuid, data);
    }

    public boolean isPlayerHidden(@Nonnull UUID uuid) {
        return storage.getPlayerData(uuid).isEconomyHudHidden();
    }

    private void tick() {
        if (!economy.isEnabled() || !config.isEconomyHudEnabled()) {
            return;
        }
        refreshAll();
    }

    private void hidePlayer(@Nonnull PlayerRef playerRef) {
        runOnWorldThread(playerRef, () -> {
            Player player = resolvePlayer(playerRef);
            if (player == null) {
                return;
            }
            EconomyHud hud = huds.get(playerRef.getUuid());
            if (hud != null) {
                hud.hide(player, playerRef, MultipleHudBridge.isAvailable());
            }
        });
    }

    private void updatePlayer(@Nonnull PlayerRef playerRef) {
        Player player = resolvePlayer(playerRef);
        if (player == null) {
            return;
        }
        boolean useMultipleHud = MultipleHudBridge.isAvailable();
        EconomyHud existingHud = huds.get(playerRef.getUuid());
        if (useMultipleHud) {
            if (!MultipleHudBridge.canAttachToPlayer(player)) {
                logSuppressed(playerRef, player);
                return;
            }
        } else {
            CustomUIHud currentHud = player.getHudManager().getCustomHud();
            if (currentHud != null && currentHud != existingHud) {
                logSuppressed(playerRef, player);
                return;
            }
        }
        suppressedPlayers.remove(playerRef.getUuid());
        long balance = economy.getBalance(playerRef.getUuid());
        EconomyHud.State state = new EconomyHud.State(
                config.getEconomyHudLabel(),
                config.getEconomyCurrencySymbol(),
                economy.formatAmountCompact(balance),
                config.getEconomyHudAnchor(),
                config.getEconomyHudOffsetX(),
                config.getEconomyHudOffsetY(),
                config.getEconomyHudWidth(),
                config.getEconomyHudHeight(),
                config.getEconomyHudBackgroundColor(),
                config.getEconomyHudLabelColor(),
                config.getEconomyHudSymbolColor(),
                config.getEconomyHudAmountColor()
        );
        EconomyHud hud = huds.computeIfAbsent(playerRef.getUuid(), id -> new EconomyHud(playerRef));
        hud.update(player, playerRef, state, useMultipleHud);
    }

    private void logSuppressed(@Nonnull PlayerRef playerRef, @Nonnull Player player) {
        if (!suppressedPlayers.add(playerRef.getUuid())) {
            return;
        }
        String hudName = "unknown";
        CustomUIHud currentHud = player.getHudManager().getCustomHud();
        if (currentHud != null) {
            hudName = currentHud.getClass().getName();
        }
        Log.warn("[HyEssentialsX] Economy HUD suppressed for " + playerRef.getUsername()
                + " (active custom HUD: " + hudName + ").");
    }

    private void runOnWorldThread(@Nonnull PlayerRef playerRef, @Nonnull Runnable task) {
        World world = resolveWorld(playerRef);
        if (world == null) {
            return;
        }
        if (world.isInThread()) {
            task.run();
        } else {
            world.execute(task);
        }
    }

    @Nullable
    private World resolveWorld(@Nonnull PlayerRef playerRef) {
        try {
            UUID worldId = playerRef.getWorldUuid();
            if (worldId == null) {
                return null;
            }
            return Universe.get().getWorld(worldId);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private Player resolvePlayer(@Nonnull PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        Store<EntityStore> store = ref.getStore();
        return store.getComponent(ref, Player.getComponentType());
    }

    private void cancelTickerLocked() {
        ScheduledFuture<?> task = tickTask;
        if (task != null) {
            task.cancel(false);
        }
        tickTask = null;
        scheduledIntervalMs = -1;
    }
}
