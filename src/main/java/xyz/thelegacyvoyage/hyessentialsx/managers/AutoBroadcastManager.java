package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class AutoBroadcastManager {

    private final ConfigManager config;
    private final ScheduledExecutorService scheduler;
    private final Random random = new Random();
    private int nextIndex = 0;
    private ScheduledFuture<?> task;
    private int scheduledIntervalSeconds = -1;

    public AutoBroadcastManager(@Nonnull ConfigManager config) {
        this.config = config;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HyEssentialsX-AutoBroadcast");
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized void start() {
        if (scheduler.isShutdown() || scheduler.isTerminated()) {
            return;
        }
        if (!config.isAutoBroadcastEnabled()) {
            cancelTaskLocked();
            return;
        }
        int interval = Math.max(30, config.getAutoBroadcastIntervalSeconds());
        ScheduledFuture<?> existing = task;
        if (existing != null && !existing.isCancelled() && !existing.isDone()
                && scheduledIntervalSeconds == interval) {
            return;
        }
        cancelTaskLocked();
        task = scheduler.scheduleAtFixedRate(this::tick, interval, interval, TimeUnit.SECONDS);
        scheduledIntervalSeconds = interval;
        Log.info("AutoBroadcast enabled (interval " + interval + "s)");
    }

    public synchronized void reload() {
        cancelTaskLocked();
        start();
    }

    public synchronized void shutdown() {
        cancelTaskLocked();
        scheduler.shutdownNow();
    }

    private void tick() {
        List<String> messages = config.getAutoBroadcastMessages();
        if (messages.isEmpty()) return;

        String message;
        if (config.isAutoBroadcastRandom()) {
            message = messages.get(random.nextInt(messages.size()));
        } else {
            message = messages.get(nextIndex % messages.size());
            nextIndex++;
        }
        Universe.get().sendMessage(Messages.m(message));
    }

    private void cancelTaskLocked() {
        ScheduledFuture<?> existing = task;
        task = null;
        scheduledIntervalSeconds = -1;
        if (existing != null) {
            existing.cancel(false);
        }
    }
}

