package xyz.thelegacyvoyage.hyessentialsx.managers;

import xyz.thelegacyvoyage.hyessentialsx.models.MuteModel;
import xyz.thelegacyvoyage.hyessentialsx.util.StorageManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public final class MuteManager {

    private final StorageManager storage;

    public MuteManager(@Nonnull StorageManager storage) {
        this.storage = storage;
    }

    public void mute(@Nonnull UUID playerId, @Nonnull MuteModel mute) {
        storage.setMute(playerId, mute);
    }

    public void unmute(@Nonnull UUID playerId) {
        storage.removeMute(playerId);
    }

    @Nullable
    public MuteModel getMute(@Nonnull UUID playerId) {
        MuteModel mute = storage.getMute(playerId);
        if (mute == null) return null;
        if (mute.getExpiresAt() > 0 && System.currentTimeMillis() > mute.getExpiresAt()) {
            storage.removeMute(playerId);
            return null;
        }
        return mute;
    }

    public boolean isMuted(@Nonnull UUID playerId) {
        return getMute(playerId) != null;
    }
}
