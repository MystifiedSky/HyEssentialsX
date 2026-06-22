package xyz.thelegacyvoyage.hyessentialsx.storage;

import xyz.thelegacyvoyage.hyessentialsx.models.KitModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface StorageBackend {

    @Nullable
    PlayerDataModel loadPlayerData(@Nonnull UUID uuid);

    void savePlayerData(@Nonnull UUID uuid, @Nonnull PlayerDataModel data);

    @Nonnull
    Set<UUID> listPlayerIds();

    @Nonnull
    Map<String, WarpModel> loadWarps();

    void saveWarps(@Nonnull Map<String, WarpModel> warps);

    @Nonnull
    Map<String, KitModel> loadKits();

    void saveKits(@Nonnull Map<String, KitModel> kits);

    void shutdown();
}
