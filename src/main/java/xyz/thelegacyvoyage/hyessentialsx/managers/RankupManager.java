package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.RankupTier;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class RankupManager {

    private final ConfigManager config;
    private final EconomyManager economy;
    private final StorageManager storage;
    private final PlaytimeManager playtime;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> autoTask;
    private final Map<UUID, PendingRankup> pendingConfirm = new HashMap<>();

    public RankupManager(@Nonnull ConfigManager config,
                         @Nonnull EconomyManager economy,
                         @Nonnull StorageManager storage,
                         @Nonnull PlaytimeManager playtime) {
        this.config = config;
        this.economy = economy;
        this.storage = storage;
        this.playtime = playtime;
    }

    public boolean isEnabled() {
        return config.isRankupEnabled();
    }

    public void start() {
        if (scheduler != null) return;
        if (!config.isRankupAutoEnabled()) return;
        int interval = Math.max(10, config.getRankupAutoCheckSeconds());
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HyEssentialsX-Rankup");
            t.setDaemon(true);
            return t;
        });
        autoTask = scheduler.scheduleAtFixedRate(this::checkAutoRankups, interval, interval, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (autoTask != null) {
            autoTask.cancel(false);
            autoTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void checkAutoRankups() {
        if (!isEnabled()) return;
        Universe universe = Universe.get();
        if (universe == null) return;
        for (World world : universe.getWorlds().values()) {
            if (world == null) continue;
            world.execute(() -> checkWorldAutoRankups(world));
        }
    }

    private void checkWorldAutoRankups(@Nonnull World world) {
        Collection<PlayerRef> players = world.getPlayerRefs();
        if (players.isEmpty()) return;
        for (PlayerRef playerRef : players) {
            if (playerRef == null) continue;
            attemptAutoRankup(playerRef);
        }
    }

    private void attemptAutoRankup(@Nonnull PlayerRef playerRef) {
        RankupTier next = getNextTier(playerRef.getUuid());
        if (next == null) return;
        Eligibility eligibility = checkEligibility(playerRef, next);
        if (!eligibility.playtimeMet) return;
        if (config.isRankupCurrencyEnabled() && eligibility.cost > 0L) {
            if (!config.isRankupAutoUseCurrency()) return;
            if (!eligibility.currencyMet) return;
        }
        performRankup(playerRef, next, config.isRankupAutoUseCurrency());
    }

    @Nullable
    public RankupTier getNextTier(@Nonnull UUID uuid) {
        List<RankupTier> tiers = config.getRankupTiers();
        if (tiers.isEmpty()) return null;
        int currentIndex = getCurrentTierIndex(uuid, tiers);
        int nextIndex = currentIndex + 1;
        if (nextIndex < 0 || nextIndex >= tiers.size()) return null;
        return tiers.get(nextIndex);
    }

    public Eligibility checkEligibility(@Nonnull PlayerRef playerRef, @Nonnull RankupTier tier) {
        long playtimeSeconds = playtime.getPlaytimeSeconds(playerRef.getUuid());
        boolean playtimeMet = !config.isRankupPlaytimeEnabled() || tier.getPlaytimeSeconds() <= 0L
                || playtimeSeconds >= tier.getPlaytimeSeconds();
        long cost = tier.getCost();
        boolean currencyMet = !config.isRankupCurrencyEnabled() || cost <= 0L
                || economy.getBalance(playerRef.getUuid()) >= cost;
        return new Eligibility(playtimeMet, currencyMet, playtimeSeconds, cost);
    }

    public boolean performRankup(@Nonnull PlayerRef playerRef, @Nonnull RankupTier tier, boolean useCurrency) {
        if (!isEnabled()) return false;
        Eligibility eligibility = checkEligibility(playerRef, tier);
        if (!eligibility.playtimeMet) return false;
        if (config.isRankupCurrencyEnabled() && eligibility.cost > 0L) {
            if (!useCurrency) return false;
            if (!eligibility.currencyMet) return false;
            if (!economy.withdraw(playerRef.getUuid(), eligibility.cost)) return false;
        }

        PlayerDataModel data = storage.getPlayerData(playerRef.getUuid());
        data.setRankupTier(tier.getRank());
        storage.savePlayerDataAsync(playerRef.getUuid(), data);

        runRankupCommands(playerRef, tier);
        Messages.sendPrefixedKey(playerRef, "rankup.success", Map.of("rank", tier.getRank()));
        return true;
    }

    public void setPendingConfirm(@Nonnull UUID uuid, @Nonnull RankupTier tier) {
        long expiresAt = System.currentTimeMillis() + Math.max(5, config.getRankupConfirmTimeoutSeconds()) * 1000L;
        pendingConfirm.put(uuid, new PendingRankup(tier.getRank(), expiresAt));
    }

    @Nullable
    public RankupTier getPendingTier(@Nonnull UUID uuid) {
        PendingRankup pending = pendingConfirm.get(uuid);
        if (pending == null) return null;
        if (System.currentTimeMillis() > pending.expiresAt) {
            pendingConfirm.remove(uuid);
            return null;
        }
        for (RankupTier tier : config.getRankupTiers()) {
            if (tier.getRank().equalsIgnoreCase(pending.rank)) return tier;
        }
        return null;
    }

    public void clearPending(@Nonnull UUID uuid) {
        pendingConfirm.remove(uuid);
    }

    private int getCurrentTierIndex(@Nonnull UUID uuid, @Nonnull List<RankupTier> tiers) {
        Set<String> groups = PermissionsModule.get().getGroupsForUser(uuid);
        if (groups != null && !groups.isEmpty()) {
            int best = -1;
            for (String g : groups) {
                int idx = indexForGroup(g, tiers);
                if (idx > best) best = idx;
            }
            if (best >= 0) return best;
        }

        PlayerDataModel data = storage.getPlayerData(uuid);
        String stored = data.getRankupTier();
        if (stored != null && !stored.isBlank()) {
            int idx = indexForGroup(stored, tiers);
            if (idx >= 0) return idx;
        }
        return -1;
    }

    private int indexForGroup(@Nonnull String group, @Nonnull List<RankupTier> tiers) {
        String key = group.trim().toLowerCase();
        for (int i = 0; i < tiers.size(); i++) {
            RankupTier tier = tiers.get(i);
            if (tier.getRank().equalsIgnoreCase(key)) return i;
        }
        return -1;
    }

    private void runRankupCommands(@Nonnull PlayerRef playerRef, @Nonnull RankupTier tier) {
        List<String> commands = tier.getCommands();
        if (commands.isEmpty()) return;
        String playerName = playerRef.getUsername();
        String rankName = tier.getRank();
        for (String raw : commands) {
            if (raw == null || raw.isBlank()) continue;
            String cmd = raw.replace("{player}", playerName)
                    .replace("{rank}", rankName);
            dispatchConsoleCommand(cmd, playerRef);
        }
    }

    private void dispatchConsoleCommand(@Nonnull String command, @Nonnull PlayerRef fallback) {
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        Object manager = resolveCommandManager();
        if (manager == null) return;
        Object consoleSender = resolveConsoleSender();
        Object sender = consoleSender != null ? consoleSender : fallback;
        try {
            Method handle = findHandleCommand(manager.getClass(), sender);
            if (handle != null) {
                handle.invoke(manager, sender, cmd);
                return;
            }
            if (!(sender instanceof PlayerRef) && fallback != null) {
                Method playerHandle = findHandleCommand(manager.getClass(), fallback);
                if (playerHandle != null) {
                    playerHandle.invoke(manager, fallback, cmd);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Nullable
    private Method findHandleCommand(@Nonnull Class<?> managerClass, @Nonnull Object sender) {
        Class<?> senderClass = sender.getClass();
        for (Method method : managerClass.getMethods()) {
            if (!method.getName().equals("handleCommand")) continue;
            if (method.getParameterCount() != 2) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params[1] != String.class) continue;
            if (params[0].isAssignableFrom(senderClass)) {
                return method;
            }
        }
        return null;
    }

    @Nullable
    private Object resolveCommandManager() {
        try {
            Class<?> cls = Class.forName("com.hypixel.hytale.server.core.command.system.CommandManager");
            Method get = cls.getMethod("get");
            return get.invoke(null);
        } catch (Throwable ignored) {
        }
        try {
            Class<?> cls = Class.forName("com.hypixel.hytale.server.core.command.CommandManager");
            Method get = cls.getMethod("get");
            return get.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private Object resolveConsoleSender() {
        try {
            Class<?> moduleClass = Class.forName("com.hypixel.hytale.server.core.console.ConsoleModule");
            Method get = moduleClass.getMethod("get");
            Object module = get.invoke(null);
            if (module == null) return null;
            for (String name : List.of("getConsoleSender", "getSender", "getConsole")) {
                try {
                    Method method = module.getClass().getMethod(name);
                    Object sender = method.invoke(module);
                    if (sender != null) return sender;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static final class Eligibility {
        public final boolean playtimeMet;
        public final boolean currencyMet;
        public final long playtimeSeconds;
        public final long cost;

        public Eligibility(boolean playtimeMet, boolean currencyMet, long playtimeSeconds, long cost) {
            this.playtimeMet = playtimeMet;
            this.currencyMet = currencyMet;
            this.playtimeSeconds = playtimeSeconds;
            this.cost = cost;
        }
    }

    private static final class PendingRankup {
        private final String rank;
        private final long expiresAt;

        private PendingRankup(@Nonnull String rank, long expiresAt) {
            this.rank = rank;
            this.expiresAt = expiresAt;
        }
    }
}
