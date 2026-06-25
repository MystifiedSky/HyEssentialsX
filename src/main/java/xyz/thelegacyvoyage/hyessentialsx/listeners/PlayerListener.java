package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.entity.entities.player.HiddenPlayersManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.managers.VanishManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.MailManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.SpawnManager;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.SpawnModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;
import xyz.thelegacyvoyage.hyessentialsx.util.PlaceholderApiUtil;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Listener for player connection events.
 *
 * Listens to:
 * - PlayerConnectEvent - When a player connects to the server
 * - PlayerDisconnectEvent - When a player disconnects from the server
 */
public class PlayerListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final ConfigManager config;
    private final StorageManager storage;
    private final VanishManager vanishManager;
    private final MailManager mailManager;
    private final SpawnManager spawnManager;
    private final Set<UUID> pendingFirstJoinRoutes = ConcurrentHashMap.newKeySet();

    public PlayerListener(@Nonnull ConfigManager config,
                          @Nonnull StorageManager storage,
                          @Nonnull VanishManager vanishManager,
                          @Nonnull MailManager mailManager,
                          @Nonnull SpawnManager spawnManager) {
        this.config = config;
        this.storage = storage;
        this.vanishManager = vanishManager;
        this.mailManager = mailManager;
        this.spawnManager = spawnManager;
    }

    /**
     * Register all player event listeners.
     * @param eventBus The event registry to register listeners with
     */
    public void register(EventRegistry eventBus) {
        // PlayerConnectEvent - When a player connects
        try {
            eventBus.register(PlayerConnectEvent.class, this::onPlayerConnect);
            LOGGER.at(Level.INFO).log("[HyEssentialsX] Registered PlayerConnectEvent listener");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[HyEssentialsX] Failed to register PlayerConnectEvent");
        }
        try {
            eventBus.registerGlobal(AddPlayerToWorldEvent.class, this::onAddPlayerToWorld);
            LOGGER.at(Level.INFO).log("[HyEssentialsX] Registered AddPlayerToWorldEvent listener");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[HyEssentialsX] Failed to register AddPlayerToWorldEvent");
        }
        try {
            eventBus.registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
            LOGGER.at(Level.INFO).log("[HyEssentialsX] Registered PlayerReadyEvent listener");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[HyEssentialsX] Failed to register PlayerReadyEvent");
        }

        // PlayerDisconnectEvent - When a player disconnects
        try {
            eventBus.register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
            LOGGER.at(Level.INFO).log("[HyEssentialsX] Registered PlayerDisconnectEvent listener");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[HyEssentialsX] Failed to register PlayerDisconnectEvent");
        }
    }

    /**
     * Handle player connect event.
     * @param event The player connect event
     */
    private void onPlayerConnect(PlayerConnectEvent event) {
        PlayerRef player = event.getPlayerRef();
        String playerName = player != null ? player.getUsername() : "Unknown";
        String worldName = event.getWorld() != null ? event.getWorld().getName() : "unknown";

        LOGGER.at(Level.INFO).log("[HyEssentialsX] Player %s connected to world %s", playerName, worldName);

        if (player == null) return;

        boolean firstJoin = isFirstJoin(player);
        if (firstJoin) {
            pendingFirstJoinRoutes.add(player.getUuid());
        }
        if (firstJoin && config.isWelcomeEnabled()) {
            sendWelcome(player, playerName);
        }

        if (config.isJoinQuitEnabled()) {
            broadcastLines(config.getJoinMessages(), playerName, player, true);
        }

        if (config.isMotdEnabled() && config.isMotdShowOnJoin()) {
            for (String line : config.getMotdMessages()) {
                player.sendMessage(PlaceholderApiUtil.apply(
                        player,
                        applyPlaceholders(line, buildPlaceholders(playerName, player, true))
                ));
            }
        }

        mailManager.notifyOnJoin(player);
    }

    private void onAddPlayerToWorld(AddPlayerToWorldEvent event) {
        if (config.isJoinQuitEnabled()) {
            event.setBroadcastJoinMessage(false);
        }
    }

    private void onPlayerReady(PlayerReadyEvent event) {
        com.hypixel.hytale.server.core.entity.entities.Player playerEntity = event.getPlayer();
        if (playerEntity == null) return;
        com.hypixel.hytale.server.core.universe.world.World world = playerEntity.getWorld();
        if (world == null) return;
        Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref = event.getPlayerRef();
        if (ref == null) return;

        // PlayerReadyEvent can fire on non-world threads. Always hop to world thread before touching the store.
        world.execute(() -> {
            Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store = ref.getStore();
            PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null) return;
            applyJoinRoute(store, ref, player, world);
            HiddenPlayersManager hidden = player.getHiddenPlayersManager();
            if (hidden == null) return;
            for (var vanishedId : vanishManager.getVanishedPlayers()) {
                if (!vanishedId.equals(player.getUuid())) {
                    hidden.hidePlayer(vanishedId);
                }
            }
        });
    }

    /**
     * Handle player disconnect event.
     * @param event The player disconnect event
     */
    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        String playerName = event.getPlayerRef() != null ? event.getPlayerRef().getUsername() : "Unknown";
        if (event.getPlayerRef() != null) {
            pendingFirstJoinRoutes.remove(event.getPlayerRef().getUuid());
        }

        LOGGER.at(Level.INFO).log("[HyEssentialsX] Player %s disconnected", playerName);

        if (config.isJoinQuitEnabled()) {
            broadcastLines(config.getQuitMessages(), playerName, event.getPlayerRef(), false);
        }
    }

    private void applyJoinRoute(@Nonnull Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store,
                                @Nonnull Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref,
                                @Nonnull PlayerRef player,
                                @Nonnull com.hypixel.hytale.server.core.universe.world.World world) {
        if (!config.isSpawnEnabled()) return;
        boolean firstJoin = pendingFirstJoinRoutes.remove(player.getUuid());
        String route = firstJoin ? "firstjoin" : "join";
        SpawnModel spawn = spawnManager.getRoutedSpawn(world, player, route);
        if (spawn == null) return;
        String err = TeleportationUtil.teleportToSpawn(store, ref, spawn);
        if (err != null) {
            Log.warn("Join spawn routing failed for " + player.getUsername() + ": " + err);
        }
    }

    private boolean isFirstJoin(@Nonnull PlayerRef player) {
        PlayerDataModel data = storage.getPlayerData(player.getUuid());
        return data.getLastSeenAt() == 0L;
    }

    private void sendWelcome(@Nonnull PlayerRef player, @Nonnull String playerName) {
        if (config.getWelcomeMessages().isEmpty()) return;
        if (config.isWelcomeBroadcastToAll()) {
            broadcastLines(config.getWelcomeMessages(), playerName, player, true);
            return;
        }
        Map<String, String> placeholders = buildPlaceholders(playerName, player, true);
        for (String line : config.getWelcomeMessages()) {
            player.sendMessage(PlaceholderApiUtil.apply(player, applyPlaceholders(line, placeholders)));
        }
    }

    private void broadcastLines(@Nonnull java.util.List<String> lines,
                                @Nonnull String playerName,
                                @Nullable PlayerRef playerRef,
                                boolean joining) {
        if (lines.isEmpty()) return;
        Map<String, String> placeholders = buildPlaceholders(playerName, playerRef, joining);
        for (PlayerRef target : Universe.get().getPlayers()) {
            for (String line : lines) {
                target.sendMessage(PlaceholderApiUtil.apply(target, applyPlaceholders(line, placeholders)));
            }
        }
    }

    private Map<String, String> buildPlaceholders(@Nonnull String playerName,
                                                  @Nullable PlayerRef playerRef,
                                                  boolean joining) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", playerName);
        placeholders.put("total_players_online", String.valueOf(getOnlineCount(playerRef, joining)));
        placeholders.put("total_joined_players", String.valueOf(getTotalJoinedPlayers(playerRef, joining)));
        placeholders.put("discord", config.getDiscordInviteUrl());
        return placeholders;
    }

    private int getOnlineCount(@Nullable PlayerRef playerRef, boolean joining) {
        int count = Universe.get().getPlayers().size();
        if (playerRef == null) return count;
        boolean listed = Universe.get().getPlayer(playerRef.getUuid()) != null;
        if (joining && !listed) {
            return count + 1;
        }
        if (!joining && listed) {
            return Math.max(0, count - 1);
        }
        return count;
    }

    private int getTotalJoinedPlayers(@Nullable PlayerRef playerRef, boolean joining) {
        java.util.Set<java.util.UUID> ids = storage.listPlayerIds();
        int total = ids.size();
        if (playerRef != null && joining && !ids.contains(playerRef.getUuid())) {
            total += 1;
        }
        return total;
    }

    private String applyPlaceholders(@Nonnull String line, @Nonnull Map<String, String> placeholders) {
        String out = line;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            out = out.replace("{" + key + "}", value);
            out = out.replace("%" + key + "%", value);
        }
        return out;
    }
}

