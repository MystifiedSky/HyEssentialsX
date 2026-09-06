package xyz.thelegacyvoyage.hyessentialsx.managers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void concurrentDepositsDoNotLoseUpdates() throws Exception {
        ConfigManager config = new ConfigManager(tempDir);
        StorageManager storage = new StorageManager(tempDir, config);
        try {
            EconomyManager economy = new EconomyManager(storage, config);
            UUID playerId = UUID.randomUUID();
            economy.setBalance(playerId, 0L);

            int deposits = 200;
            ExecutorService workers = Executors.newFixedThreadPool(8);
            try {
                for (int i = 0; i < deposits; i++) {
                    workers.submit(() -> economy.deposit(playerId, 1L));
                }
            } finally {
                workers.shutdown();
            }
            assertTrue(workers.awaitTermination(10L, TimeUnit.SECONDS));
            assertEquals(deposits, economy.getBalance(playerId));
        } finally {
            storage.shutdown();
        }
    }
}
