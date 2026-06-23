package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import xyz.thelegacyvoyage.hyessentialsx.models.AuctionHouseDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.IpBanModel;
import xyz.thelegacyvoyage.hyessentialsx.models.KitModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.StaffCaseModel;
import xyz.thelegacyvoyage.hyessentialsx.models.StaffActivityEntryModel;
import xyz.thelegacyvoyage.hyessentialsx.models.StaffActivityLogDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.StaffNoteModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarningModel;
import xyz.thelegacyvoyage.hyessentialsx.storage.JsonStorageBackend;
import xyz.thelegacyvoyage.hyessentialsx.storage.MongoStorageBackend;
import xyz.thelegacyvoyage.hyessentialsx.storage.MysqlStorageBackend;
import xyz.thelegacyvoyage.hyessentialsx.storage.SqliteStorageBackend;
import xyz.thelegacyvoyage.hyessentialsx.storage.StorageBackend;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public final class StorageManager {

    private static final int MAX_STAFF_ACTIVITY_ENTRIES = 250;

    private final StorageBackend backend;
    private final ExecutorService ioPool;
    private final Gson snapshotGson;

    private final ConcurrentHashMap<UUID, PlayerDataModel> playerCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> nameIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WarpModel> warps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, KitModel> kits = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ShopModel> shops = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, IpBanModel> ipBans = new ConcurrentHashMap<>();
    private AuctionHouseDataModel auctionHouseData = new AuctionHouseDataModel();
    private StaffActivityLogDataModel staffActivityLog = new StaffActivityLogDataModel();

    public StorageManager(@Nonnull Path dataFolder, @Nonnull ConfigManager config) {
        this(createBackend(dataFolder, config), createIoExecutor());
    }

    StorageManager(@Nonnull StorageBackend backend) {
        this(backend, createIoExecutor());
    }

    private StorageManager(@Nonnull StorageBackend backend, @Nonnull ExecutorService ioPool) {
        this.backend = backend;
        this.ioPool = ioPool;
        this.snapshotGson = new GsonBuilder().create();
        loadAll();
    }

    @Nonnull
    private static ExecutorService createIoExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "HyEssentialsX-IO");
            t.setDaemon(true);
            return t;
        });
    }

    private static StorageBackend createBackend(Path dataFolder, ConfigManager config) {
        String type = config.getStorageType().toLowerCase(Locale.ROOT);
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
        if ("mongodb".equals(type) || "mongo".equals(type)) {
            try {
                StorageBackend mongo = new MongoStorageBackend(
                        config.getMongoUri(),
                        config.getMongoDatabase(),
                        config.getMongoCollectionPrefix()
                );
                Log.info("Storage backend: mongodb (" + config.getMongoDatabase() + ")");
                return mongo;
            } catch (Exception e) {
                Log.warn("MongoDB unavailable, falling back to JSON: " + e.getMessage());
            }
        }
        if ("json".equals(type)) {
            Log.info("Storage backend: json");
        } else if (!"sqlite".equals(type)
                && !"mysql".equals(type)
                && !"mariadb".equals(type)
                && !"mongodb".equals(type)
                && !"mongo".equals(type)) {
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

        auctionHouseData = backend.loadAuctionHouseData();

        staffActivityLog = backend.loadStaffActivityLog();
        staffActivityLog.sanitize(MAX_STAFF_ACTIVITY_ENTRIES);

        rebuildNameIndex();
    }

    public void reloadCaches() {
        flush();
        playerCache.clear();
        loadAll();
    }

    private void rebuildNameIndex() {
        nameIndex.clear();
        Set<UUID> ids = backend.listPlayerIds();
        for (UUID id : ids) {
            PlayerDataModel data = sanitizePlayerData(backend.loadPlayerData(id));
            if (data != null && data.getLastKnownName() != null) {
                nameIndex.put(data.getLastKnownName().toLowerCase(Locale.ROOT), id);
            }
        }
    }

    @Nonnull
    public PlayerDataModel getPlayerData(@Nonnull UUID uuid) {
        return playerCache.computeIfAbsent(uuid, id -> sanitizePlayerData(backend.loadPlayerData(id)));
    }

    public void savePlayerData(@Nonnull UUID uuid) {
        PlayerDataModel data = playerCache.get(uuid);
        if (data == null) return;
        savePlayerDataAsync(uuid, data);
    }

    public void savePlayerDataAsync(@Nonnull UUID uuid, @Nonnull PlayerDataModel data) {
        PlayerDataModel snapshot = snapshotPlayerData(data);
        submitIoTask(() -> backend.savePlayerData(uuid, snapshot));
    }

    public void unloadPlayer(@Nonnull UUID uuid) {
        PlayerDataModel data = playerCache.get(uuid);
        if (data != null) {
            savePlayerDataAsync(uuid, data);
            flush();
        }
        playerCache.remove(uuid);
    }

    public void updatePlayerName(@Nonnull UUID uuid, @Nonnull String name) {
        String key = name.toLowerCase(Locale.ROOT);
        nameIndex.put(key, uuid);
        PlayerDataModel data = getPlayerData(uuid);
        data.setLastKnownName(name);
        savePlayerDataAsync(uuid, data);
    }

    @Nullable
    public UUID resolvePlayerIdByName(@Nonnull String name) {
        return nameIndex.get(name.toLowerCase(Locale.ROOT));
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
        return warps.get(name.toLowerCase(Locale.ROOT));
    }

    public void setWarp(@Nonnull String name, @Nonnull WarpModel warp) {
        warps.put(name.toLowerCase(Locale.ROOT), warp);
        saveWarpsAsync();
    }

    public boolean deleteWarp(@Nonnull String name) {
        WarpModel removed = warps.remove(name.toLowerCase(Locale.ROOT));
        if (removed != null) {
            saveWarpsAsync();
            return true;
        }
        return false;
    }

    private void saveWarpsAsync() {
        Map<String, WarpModel> snapshot = Map.copyOf(warps);
        submitIoTask(() -> backend.saveWarps(snapshot));
    }

    @Nonnull
    public Map<String, KitModel> getKits() {
        return Map.copyOf(kits);
    }

    @Nullable
    public KitModel getKit(@Nonnull String name) {
        return kits.get(name.toLowerCase(Locale.ROOT));
    }

    public void setKit(@Nonnull String name, @Nonnull KitModel kit) {
        kits.put(name.toLowerCase(Locale.ROOT), kit);
        saveKitsAsync();
    }

    public boolean deleteKit(@Nonnull String name) {
        KitModel removed = kits.remove(name.toLowerCase(Locale.ROOT));
        if (removed != null) {
            saveKitsAsync();
            return true;
        }
        return false;
    }

    private void saveKitsAsync() {
        Map<String, KitModel> snapshot = Map.copyOf(kits);
        submitIoTask(() -> backend.saveKits(snapshot));
    }

    @Nonnull
    public Map<String, ShopModel> getShops() {
        return Map.copyOf(shops);
    }

    @Nullable
    public ShopModel getShop(@Nonnull String name) {
        return shops.get(name.toLowerCase(Locale.ROOT));
    }

    public void setShop(@Nonnull String name, @Nonnull ShopModel shop) {
        shops.put(name.toLowerCase(Locale.ROOT), shop);
        saveShopsAsync();
    }

    public boolean renameShop(@Nonnull String oldName, @Nonnull String newName, @Nonnull ShopModel shop) {
        String oldKey = oldName.toLowerCase(Locale.ROOT);
        String newKey = newName.toLowerCase(Locale.ROOT);
        if (!oldKey.equals(newKey) && shops.containsKey(newKey)) {
            return false;
        }
        shops.remove(oldKey);
        shops.put(newKey, shop);
        saveShopsAsync();
        return true;
    }

    public boolean deleteShop(@Nonnull String name) {
        ShopModel removed = shops.remove(name.toLowerCase(Locale.ROOT));
        if (removed != null) {
            saveShopsAsync();
            return true;
        }
        return false;
    }

    private void saveShopsAsync() {
        Map<String, ShopModel> snapshot = Map.copyOf(shops);
        submitIoTask(() -> backend.saveShops(snapshot));
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
        submitIoTask(() -> backend.saveIpBans(snapshot));
    }

    @Nonnull
    public synchronized AuctionHouseDataModel getAuctionHouseData() {
        AuctionHouseDataModel snapshot = snapshotGson.fromJson(snapshotGson.toJson(auctionHouseData), AuctionHouseDataModel.class);
        return snapshot != null ? snapshot : new AuctionHouseDataModel();
    }

    public synchronized void saveAuctionHouseData(@Nonnull AuctionHouseDataModel data) {
        AuctionHouseDataModel snapshot = snapshotGson.fromJson(snapshotGson.toJson(data), AuctionHouseDataModel.class);
        auctionHouseData = snapshot != null ? snapshot : new AuctionHouseDataModel();
        AuctionHouseDataModel asyncSnapshot = getAuctionHouseData();
        submitIoTask(() -> backend.saveAuctionHouseData(asyncSnapshot));
    }

    @Nonnull
    public synchronized StaffActivityLogDataModel getStaffActivityLog() {
        StaffActivityLogDataModel snapshot = snapshotGson.fromJson(snapshotGson.toJson(staffActivityLog), StaffActivityLogDataModel.class);
        if (snapshot == null) {
            snapshot = new StaffActivityLogDataModel();
        }
        snapshot.sanitize(MAX_STAFF_ACTIVITY_ENTRIES);
        return snapshot;
    }

    public synchronized void addStaffActivity(@Nonnull StaffActivityEntryModel entry) {
        staffActivityLog.getEntries().add(entry);
        staffActivityLog.sanitize(MAX_STAFF_ACTIVITY_ENTRIES);
        StaffActivityLogDataModel snapshot = getStaffActivityLog();
        submitIoTask(() -> backend.saveStaffActivityLog(snapshot));
    }

    @Nonnull
    public java.util.List<StaffActivityEntryModel> listRecentStaffActivity(int limit) {
        StaffActivityLogDataModel snapshot = getStaffActivityLog();
        int end = Math.min(Math.max(0, limit), snapshot.getEntries().size());
        return java.util.List.copyOf(snapshot.getEntries().subList(0, end));
    }

    public void addWarning(@Nonnull UUID uuid, @Nonnull WarningModel warning) {
        PlayerDataModel data = getPlayerData(uuid);
        data.getWarnings().add(warning);
        savePlayerDataAsync(uuid, data);
    }

    public int clearWarnings(@Nonnull UUID uuid) {
        PlayerDataModel data = getPlayerData(uuid);
        int before = data.getWarnings().size();
        data.getWarnings().clear();
        if (before > 0) {
            savePlayerDataAsync(uuid, data);
        }
        return before;
    }

    @Nonnull
    public java.util.List<WarningModel> getWarnings(@Nonnull UUID uuid) {
        return java.util.List.copyOf(getPlayerData(uuid).getWarnings());
    }

    public long countActiveWarnings(@Nonnull UUID uuid) {
        long count = 0L;
        for (WarningModel warning : getPlayerData(uuid).getWarnings()) {
            if (warning != null && warning.isActive()) {
                count++;
            }
        }
        return count;
    }

    public void addStaffCase(@Nonnull UUID uuid, @Nonnull StaffCaseModel staffCase) {
        PlayerDataModel data = getPlayerData(uuid);
        data.getStaffCases().add(staffCase);
        data.sanitizeForStorage();
        savePlayerDataAsync(uuid, data);
    }

    @Nonnull
    public StaffCaseModel addStaffCase(@Nonnull UUID uuid,
                                       @Nonnull String type,
                                       @Nonnull String actor,
                                       @Nullable String detail) {
        PlayerDataModel data = getPlayerData(uuid);
        StaffCaseModel staffCase = new StaffCaseModel(
                nextCaseId(data),
                type,
                actor,
                detail,
                System.currentTimeMillis()
        );
        data.getStaffCases().add(staffCase);
        data.sanitizeForStorage();
        savePlayerDataAsync(uuid, data);
        return staffCase;
    }

    @Nonnull
    private String nextCaseId(@Nonnull PlayerDataModel data) {
        int max = 0;
        for (StaffCaseModel staffCase : data.getStaffCases()) {
            if (staffCase == null || staffCase.getId() == null) continue;
            String id = staffCase.getId().trim();
            if (!id.startsWith("CASE-")) continue;
            try {
                max = Math.max(max, Integer.parseInt(id.substring("CASE-".length())));
            } catch (NumberFormatException ignored) {
            }
        }
        return "CASE-" + String.format(Locale.ROOT, "%04d", max + 1);
    }

    public void addStaffNote(@Nonnull UUID uuid, @Nonnull StaffNoteModel note) {
        PlayerDataModel data = getPlayerData(uuid);
        data.getStaffNotes().add(note);
        data.sanitizeForStorage();
        savePlayerDataAsync(uuid, data);
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
            flush();
            backend.saveWarps(Map.copyOf(warps));
            backend.saveKits(Map.copyOf(kits));
            backend.saveShops(Map.copyOf(shops));
            backend.saveIpBans(Map.copyOf(ipBans));
            backend.saveAuctionHouseData(getAuctionHouseData());
            backend.saveStaffActivityLog(getStaffActivityLog());
            for (Map.Entry<UUID, PlayerDataModel> entry : playerCache.entrySet()) {
                backend.savePlayerData(entry.getKey(), snapshotPlayerData(entry.getValue()));
            }
        } catch (Exception e) {
            Log.error("Failed to flush storage on shutdown: " + e.getMessage());
        } finally {
            ioPool.shutdown();
            try {
                if (!ioPool.awaitTermination(5L, TimeUnit.SECONDS)) {
                    ioPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ioPool.shutdownNow();
            }
            backend.shutdown();
        }
    }

    public void flush() {
        if (ioPool.isShutdown()) {
            return;
        }
        try {
            Future<?> barrier = ioPool.submit(() -> {});
            barrier.get();
        } catch (RejectedExecutionException ignored) {
        } catch (Exception e) {
            Log.warn("Failed to flush storage queue: " + e.getMessage());
        }
    }

    @Nonnull
    private PlayerDataModel sanitizePlayerData(@Nullable PlayerDataModel data) {
        PlayerDataModel safe = data != null ? data : new PlayerDataModel();
        safe.sanitizeForStorage();
        return safe;
    }

    @Nonnull
    private PlayerDataModel snapshotPlayerData(@Nonnull PlayerDataModel data) {
        data.sanitizeForStorage();
        PlayerDataModel snapshot = snapshotGson.fromJson(snapshotGson.toJson(data), PlayerDataModel.class);
        return sanitizePlayerData(snapshot);
    }

    private void submitIoTask(@Nonnull Runnable task) {
        if (ioPool.isShutdown()) {
            task.run();
            return;
        }
        try {
            ioPool.execute(task);
        } catch (RejectedExecutionException ignored) {
            task.run();
        }
    }
}

