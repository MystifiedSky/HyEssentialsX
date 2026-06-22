package xyz.thelegacyvoyage.hyessentialsx.managers;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

public final class VanishManager {

    private final ConcurrentHashMap<UUID, Boolean> vanished = new ConcurrentHashMap<>();

    public boolean isEnabled(@Nonnull UUID playerId) {
        return vanished.containsKey(playerId);
    }

    @Nonnull
    public Set<UUID> getVanishedPlayers() {
        return vanished.keySet();
    }

    public boolean setEnabled(@Nonnull UUID playerId, boolean enabled) {
        if (enabled) {
            vanished.put(playerId, Boolean.TRUE);
            return true;
        }
        vanished.remove(playerId);
        return false;
    }

    public boolean toggle(@Nonnull UUID playerId) {
        if (vanished.remove(playerId) != null) return false;
        vanished.put(playerId, Boolean.TRUE);
        return true;
    }

    public void clear(@Nonnull UUID playerId) {
        vanished.remove(playerId);
    }
}
