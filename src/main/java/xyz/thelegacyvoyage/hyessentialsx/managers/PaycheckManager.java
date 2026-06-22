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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class PaycheckManager {

    private static final String PAYCHECK_PERMISSION_PREFIX = "hyessentialsx.paycheck.amount.";
    private static final int MAX_PERMISSION_SCAN = 100000;

    private final ConfigManager config;
    private final EconomyManager economy;
    private final StorageManager storage;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;

    public PaycheckManager(@Nonnull ConfigManager config,
                           @Nonnull EconomyManager economy,
                           @Nonnull StorageManager storage) {
        this.config = config;
        this.economy = economy;
        this.storage = storage;
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
        long intervalMillis = Math.max(60_000L, Math.round(config.getPaycheckIntervalHours() * 3600_000d));
        long now = System.currentTimeMillis();
        for (PlayerRef ref : players) {
            if (ref == null) continue;
            UUID uuid = ref.getUuid();
            PlayerDataModel data = storage.getPlayerData(uuid);
            long last = data.getLastPaycheckAt();
            if (last <= 0L) {
                // Missing timestamp: seed it to avoid free paychecks on restart/older data.
                data.setLastPaycheckAt(now);
                storage.savePlayerDataAsync(uuid, data);
                continue;
            }
            if (last > 0L && now - last < intervalMillis) {
                continue;
            }
            long amount = resolvePaycheckAmount(uuid);
            if (amount <= 0L) {
                data.setLastPaycheckAt(now);
                storage.savePlayerDataAsync(uuid, data);
                continue;
            }
            economy.deposit(uuid, amount);
            data.setLastPaycheckAt(now);
            storage.savePlayerDataAsync(uuid, data);
            Messages.sendPrefixedKey(ref, "economy.paycheck", Map.of(
                    "amount", economy.formatAmount(amount)
            ));
        }
    }

    private long resolvePaycheckAmount(@Nonnull UUID uuid) {
        long base = config.getPaycheckAmount();
        int best = -1;
        if (PermissionsModule.get().hasPermission(uuid, PAYCHECK_PERMISSION_PREFIX + "*")
                || PermissionsModule.get().hasPermission(uuid, PAYCHECK_PERMISSION_PREFIX + "unlimited")) {
            return base;
        }
        for (int i = 1; i <= MAX_PERMISSION_SCAN; i++) {
            if (PermissionsModule.get().hasPermission(uuid, PAYCHECK_PERMISSION_PREFIX + i)) {
                best = i;
            }
        }
        return best > 0 ? best : base;
    }
}

