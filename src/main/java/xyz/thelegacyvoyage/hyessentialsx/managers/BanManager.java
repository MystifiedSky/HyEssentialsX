package xyz.thelegacyvoyage.hyessentialsx.managers;

import xyz.thelegacyvoyage.hyessentialsx.models.BanModel;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public final class BanManager {

    private final StorageManager storage;

    public BanManager(@Nonnull StorageManager storage) {
        this.storage = storage;
    }

    public void ban(@Nonnull UUID playerId, @Nonnull BanModel ban) {
        storage.setBan(playerId, ban);
    }

    public void unban(@Nonnull UUID playerId) {
        storage.removeBan(playerId);
    }

    @Nullable
    public BanModel getBan(@Nonnull UUID playerId) {
        BanModel ban = storage.getBan(playerId);
        if (ban == null) return null;
        if (ban.getExpiresAt() > 0 && System.currentTimeMillis() > ban.getExpiresAt()) {
            storage.removeBan(playerId);
            return null;
        }
        return ban;
    }

    public boolean isBanned(@Nonnull UUID playerId) {
        return getBan(playerId) != null;
    }
}

