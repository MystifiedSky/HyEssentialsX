package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.permissions.GroupPermissionChangeEvent;
import com.hypixel.hytale.server.core.event.events.permissions.PlayerGroupEvent;
import com.hypixel.hytale.server.core.event.events.permissions.PlayerPermissionChangeEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.io.handlers.game.GamePacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class CommandTreeRefreshManager {

    private final ConfigManager config;
    private final Map<UUID, ScheduledFuture<?>> pendingRefreshes = new ConcurrentHashMap<>();
    private final List<Object> luckPermsSubscriptions = new ArrayList<>();
    private volatile boolean luckPermsSubscribed;

    public CommandTreeRefreshManager(@Nonnull ConfigManager config) {
        this.config = config;
    }

    public void register(@Nonnull EventRegistry events) {
        events.registerGlobal(PlayerConnectEvent.class, event -> {
            signalLuckPermsContextUpdate(event.getPlayerRef());
            scheduleRefresh(event.getPlayerRef(), 1_000L);
        });
        events.register(PlayerPermissionChangeEvent.PermissionsAdded.class,
                event -> schedulePermissionRefresh(event.getPlayerUuid()));
        events.register(PlayerPermissionChangeEvent.PermissionsRemoved.class,
                event -> schedulePermissionRefresh(event.getPlayerUuid()));
        events.register(PlayerGroupEvent.Added.class, event -> schedulePermissionRefresh(event.getPlayerUuid()));
        events.register(PlayerGroupEvent.Removed.class, event -> schedulePermissionRefresh(event.getPlayerUuid()));
        events.register(GroupPermissionChangeEvent.Added.class, event -> schedulePermissionRefreshAll());
        events.register(GroupPermissionChangeEvent.Removed.class, event -> schedulePermissionRefreshAll());
    }

    public synchronized void start() {
        if (!config.isCommandTreeRefreshEnabled()) {
            unsubscribeLuckPermsEvents();
            return;
        }
        subscribeLuckPermsEvents();
        scheduleRefreshAll(1_000L);
    }

    public synchronized void reload() {
        start();
        scheduleRefreshAll(250L);
    }

    public synchronized void shutdown() {
        unsubscribeLuckPermsEvents();
        for (ScheduledFuture<?> task : pendingRefreshes.values()) {
            task.cancel(false);
        }
        pendingRefreshes.clear();
    }

    public void scheduleRefreshAll(long delayMs) {
        if (!config.isCommandTreeRefreshEnabled()) {
            return;
        }
        try {
            for (PlayerRef player : Universe.get().getPlayers()) {
                scheduleRefresh(player, delayMs);
            }
        } catch (Throwable ignored) {
        }
    }

    public void scheduleRefresh(@Nullable UUID uuid, long delayMs) {
        if (uuid == null) {
            return;
        }
        try {
            scheduleRefresh(Universe.get().getPlayer(uuid), delayMs);
        } catch (Throwable ignored) {
        }
    }

    public void scheduleRefresh(@Nullable PlayerRef player, long delayMs) {
        if (!config.isCommandTreeRefreshEnabled() || player == null) {
            return;
        }
        UUID uuid = player.getUuid();
        ScheduledFuture<?> oldTask = pendingRefreshes.remove(uuid);
        if (oldTask != null) {
            oldTask.cancel(false);
        }
        long safeDelay = Math.max(0L, delayMs);
        ScheduledFuture<?> task = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            pendingRefreshes.remove(uuid);
            refresh(player);
        }, safeDelay, TimeUnit.MILLISECONDS);
        pendingRefreshes.put(uuid, task);
    }

    public void refreshAllSafely() {
        if (!config.isCommandTreeRefreshEnabled()) {
            return;
        }
        try {
            for (PlayerRef player : Universe.get().getPlayers()) {
                refresh(player);
            }
        } catch (Throwable t) {
            Log.warn("[HyEssentialsX] Failed to refresh command trees: " + t.getMessage());
        }
    }

    private void schedulePermissionRefresh(@Nullable UUID uuid) {
        if (uuid == null) {
            return;
        }
        try {
            PlayerRef player = Universe.get().getPlayer(uuid);
            signalLuckPermsContextUpdate(player);
            scheduleRefresh(player, 500L);
        } catch (Throwable ignored) {
        }
    }

    private void schedulePermissionRefreshAll() {
        if (!config.isCommandTreeRefreshEnabled()) {
            return;
        }
        try {
            for (PlayerRef player : Universe.get().getPlayers()) {
                signalLuckPermsContextUpdate(player);
                scheduleRefresh(player, 500L);
            }
        } catch (Throwable ignored) {
        }
    }

    private void signalLuckPermsContextUpdate(@Nullable PlayerRef player) {
        if (player == null) {
            return;
        }
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object luckPerms = providerClass.getMethod("get").invoke(null);
            Object contextManager = luckPerms.getClass().getMethod("getContextManager").invoke(luckPerms);
            contextManager.getClass().getMethod("signalContextUpdate", Object.class).invoke(contextManager, player);
        } catch (ClassNotFoundException ignored) {
        } catch (InvocationTargetException e) {
            if (!(e.getCause() instanceof IllegalStateException)) {
                String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                Log.warn("[HyEssentialsX] Failed to signal LuckPerms context update: " + message);
            }
        } catch (Throwable t) {
            Log.warn("[HyEssentialsX] Failed to signal LuckPerms context update: " + t.getMessage());
        }
    }

    private void subscribeLuckPermsEvents() {
        if (luckPermsSubscribed) {
            return;
        }
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object luckPerms = providerClass.getMethod("get").invoke(null);
            Object eventBus = luckPerms.getClass().getMethod("getEventBus").invoke(luckPerms);
            Method subscribe = eventBus.getClass().getMethod("subscribe", Class.class, Consumer.class);

            subscribeLuckPermsEvent(eventBus, subscribe,
                    "net.luckperms.api.event.user.UserDataRecalculateEvent",
                    this::onLuckPermsUserDataRecalculate);
            subscribeLuckPermsEvent(eventBus, subscribe,
                    "net.luckperms.api.event.group.GroupDataRecalculateEvent",
                    event -> scheduleRefreshAll(250L));
            subscribeLuckPermsEvent(eventBus, subscribe,
                    "net.luckperms.api.event.context.ContextUpdateEvent",
                    this::onLuckPermsContextUpdate);
            subscribeLuckPermsEvent(eventBus, subscribe,
                    "net.luckperms.api.event.sync.PostSyncEvent",
                    event -> scheduleRefreshAll(250L));
            subscribeLuckPermsEvent(eventBus, subscribe,
                    "net.luckperms.api.event.sync.PostNetworkSyncEvent",
                    event -> scheduleRefreshAll(250L));

            luckPermsSubscribed = !luckPermsSubscriptions.isEmpty();
            if (luckPermsSubscribed) {
                Log.info("[HyEssentialsX] LuckPerms command tree refresh hooks registered.");
            }
        } catch (ClassNotFoundException ignored) {
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (!(cause instanceof IllegalStateException)) {
                String message = cause != null ? cause.getMessage() : e.getMessage();
                Log.warn("[HyEssentialsX] LuckPerms command tree refresh hooks unavailable: " + message);
            }
        } catch (Throwable t) {
            Log.warn("[HyEssentialsX] LuckPerms command tree refresh hooks unavailable: " + t.getMessage());
        }
    }

    private void subscribeLuckPermsEvent(
            @Nonnull Object eventBus,
            @Nonnull Method subscribe,
            @Nonnull String eventClassName,
            @Nonnull Consumer<Object> handler
    ) {
        try {
            Class<?> eventClass = Class.forName(eventClassName);
            Object subscription = subscribe.invoke(eventBus, eventClass, handler);
            if (subscription != null) {
                luckPermsSubscriptions.add(subscription);
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            Log.warn("[HyEssentialsX] Failed to register LuckPerms hook " + eventClassName + ": " + t.getMessage());
        }
    }

    private void onLuckPermsUserDataRecalculate(@Nonnull Object event) {
        UUID uuid = extractLuckPermsUserUuid(event);
        if (uuid != null) {
            scheduleRefresh(uuid, 250L);
        }
    }

    private void onLuckPermsContextUpdate(@Nonnull Object event) {
        UUID uuid = extractSubjectUuid(event);
        if (uuid != null) {
            scheduleRefresh(uuid, 250L);
        } else {
            scheduleRefreshAll(250L);
        }
    }

    @Nullable
    private UUID extractLuckPermsUserUuid(@Nonnull Object event) {
        try {
            Object user = event.getClass().getMethod("getUser").invoke(event);
            if (user == null) {
                return null;
            }
            Object uuid = user.getClass().getMethod("getUniqueId").invoke(user);
            return uuid instanceof UUID value ? value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private UUID extractSubjectUuid(@Nonnull Object event) {
        try {
            Object subject = event.getClass().getMethod("getSubject").invoke(event);
            if (subject instanceof PlayerRef player) {
                return player.getUuid();
            }
            if (subject instanceof UUID uuid) {
                return uuid;
            }
            if (subject != null) {
                try {
                    Object uuid = subject.getClass().getMethod("getUniqueId").invoke(subject);
                    if (uuid instanceof UUID value) {
                        return value;
                    }
                } catch (Throwable ignored) {
                }
                try {
                    Object uuid = subject.getClass().getMethod("getUuid").invoke(subject);
                    if (uuid instanceof UUID value) {
                        return value;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private synchronized void unsubscribeLuckPermsEvents() {
        for (Object subscription : luckPermsSubscriptions) {
            try {
                subscription.getClass().getMethod("close").invoke(subscription);
            } catch (Throwable ignored) {
            }
        }
        luckPermsSubscriptions.clear();
        luckPermsSubscribed = false;
    }

    private void refresh(@Nonnull PlayerRef player) {
        try {
            if (!player.isValid()) {
                return;
            }
            Object handler = player.getPacketHandler();
            if (handler instanceof GamePacketHandler gameHandler) {
                gameHandler.sendCommandTree();
            }
        } catch (Throwable ignored) {
        }
    }

}
