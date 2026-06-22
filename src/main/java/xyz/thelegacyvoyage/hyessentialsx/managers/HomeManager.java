package xyz.thelegacyvoyage.hyessentialsx.managers;

import xyz.thelegacyvoyage.hyessentialsx.models.HomeModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HomeManager {

    private final StorageManager storage;

    public HomeManager(@Nonnull StorageManager storage) {
        this.storage = storage;
    }

    public void setHome(@Nonnull UUID playerId, @Nonnull HomeModel home) {
        PlayerDataModel data = storage.getPlayerData(playerId);
        Map<String, HomeModel> homes = data.getHomes();
        homes.put(home.getName().toLowerCase(), home);
        storage.savePlayerDataAsync(playerId, data);
    }

    public boolean hasHome(@Nonnull UUID playerId, @Nonnull String name) {
        PlayerDataModel data = storage.getPlayerData(playerId);
        return data.getHomes().containsKey(name.toLowerCase());
    }

    public int getHomeCount(@Nonnull UUID playerId) {
        PlayerDataModel data = storage.getPlayerData(playerId);
        return data.getHomes().size();
    }

    @Nullable
    public HomeModel getHome(@Nonnull UUID playerId, @Nonnull String name) {
        PlayerDataModel data = storage.getPlayerData(playerId);
        return data.getHomes().get(name.toLowerCase());
    }

    public boolean removeHome(@Nonnull UUID playerId, @Nonnull String name) {
        PlayerDataModel data = storage.getPlayerData(playerId);
        HomeModel removed = data.getHomes().remove(name.toLowerCase());
        if (removed != null) {
            storage.savePlayerDataAsync(playerId, data);
            return true;
        }
        return false;
    }

    @Nonnull
    public List<String> listHomes(@Nonnull UUID playerId) {
        PlayerDataModel data = storage.getPlayerData(playerId);
        return new ArrayList<>(data.getHomes().keySet());
    }
}

