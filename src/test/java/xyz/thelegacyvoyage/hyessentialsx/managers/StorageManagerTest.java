package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.google.gson.Gson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import xyz.thelegacyvoyage.hyessentialsx.models.AuctionHouseDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.IpBanModel;
import xyz.thelegacyvoyage.hyessentialsx.models.KitModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;
import xyz.thelegacyvoyage.hyessentialsx.storage.StorageBackend;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageManagerTest {

    private StorageManager storage;

    @AfterEach
    void tearDown() {
        if (storage != null) {
            storage.shutdown();
        }
    }

    @Test
    void savePlayerDataAsyncSnapshotsStateAtSubmissionTime() throws Exception {
        BlockingBackend backend = new BlockingBackend();
        storage = new StorageManager(backend);

        UUID uuid = UUID.randomUUID();
        PlayerDataModel data = storage.getPlayerData(uuid);
        data.setLastKnownName("before");
        data.getCommandCooldowns().put("home", 1L);

        backend.blockNextSave();
        storage.savePlayerDataAsync(uuid, data);
        assertTrue(backend.awaitSaveStarted());

        data.setLastKnownName("after");
        data.getCommandCooldowns().put("warp", 2L);

        backend.releaseBlockedSave();
        storage.flush();

        PlayerDataModel saved = backend.getStoredPlayer(uuid);
        assertEquals("before", saved.getLastKnownName());
        assertTrue(saved.getCommandCooldowns().containsKey("home"));
        assertFalse(saved.getCommandCooldowns().containsKey("warp"));
    }

    @Test
    void reloadCachesWaitsForQueuedWritesBeforeClearingPlayerCache() throws Exception {
        BlockingBackend backend = new BlockingBackend();
        storage = new StorageManager(backend);

        UUID uuid = UUID.randomUUID();
        PlayerDataModel data = storage.getPlayerData(uuid);
        data.setLastKnownName("persisted");

        backend.blockNextSave();
        storage.savePlayerDataAsync(uuid, data);
        assertTrue(backend.awaitSaveStarted());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> reloadFuture = executor.submit(storage::reloadCaches);
            assertThrows(TimeoutException.class, () -> reloadFuture.get(150L, TimeUnit.MILLISECONDS));

            backend.releaseBlockedSave();
            reloadFuture.get(5L, TimeUnit.SECONDS);

            PlayerDataModel reloaded = storage.getPlayerData(uuid);
            assertEquals("persisted", reloaded.getLastKnownName());
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class BlockingBackend implements StorageBackend {

        private static final Gson GSON = new Gson();

        private final Map<UUID, PlayerDataModel> players = new HashMap<>();
        private volatile CountDownLatch saveStarted = new CountDownLatch(0);
        private volatile CountDownLatch allowSave = new CountDownLatch(0);

        void blockNextSave() {
            saveStarted = new CountDownLatch(1);
            allowSave = new CountDownLatch(1);
        }

        boolean awaitSaveStarted() throws InterruptedException {
            return saveStarted.await(5L, TimeUnit.SECONDS);
        }

        void releaseBlockedSave() {
            allowSave.countDown();
        }

        @Override
        public PlayerDataModel loadPlayerData(UUID uuid) {
            return copy(players.get(uuid));
        }

        @Override
        public void savePlayerData(UUID uuid, PlayerDataModel data) {
            saveStarted.countDown();
            try {
                allowSave.await(5L, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            players.put(uuid, copy(data));
        }

        PlayerDataModel getStoredPlayer(UUID uuid) {
            PlayerDataModel data = players.get(uuid);
            if (data == null) {
                return new PlayerDataModel();
            }
            return copy(data);
        }

        @Override
        public Set<UUID> listPlayerIds() {
            return Set.copyOf(players.keySet());
        }

        @Override
        public Map<String, WarpModel> loadWarps() {
            return Map.of();
        }

        @Override
        public void saveWarps(Map<String, WarpModel> warps) {}

        @Override
        public Map<String, KitModel> loadKits() {
            return Map.of();
        }

        @Override
        public void saveKits(Map<String, KitModel> kits) {}

        @Override
        public Map<String, ShopModel> loadShops() {
            return Map.of();
        }

        @Override
        public void saveShops(Map<String, ShopModel> shops) {}

        @Override
        public Map<String, IpBanModel> loadIpBans() {
            return Map.of();
        }

        @Override
        public void saveIpBans(Map<String, IpBanModel> bans) {}

        @Override
        public AuctionHouseDataModel loadAuctionHouseData() {
            return new AuctionHouseDataModel();
        }

        @Override
        public void saveAuctionHouseData(AuctionHouseDataModel data) {}

        @Override
        public void shutdown() {}

        private PlayerDataModel copy(PlayerDataModel data) {
            if (data == null) {
                return null;
            }
            return GSON.fromJson(GSON.toJson(data), PlayerDataModel.class);
        }
    }
}
