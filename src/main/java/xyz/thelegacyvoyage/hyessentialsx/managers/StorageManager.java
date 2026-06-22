package xyz.thelegacyvoyage.hyessentialsx.managers;

import xyz.thelegacyvoyage.hyessentialsx.models.IpBanModel;
import xyz.thelegacyvoyage.hyessentialsx.models.KitModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;
import xyz.thelegacyvoyage.hyessentialsx.storage.JsonStorageBackend;
import xyz.thelegacyvoyage.hyessentialsx.storage.MysqlStorageBackend;
import xyz.thelegacyvoyage.hyessentialsx.storage.SqliteStorageBackend;
import xyz.thelegacyvoyage.hyessentialsx.storage.StorageBackend;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StorageManager {

    private final StorageBackend backend;
    private final ExecutorService ioPool;

    private final ConcurrentHashMap<UUID, PlayerDataModel> playerCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> nameIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WarpModel> warps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, KitModel> kits = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ShopModel> shops = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, IpBanModel> ipBans = new ConcurrentHashMap<>();

    public StorageManager(@Nonnull Path dataFolder, @Nonnull ConfigManager config) {
        this.ioPool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "HyEssentialsX-IO");
            t.setDaemon(true);
            return t;
        });

        this.backend = createBackend(dataFolder, config);
        loadAll();
    }

    private StorageBackend createBackend(Path dataFolder, ConfigManager config) {
        String type = config.getStorageType().toLowerCase();
        if ("sqlite".equals(type)) {
            try {
                StorageBackend backend = new SqliteStorageBackend(config.getSqliteFile(dataFolder));
                Log.info("Storage backend: sqlite (" + config.getSqliteFile(dataFolder) + ")");
                return backend;
            } catch (Exception e) {
                Log.warn("SQLite unavailable, falling back to JSON: " + e.getMessage());
            }
        }
        if ("mysql".equals(type) || "mariadb".equals(type)) {
            try {
                StorageBackend mysql = new MysqlStorageBackend(
                        config.getMysqlHost(),
                        config.getMysqlPort(),
                        config.getMysqlDatabase(),
                        config.getMysqlUser(),
                        config.getMysqlPassword()
                );
                Log.info("Storage backend: mysql (" + config.getMysqlHost() + ":" + config.getMysqlPort() + ")");
                return mysql;
            } catch (Exception e) {
                Log.warn("MySQL unavailable, falling back to JSON: " + e.getMessage());
            }
        }
        if ("json".equals(type)) {
            Log.info("Storage backend: json");
        } else if (!"sqlite".equals(type) && !"mysql".equals(type) && !"mariadb".equals(type)) {
            Log.warn("Unknown storage type '" + type + "', falling back to JSON.");
        }
        return new JsonStorageBackend(dataFolder);
    }


    private void loadAll() {
        warps.clear();
        warps.putAll(backend.loadWarps());

        kits.clear();
        kits.putAll(backend.loadKits());

        shops.clear();
        shops.putAll(backend.loadShops());

        ipBans.clear();
        ipBans.putAll(backend.loadIpBans());

        rebuildNameIndex();
    }

    public void reloadCaches() {
        playerCache.clear();
        loadAll();
    }

    private void rebuildNameIndex() {
        nameIndex.clear();
        Set<UUID> ids = backend.listPlayerIds();
        for (UUID id : ids) {
            PlayerDataModel data = backend.loadPlayerData(id);
            if (data != null && data.getLastKnownName() != null) {
                nameIndex.put(data.getLastKnownName().toLowerCase(), id);
            }
        }
    }

    @Nonnull
    public PlayerDataModel getPlayerData(@Nonnull UUID uuid) {
        return playerCache.computeIfAbsent(uuid, backend::loadPlayerData);
    }

    public void savePlayerData(@Nonnull UUID uuid) {
        PlayerDataModel data = playerCache.get(uuid);
        if (data == null) return;
        savePlayerDataAsync(uuid, data);
    }

    public void savePlayerDataAsync(@Nonnull UUID uuid, @Nonnull PlayerDataModel data) {
        CompletableFuture.runAsync(() -> backend.savePlayerData(uuid, data), ioPool);
    }

    public void unloadPlayer(@Nonnull UUID uuid) {
        playerCache.remove(uuid);
    }

    public void updatePlayerName(@Nonnull UUID uuid, @Nonnull String name) {
        String key = name.toLowerCase();
        nameIndex.put(key, uuid);
        PlayerDataModel data = getPlayerData(uuid);
        data.setLastKnownName(name);
        savePlayerDataAsync(uuid, data);
    }

    @Nullable
    public UUID resolvePlayerIdByName(@Nonnull String name) {
        return nameIndex.get(name.toLowerCase());
    }

    @Nonnull
    public Set<UUID> listPlayerIds() {
        Set<UUID> ids = new java.util.HashSet<>(backend.listPlayerIds());
        ids.addAll(playerCache.keySet());
        return Set.copyOf(ids);
    }

    @Nonnull
    public Map<String, WarpModel> getWarps() {
        return Map.copyOf(warps);
    }

    @Nullable
    public WarpModel getWarp(@Nonnull String name) {
        return warps.get(name.toLowerCase());
    }

    public void setWarp(@Nonnull String name, @Nonnull WarpModel warp) {
        warps.put(name.toLowerCase(), warp);
        saveWarpsAsync();
    }

    public boolean deleteWarp(@Nonnull String name) {
        WarpModel removed = warps.remove(name.toLowerCase());
        if (removed != null) {
            saveWarpsAsync();
            return true;
        }
        return false;
    }

    private void saveWarpsAsync() {
        Map<String, WarpModel> snapshot = Map.copyOf(warps);
        CompletableFuture.runAsync(() -> backend.saveWarps(snapshot), ioPool);
    }

    @Nonnull
    public Map<String, KitModel> getKits() {
        return Map.copyOf(kits);
    }

    @Nullable
    public KitModel getKit(@Nonnull String name) {
        return kits.get(name.toLowerCase());
    }

    public void setKit(@Nonnull String name, @Nonnull KitModel kit) {
        kits.put(name.toLowerCase(), kit);
        saveKitsAsync();
    }

    public boolean deleteKit(@Nonnull String name) {
        KitModel removed = kits.remove(name.toLowerCase());
        if (removed != null) {
            saveKitsAsync();
            return true;
        }
        return false;
    }

    private void saveKitsAsync() {
        Map<String, KitModel> snapshot = Map.copyOf(kits);
        CompletableFuture.runAsync(() -> backend.saveKits(snapshot), ioPool);
    }

    @Nonnull
    public Map<String, ShopModel> getShops() {
        return Map.copyOf(shops);
    }

    @Nullable
    public ShopModel getShop(@Nonnull String name) {
        return shops.get(name.toLowerCase());
    }

    public void setShop(@Nonnull String name, @Nonnull ShopModel shop) {
        shops.put(name.toLowerCase(), shop);
        saveShopsAsync();
    }

    public boolean deleteShop(@Nonnull String name) {
        ShopModel removed = shops.remove(name.toLowerCase());
        if (removed != null) {
            saveShopsAsync();
            return true;
        }
        return false;
    }

    private void saveShopsAsync() {
        Map<String, ShopModel> snapshot = Map.copyOf(shops);
        CompletableFuture.runAsync(() -> backend.saveShops(snapshot), ioPool);
    }

    @Nonnull
    public Map<String, IpBanModel> getIpBans() {
        return Map.copyOf(ipBans);
    }

    @Nullable
    public IpBanModel getIpBan(@Nonnull String ip) {
        return ipBans.get(ip);
    }

    public void setIpBan(@Nonnull String ip, @Nonnull IpBanModel ban) {
        ipBans.put(ip, ban);
        saveIpBansAsync();
    }

    public boolean removeIpBan(@Nonnull String ip) {
        IpBanModel removed = ipBans.remove(ip);
        if (removed != null) {
            saveIpBansAsync();
            return true;
        }
        return false;
    }

    private void saveIpBansAsync() {
        Map<String, IpBanModel> snapshot = Map.copyOf(ipBans);
        CompletableFuture.runAsync(() -> backend.saveIpBans(snapshot), ioPool);
    }

    @Nullable
    public xyz.thelegacyvoyage.hyessentialsx.models.MuteModel getMute(@Nonnull UUID uuid) {
        PlayerDataModel data = getPlayerData(uuid);
        return data.getMute();
    }

    public void setMute(@Nonnull UUID uuid, @Nonnull xyz.thelegacyvoyage.hyessentialsx.models.MuteModel mute) {
        PlayerDataModel data = getPlayerData(uuid);
        data.setMute(mute);
        savePlayerDataAsync(uuid, data);
    }

    public void removeMute(@Nonnull UUID uuid) {
        PlayerDataModel data = getPlayerData(uuid);
        if (data.getMute() != null) {
            data.setMute(null);
            savePlayerDataAsync(uuid, data);
        }
    }

    @Nullable
    public xyz.thelegacyvoyage.hyessentialsx.models.BanModel getBan(@Nonnull UUID uuid) {
        PlayerDataModel data = getPlayerData(uuid);
        return data.getBan();
    }

    public void setBan(@Nonnull UUID uuid, @Nonnull xyz.thelegacyvoyage.hyessentialsx.models.BanModel ban) {
        PlayerDataModel data = getPlayerData(uuid);
        data.setBan(ban);
        savePlayerDataAsync(uuid, data);
    }

    public void removeBan(@Nonnull UUID uuid) {
        PlayerDataModel data = getPlayerData(uuid);
        if (data.getBan() != null) {
            data.setBan(null);
            savePlayerDataAsync(uuid, data);
        }
    }

    public void shutdown() {
        try {
            backend.saveWarps(Map.copyOf(warps));
            backend.saveKits(Map.copyOf(kits));
            backend.saveShops(Map.copyOf(shops));
            backend.saveIpBans(Map.copyOf(ipBans));
            for (Map.Entry<UUID, PlayerDataModel> entry : playerCache.entrySet()) {
                backend.savePlayerData(entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            Log.error("Failed to flush storage on shutdown: " + e.getMessage());
        } finally {
            ioPool.shutdownNow();
            backend.shutdown();
        }
    }
}
