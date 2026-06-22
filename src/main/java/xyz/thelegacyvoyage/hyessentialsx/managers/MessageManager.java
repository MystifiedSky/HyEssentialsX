package xyz.thelegacyvoyage.hyessentialsx.managers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MessageManager {

    private final ConcurrentHashMap<UUID, UUID> lastPartner = new ConcurrentHashMap<>();

    public void setLastPartner(@Nonnull UUID sender, @Nonnull UUID target) {
        lastPartner.put(sender, target);
        lastPartner.put(target, sender);
    }

    @Nullable
    public UUID getLastPartner(@Nonnull UUID playerId) {
        return lastPartner.get(playerId);
    }

    public void clear(@Nonnull UUID playerId) {
        lastPartner.remove(playerId);
    }
}
