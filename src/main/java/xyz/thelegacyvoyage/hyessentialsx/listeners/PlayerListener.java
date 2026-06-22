package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.StorageManager;

import javax.annotation.Nonnull;
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

    public PlayerListener(@Nonnull ConfigManager config, @Nonnull StorageManager storage) {
        this.config = config;
        this.storage = storage;
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
        if (firstJoin && config.isWelcomeEnabled()) {
            sendWelcome(player, playerName);
        }

        if (config.isJoinQuitEnabled()) {
            broadcastLines(config.getJoinMessages(), playerName);
        }

        if (config.isMotdEnabled() && config.isMotdShowOnJoin()) {
            for (String line : config.getMotdMessages()) {
                Messages.send(player, line);
            }
        }
    }

    /**
     * Handle player disconnect event.
     * @param event The player disconnect event
     */
    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        String playerName = event.getPlayerRef() != null ? event.getPlayerRef().getUsername() : "Unknown";

        LOGGER.at(Level.INFO).log("[HyEssentialsX] Player %s disconnected", playerName);

        if (config.isJoinQuitEnabled()) {
            broadcastLines(config.getQuitMessages(), playerName);
        }
    }

    private boolean isFirstJoin(@Nonnull PlayerRef player) {
        PlayerDataModel data = storage.getPlayerData(player.getUuid());
        return data.getLastSeenAt() == 0L;
    }

    private void sendWelcome(@Nonnull PlayerRef player, @Nonnull String playerName) {
        if (config.getWelcomeMessages().isEmpty()) return;
        if (config.isWelcomeBroadcastToAll()) {
            broadcastLines(config.getWelcomeMessages(), playerName);
            return;
        }
        for (String line : config.getWelcomeMessages()) {
            Messages.send(player, line.replace("{player}", playerName));
        }
    }

    private void broadcastLines(@Nonnull java.util.List<String> lines, @Nonnull String playerName) {
        if (lines.isEmpty()) return;
        for (PlayerRef target : Universe.get().getPlayers()) {
            for (String line : lines) {
                Messages.send(target, line.replace("{player}", playerName));
            }
        }
    }
}
