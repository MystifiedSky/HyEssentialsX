package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class AutoBroadcastManager {

    private final ConfigManager config;
    private final ScheduledExecutorService scheduler;
    private final Random random = new Random();
    private int nextIndex = 0;

    public AutoBroadcastManager(@Nonnull ConfigManager config) {
        this.config = config;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HyEssentialsX-AutoBroadcast");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (!config.isAutoBroadcastEnabled()) return;
        int interval = Math.max(30, config.getAutoBroadcastIntervalSeconds());
        scheduler.scheduleAtFixedRate(this::tick, interval, interval, TimeUnit.SECONDS);
        Log.info("AutoBroadcast enabled (interval " + interval + "s)");
    }

    public void shutdown() {
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
}
