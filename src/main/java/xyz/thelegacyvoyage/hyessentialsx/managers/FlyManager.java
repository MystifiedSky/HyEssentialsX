package xyz.thelegacyvoyage.hyessentialsx.managers;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FlyManager {

    private final ConcurrentHashMap<UUID, Boolean> enabled = new ConcurrentHashMap<>();

    public boolean isEnabled(@Nonnull UUID playerId) {
        return enabled.containsKey(playerId);
    }

    public boolean setEnabled(@Nonnull UUID playerId, boolean value) {
        if (value) {
            return enabled.put(playerId, Boolean.TRUE) == null;
        }
        return enabled.remove(playerId) != null;
    }

    public boolean toggle(@Nonnull UUID playerId) {
        if (enabled.remove(playerId) != null) return false;
        enabled.put(playerId, Boolean.TRUE);
        return true;
    }

    public void clear(@Nonnull UUID playerId) {
        enabled.remove(playerId);
    }
}
