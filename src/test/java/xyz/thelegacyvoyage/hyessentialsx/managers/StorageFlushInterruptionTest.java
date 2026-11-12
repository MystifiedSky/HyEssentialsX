package xyz.thelegacyvoyage.hyessentialsx.managers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.thelegacyvoyage.hyessentialsx.storage.JsonStorageBackend;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageFlushInterruptionTest {

    @TempDir
    Path dataFolder;

    @Test
    void interruptedFlushPreservesCallerInterruptFlag() throws Exception {
        StorageManager storage = new StorageManager(new JsonStorageBackend(dataFolder));
        CountDownLatch releaseQueue = new CountDownLatch(1);
        CountDownLatch queueBlocked = new CountDownLatch(1);
        try {
            Field ioPoolField = StorageManager.class.getDeclaredField("ioPool");
            ioPoolField.setAccessible(true);
            ExecutorService ioPool = (ExecutorService) ioPoolField.get(storage);
            ioPool.execute(() -> {
                queueBlocked.countDown();
                try {
                    releaseQueue.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(queueBlocked.await(5L, TimeUnit.SECONDS));

            Thread.currentThread().interrupt();
            storage.flush();

            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
            releaseQueue.countDown();
            storage.shutdown();
        }
    }
}
