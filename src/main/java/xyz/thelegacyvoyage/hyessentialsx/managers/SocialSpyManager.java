package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SocialSpyManager {

    public static final String PERMISSION_NODE = "hyessentialsx.socialspy";
    public static final String BYPASS_PERMISSION = "hyessentialsx.socialspy.bypass";

    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    public boolean toggle(@Nonnull UUID playerId) {
        if (enabled.remove(playerId)) {
            return false;
        }
        enabled.add(playerId);
        return true;
    }

    public boolean isEnabled(@Nonnull UUID playerId) {
        return enabled.contains(playerId);
    }

    public void clear(@Nonnull UUID playerId) {
        enabled.remove(playerId);
    }

    public boolean canUse(@Nonnull PlayerRef playerRef) {
        return hasPermission(playerRef, PERMISSION_NODE);
    }

    public void notifyPrivateMessage(@Nonnull PlayerRef sender,
                                     @Nonnull PlayerRef receiver,
                                     @Nonnull String message) {
        if (message.isBlank()) {
            return;
        }
        if (hasBypass(sender) || hasBypass(receiver)) {
            return;
        }

        for (PlayerRef watcher : Universe.get().getPlayers()) {
            if (watcher == null) continue;
            UUID watcherId = watcher.getUuid();
            if (!enabled.contains(watcherId)) continue;
            if (!canUse(watcher)) continue;

            Messages.sendPrefixedKey(watcher, "socialspy.format", Map.of(
                    "sender", sender.getUsername(),
                    "receiver", receiver.getUsername(),
                    "message", message
            ));
        }
    }

    private boolean hasBypass(@Nonnull PlayerRef playerRef) {
        return hasPermission(playerRef, BYPASS_PERMISSION);
    }

    private boolean hasPermission(@Nonnull PlayerRef playerRef, @Nonnull String permission) {
        return CommandPermissionUtil.hasPermission(playerRef, permission);
    }
}
