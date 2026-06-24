package xyz.thelegacyvoyage.hyessentialsx.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import xyz.thelegacyvoyage.hyessentialsx.models.AuctionHouseDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.IpBanModel;
import xyz.thelegacyvoyage.hyessentialsx.models.KitModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.StaffActivityLogDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MongoStorageBackend implements StorageBackend {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final MongoClient client;
    private final MongoCollection<Document> playersCollection;
    private final MongoCollection<Document> warpsCollection;
    private final MongoCollection<Document> kitsCollection;
    private final MongoCollection<Document> shopsCollection;
    private final MongoCollection<Document> ipBansCollection;
    private final MongoCollection<Document> auctionHouseCollection;
    private final MongoCollection<Document> staffActivityCollection;

    public MongoStorageBackend(@Nonnull String uri,
                               @Nonnull String databaseName,
                               @Nonnull String collectionPrefix) {
        String prefix = sanitizeCollectionPrefix(collectionPrefix);
        this.client = MongoClients.create(uri);
        MongoDatabase database = client.getDatabase(databaseName);
        this.playersCollection = database.getCollection(prefix + "players");
        this.warpsCollection = database.getCollection(prefix + "warps");
        this.kitsCollection = database.getCollection(prefix + "kits");
        this.shopsCollection = database.getCollection(prefix + "shops");
        this.ipBansCollection = database.getCollection(prefix + "ipbans");
        this.auctionHouseCollection = database.getCollection(prefix + "auctionhouse");
        this.staffActivityCollection = database.getCollection(prefix + "staff_activity");
        ensureIndexes();
    }

    private void ensureIndexes() {
        try {
            playersCollection.createIndex(Indexes.ascending("uuid"));
            warpsCollection.createIndex(Indexes.ascending("name"));
            kitsCollection.createIndex(Indexes.ascending("name"));
            shopsCollection.createIndex(Indexes.ascending("name"));
            ipBansCollection.createIndex(Indexes.ascending("ip"));
            auctionHouseCollection.createIndex(Indexes.ascending("id"));
            staffActivityCollection.createIndex(Indexes.ascending("id"));
        } catch (Exception e) {
            Log.warn("Failed to ensure MongoDB indexes: " + e.getMessage());
        }
    }

    @Override
    @Nullable
    public PlayerDataModel loadPlayerData(@Nonnull UUID uuid) {
        try {
            Document document = playersCollection.find(Filters.eq("uuid", uuid.toString())).first();
            if (document == null) {
                return new PlayerDataModel();
            }
            String json = document.getString("json");
            if (json == null || json.isBlank()) {
                return new PlayerDataModel();
            }
            PlayerDataModel loaded = gson.fromJson(json, PlayerDataModel.class);
            return loaded != null ? loaded : new PlayerDataModel();
        } catch (Exception e) {
            Log.warn("Failed to load player data from MongoDB for " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public void savePlayerData(@Nonnull UUID uuid, @Nonnull PlayerDataModel data) {
        try {
            Document document = new Document("uuid", uuid.toString())
                    .append("json", gson.toJson(data));
            playersCollection.replaceOne(
                    Filters.eq("uuid", uuid.toString()),
                    document,
                    new ReplaceOptions().upsert(true)
            );
        } catch (Exception e) {
            Log.warn("Failed to save player data to MongoDB for " + uuid + ": " + e.getMessage());
        }
    }

    @Override
    @Nonnull
    public Set<UUID> listPlayerIds() {
        Set<UUID> ids = new HashSet<>();
        try {
            for (Document document : playersCollection.find().projection(Projections.include("uuid"))) {
                String raw = document.getString("uuid");
                if (raw == null || raw.isBlank()) continue;
                try {
                    ids.add(UUID.fromString(raw));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (Exception e) {
            Log.warn("Failed to list player ids from MongoDB: " + e.getMessage());
        }
        return ids;
    }

    @Override
    @Nonnull
    public Map<String, WarpModel> loadWarps() {
        return loadNamedCollection(warpsCollection, "name", WarpModel.class);
    }

    @Override
    public void saveWarps(@Nonnull Map<String, WarpModel> warps) {
        saveNamedCollection(warpsCollection, "name", warps);
    }

    @Override
    @Nonnull
    public Map<String, KitModel> loadKits() {
        return loadNamedCollection(kitsCollection, "name", KitModel.class);
    }

    @Override
    public void saveKits(@Nonnull Map<String, KitModel> kits) {
        saveNamedCollection(kitsCollection, "name", kits);
    }

    @Override
    @Nonnull
    public Map<String, ShopModel> loadShops() {
        return loadNamedCollection(shopsCollection, "name", ShopModel.class);
    }

    @Override
    public void saveShops(@Nonnull Map<String, ShopModel> shops) {
        saveNamedCollection(shopsCollection, "name", shops);
    }

    @Override
    @Nonnull
    public Map<String, IpBanModel> loadIpBans() {
        return loadNamedCollection(ipBansCollection, "ip", IpBanModel.class);
    }

    @Override
    public void saveIpBans(@Nonnull Map<String, IpBanModel> bans) {
        saveNamedCollection(ipBansCollection, "ip", bans);
    }

    @Override
    @Nonnull
    public AuctionHouseDataModel loadAuctionHouseData() {
        try {
            Document document = auctionHouseCollection.find(Filters.eq("id", "global")).first();
            if (document == null) return new AuctionHouseDataModel();
            String json = document.getString("json");
            if (json == null || json.isBlank()) return new AuctionHouseDataModel();
            AuctionHouseDataModel loaded = gson.fromJson(json, AuctionHouseDataModel.class);
            return loaded != null ? loaded : new AuctionHouseDataModel();
        } catch (Exception e) {
            Log.warn("Failed to load auction house data from MongoDB: " + e.getMessage());
            return new AuctionHouseDataModel();
        }
    }

    @Override
    public void saveAuctionHouseData(@Nonnull AuctionHouseDataModel data) {
        try {
            Document document = new Document("id", "global").append("json", gson.toJson(data));
            auctionHouseCollection.replaceOne(
                    Filters.eq("id", "global"),
                    document,
                    new ReplaceOptions().upsert(true)
            );
        } catch (Exception e) {
            Log.warn("Failed to save auction house data to MongoDB: " + e.getMessage());
        }
    }

    @Override
    @Nonnull
    public StaffActivityLogDataModel loadStaffActivityLog() {
        try {
            Document document = staffActivityCollection.find(Filters.eq("id", "global")).first();
            if (document == null) return new StaffActivityLogDataModel();
            String json = document.getString("json");
            if (json == null || json.isBlank()) return new StaffActivityLogDataModel();
            StaffActivityLogDataModel loaded = gson.fromJson(json, StaffActivityLogDataModel.class);
            return loaded != null ? loaded : new StaffActivityLogDataModel();
        } catch (Exception e) {
            Log.warn("Failed to load staff activity from MongoDB: " + e.getMessage());
            return new StaffActivityLogDataModel();
        }
    }

    @Override
    public void saveStaffActivityLog(@Nonnull StaffActivityLogDataModel data) {
        try {
            Document document = new Document("id", "global").append("json", gson.toJson(data));
            staffActivityCollection.replaceOne(
                    Filters.eq("id", "global"),
                    document,
                    new ReplaceOptions().upsert(true)
            );
        } catch (Exception e) {
            Log.warn("Failed to save staff activity to MongoDB: " + e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        try {
            client.close();
        } catch (Exception e) {
            Log.warn("Failed to close MongoDB client: " + e.getMessage());
        }
    }

    @Nonnull
    private <T> Map<String, T> loadNamedCollection(@Nonnull MongoCollection<Document> collection,
                                                   @Nonnull String keyField,
                                                   @Nonnull Class<T> type) {
        Map<String, T> out = new HashMap<>();
        try {
            for (Document document : collection.find()) {
                String key = document.getString(keyField);
                String json = document.getString("json");
                if (key == null || key.isBlank() || json == null || json.isBlank()) {
                    continue;
                }
                T parsed = gson.fromJson(json, type);
                if (parsed != null) {
                    out.put(key, parsed);
                }
            }
        } catch (Exception e) {
            Log.warn("Failed to load data from MongoDB collection '" + collection.getNamespace().getCollectionName()
                    + "': " + e.getMessage());
        }
        return out;
    }

    private <T> void saveNamedCollection(@Nonnull MongoCollection<Document> collection,
                                         @Nonnull String keyField,
                                         @Nonnull Map<String, T> values) {
        try {
            collection.deleteMany(new Document());
            if (values.isEmpty()) {
                return;
            }
            List<Document> documents = new ArrayList<>(values.size());
            for (Map.Entry<String, T> entry : values.entrySet()) {
                String key = entry.getKey();
                if (key == null || key.isBlank()) continue;
                documents.add(new Document(keyField, key).append("json", gson.toJson(entry.getValue())));
            }
            if (!documents.isEmpty()) {
                collection.insertMany(documents);
            }
        } catch (Exception e) {
            Log.warn("Failed to save data to MongoDB collection '" + collection.getNamespace().getCollectionName()
                    + "': " + e.getMessage());
        }
    }

    @Nonnull
    private static String sanitizeCollectionPrefix(@Nullable String raw) {
        String prefix = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (prefix.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(prefix.length());
        for (int i = 0; i < prefix.length(); i++) {
            char c = prefix.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_';
            out.append(valid ? c : '_');
        }
        return out.toString();
    }
}
