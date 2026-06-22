package xyz.thelegacyvoyage.hyessentialsx.managers;

import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;

import javax.annotation.Nonnull;
import java.util.UUID;

public final class IgnoreManager {

    private final StorageManager storage;

    public IgnoreManager(@Nonnull StorageManager storage) {
        this.storage = storage;
    }

    public boolean isIgnoring(@Nonnull UUID owner, @Nonnull UUID target) {
        PlayerDataModel data = storage.getPlayerData(owner);
        return data.isIgnoring(target);
    }

    public boolean toggleIgnore(@Nonnull UUID owner, @Nonnull UUID target) {
        PlayerDataModel data = storage.getPlayerData(owner);
        boolean nowIgnoring;
        if (data.isIgnoring(target)) {
            data.removeIgnoredPlayer(target);
            nowIgnoring = false;
        } else {
            data.addIgnoredPlayer(target);
            nowIgnoring = true;
        }
        storage.savePlayerDataAsync(owner, data);
        return nowIgnoring;
    }

    public boolean unignore(@Nonnull UUID owner, @Nonnull UUID target) {
        PlayerDataModel data = storage.getPlayerData(owner);
        boolean changed = data.removeIgnoredPlayer(target);
        if (changed) {
            storage.savePlayerDataAsync(owner, data);
        }
        return changed;
    }
}
