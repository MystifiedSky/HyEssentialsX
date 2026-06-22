package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class PaycheckManager {

    private static final String PAYCHECK_PERMISSION_PREFIX = "hyessentialsx.paycheck.amount.";
    private static final int MAX_PERMISSION_SCAN = 100000;
    private static final long PERMISSION_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5L);

    private final ConfigManager config;
    private final EconomyManager economy;
    private final StorageManager storage;
    private final PlaytimeManager playtime;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;
    private final Map<UUID, CachedPaycheckAmount> cachedAmounts = new ConcurrentHashMap<>();

    public PaycheckManager(@Nonnull ConfigManager config,
                           @Nonnull EconomyManager economy,
                           @Nonnull StorageManager storage,
                           @Nonnull PlaytimeManager playtime) {
        this.config = config;
        this.economy = economy;
        this.storage = storage;
        this.playtime = playtime;
    }

    public void start() {
        if (scheduler != null) return;
        if (!config.isPaycheckEnabled()) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HyEssentialsX-Paycheck");
            t.setDaemon(true);
            return t;
        });
        int intervalSeconds = 60;
        task = scheduler.scheduleAtFixedRate(this::tick, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    public void clearCachedAmounts() {
        cachedAmounts.clear();
    }

    private void tick() {
        if (!economy.isEnabled() || !config.isPaycheckEnabled()) return;
        Universe universe = Universe.get();
        if (universe == null) return;
        for (World world : universe.getWorlds().values()) {
            if (world == null) continue;
            world.execute(() -> checkWorld(world));
        }
    }

    private void checkWorld(@Nonnull World world) {
        Collection<PlayerRef> players = world.getPlayerRefs();
        if (players.isEmpty()) return;
        long intervalSeconds = Math.max(60L, Math.round(config.getPaycheckIntervalHours() * 3600d));
        for (PlayerRef ref : players) {
            if (ref == null) continue;
            UUID uuid = ref.getUuid();
            PlayerDataModel data = storage.getPlayerData(uuid);
            long playtimeSeconds = playtime.getPlaytimeSeconds(uuid);
            long last = data.getLastPaycheckPlaytimeSeconds();
            if (last <= 0L) {
                // Missing playtime checkpoint: seed it to avoid free paychecks on update/older data.
                if (data.getLastPaycheckAt() > 0L) {
                    data.setLastPaycheckPlaytimeSeconds(playtimeSeconds);
                    storage.savePlayerDataAsync(uuid, data);
                    continue;
                }
                data.setLastPaycheckPlaytimeSeconds(playtimeSeconds);
                storage.savePlayerDataAsync(uuid, data);
                continue;
            }
            if (playtimeSeconds < last) {
                data.setLastPaycheckPlaytimeSeconds(playtimeSeconds);
                storage.savePlayerDataAsync(uuid, data);
                continue;
            }
            if (playtimeSeconds - last < intervalSeconds) {
                continue;
            }
            long amount = resolvePaycheckAmount(uuid);
            long nextPaycheckAt = Math.min(playtimeSeconds, last + intervalSeconds);
            if (amount <= 0L) {
                data.setLastPaycheckPlaytimeSeconds(nextPaycheckAt);
                data.setLastPaycheckAt(System.currentTimeMillis());
                storage.savePlayerDataAsync(uuid, data);
                continue;
            }
            economy.deposit(uuid, amount);
            data.setLastPaycheckPlaytimeSeconds(nextPaycheckAt);
            data.setLastPaycheckAt(System.currentTimeMillis());
            storage.savePlayerDataAsync(uuid, data);
            Messages.sendPrefixedKey(ref, "economy.paycheck", Map.of(
                    "amount", economy.formatAmount(amount)
            ));
        }
    }

    private long resolvePaycheckAmount(@Nonnull UUID uuid) {
        long now = System.currentTimeMillis();
        CachedPaycheckAmount cached = cachedAmounts.get(uuid);
        if (cached != null && now - cached.cachedAtMs() <= PERMISSION_CACHE_TTL_MS) {
            return cached.amount();
        }

        long base = config.getPaycheckAmount();
        if (PermissionsModule.get().hasPermission(uuid, PAYCHECK_PERMISSION_PREFIX + "*")
                || PermissionsModule.get().hasPermission(uuid, PAYCHECK_PERMISSION_PREFIX + "unlimited")) {
            cachedAmounts.put(uuid, new CachedPaycheckAmount(base, now));
            return base;
        }
        int best = -1;
        for (int i = 1; i <= MAX_PERMISSION_SCAN; i++) {
            if (PermissionsModule.get().hasPermission(uuid, PAYCHECK_PERMISSION_PREFIX + i)) {
                best = i;
            }
        }
        long resolved = best > 0 ? best : base;
        cachedAmounts.put(uuid, new CachedPaycheckAmount(resolved, now));
        return resolved;
    }

    private record CachedPaycheckAmount(long amount, long cachedAtMs) {}
}

