package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSleep;
import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSomnolence;
import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSleep.MorningWakeUp;
import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSleep.NoddingOff;
import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSleep.Slumber;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSleep;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSlumber;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSomnolence;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.gameplay.SleepConfig;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class SleepPercentManager {

    private final ConfigManager config;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> sleepCheckTask;
    private final Map<String, Set<UUID>> lastSleepingByWorld = new HashMap<>();

    public SleepPercentManager(@Nonnull ConfigManager config) {
        this.config = config;
    }

    public void start() {
        if (scheduler != null) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HyEssentialsX-SleepPercent");
            t.setDaemon(true);
            return t;
        });
        sleepCheckTask = scheduler.scheduleAtFixedRate(this::checkSleepingPlayers, 1L, 1L, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (sleepCheckTask != null) {
            sleepCheckTask.cancel(false);
            sleepCheckTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void checkSleepingPlayers() {
        try {
            int requiredPercentage = config.getSleepPercentage();
            if (requiredPercentage >= 100) return;

            Universe universe = Universe.get();
            if (universe == null) return;

            for (World world : universe.getWorlds().values()) {
                if (world == null) continue;
                world.execute(() -> checkWorldSleep(world, requiredPercentage));
            }
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Sleep percentage check failed: " + e.getMessage());
        }
    }

    private void checkWorldSleep(@Nonnull World world, int requiredPercentage) {
        try {
            EntityStore entityStore = world.getEntityStore();
            if (entityStore == null) return;
            Store<EntityStore> store = entityStore.getStore();
            if (store == null) return;

            WorldSomnolence worldSomnolence = store.getResource(WorldSomnolence.getResourceType());
            if (worldSomnolence == null) return;

            WorldSleep worldSleep = worldSomnolence.getState();
            if (worldSleep instanceof WorldSlumber) return;

            Collection<PlayerRef> playerRefs = world.getPlayerRefs();
            if (playerRefs.isEmpty()) return;

            int totalPlayers = playerRefs.size();
            int sleepingPlayers = 0;
            Map<UUID, PlayerRef> playerById = new HashMap<>();

            WorldTimeResource timeResource = store.getResource(WorldTimeResource.getResourceType());
            Instant gameTime = timeResource != null ? timeResource.getGameTime() : Instant.now();

            Set<UUID> sleepingNow = new HashSet<>();
            Iterator<PlayerRef> it = playerRefs.iterator();
            while (it.hasNext()) {
                PlayerRef playerRef = it.next();
                if (playerRef == null) continue;
                playerById.put(playerRef.getUuid(), playerRef);
                Ref<EntityStore> entityRef = playerRef.getReference();
                if (entityRef == null) continue;
                PlayerSomnolence somnolence = store.getComponent(entityRef, PlayerSomnolence.getComponentType());
                if (somnolence == null) continue;
                PlayerSleep sleepState = somnolence.getSleepState();
                if (sleepState instanceof Slumber) {
                    sleepingPlayers++;
                    sleepingNow.add(playerRef.getUuid());
                } else if (sleepState instanceof NoddingOff) {
                    NoddingOff noddingOff = (NoddingOff) sleepState;
                    Instant readyTime = noddingOff.realTimeStart().plusMillis(3200L);
                    if (Instant.now().isAfter(readyTime)) {
                        sleepingPlayers++;
                        sleepingNow.add(playerRef.getUuid());
                    }
                } else if (sleepState instanceof MorningWakeUp) {
                    MorningWakeUp wakeUp = (MorningWakeUp) sleepState;
                    Instant readyTime = wakeUp.gameTimeStart().plus(Duration.ofHours(1L));
                    if (gameTime.isAfter(readyTime)) {
                        sleepingPlayers++;
                        sleepingNow.add(playerRef.getUuid());
                    }
                }
            }

            int needed = calculateNeededPlayers(totalPlayers, requiredPercentage);
            maybeAnnounceSleepers(world, playerRefs, playerById, sleepingNow, sleepingPlayers, needed);

            if (sleepingPlayers == 0) return;
            int currentPercentage = sleepingPlayers * 100 / totalPlayers;
            if (currentPercentage >= requiredPercentage) {
                triggerSlumber(store, world, worldSomnolence);
            }
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Sleep percentage world check failed: " + e.getMessage());
        }
    }

    private void triggerSlumber(@Nonnull Store<EntityStore> store,
                                @Nonnull World world,
                                @Nonnull WorldSomnolence worldSomnolence) {
        if (worldSomnolence.getState() instanceof WorldSlumber) return;

        WorldTimeResource timeResource = store.getResource(WorldTimeResource.getResourceType());
        if (timeResource == null) {
            Log.warn("[HyEssentialsX] WorldTimeResource is null for sleep percentage.");
            return;
        }

        SleepConfig sleepConfig = world.getGameplayConfig().getWorldConfig().getSleepConfig();
        float wakeUpHour = sleepConfig.getWakeUpHour();
        Instant now = timeResource.getGameTime();
        Instant wakeUp = computeWakeupInstant(now, wakeUpHour);
        timeResource.setGameTime(wakeUp, world, store);

        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null) continue;
            PlayerSomnolence somnolence = store.getComponent(entityRef, PlayerSomnolence.getComponentType());
            if (somnolence == null) continue;
            PlayerSleep sleepState = somnolence.getSleepState();
            if (!(sleepState instanceof NoddingOff) && !(sleepState instanceof Slumber)) continue;
            PlayerSomnolence wakeUpState = new PlayerSomnolence(new MorningWakeUp(wakeUp));
            store.putComponent(entityRef, PlayerSomnolence.getComponentType(), wakeUpState);
        }

        Log.info("[HyEssentialsX] Sleep percentage: skipped to morning.");
    }

    private Instant computeWakeupInstant(@Nonnull Instant now, float wakeUpHour) {
        LocalDateTime nowDateTime = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        int hour = (int) wakeUpHour;
        float minuteFraction = wakeUpHour - (float) hour;
        int minutes = (int) (minuteFraction * 60.0F);
        LocalDateTime wakeUpDateTime = nowDateTime.toLocalDate().plusDays(1L).atTime(hour, minutes);
        return wakeUpDateTime.toInstant(ZoneOffset.UTC);
    }

    private int calculateNeededPlayers(int totalPlayers, int requiredPercentage) {
        if (requiredPercentage <= 0) return 0;
        if (requiredPercentage >= 100) return totalPlayers;
        return Math.max(1, (int) Math.ceil(totalPlayers * (requiredPercentage / 100.0)));
    }

    private void maybeAnnounceSleepers(@Nonnull World world,
                                       @Nonnull Collection<PlayerRef> playerRefs,
                                       @Nonnull Map<UUID, PlayerRef> playerById,
                                       @Nonnull Set<UUID> sleepingNow,
                                       int sleepingPlayers,
                                       int neededPlayers) {
        if (!config.isSleepChatEnabled()) return;
        if (sleepingNow.isEmpty()) {
            lastSleepingByWorld.remove(world.getName());
            return;
        }
        String worldKey = world.getName();
        Set<UUID> previous = lastSleepingByWorld.getOrDefault(worldKey, Set.of());
        if (previous.isEmpty()) {
            lastSleepingByWorld.put(worldKey, new HashSet<>(sleepingNow));
            for (UUID sleeper : sleepingNow) {
                PlayerRef ref = playerById.get(sleeper);
                if (ref == null) continue;
                broadcastSleepMessage(playerRefs, ref.getUsername(), sleepingPlayers, neededPlayers);
            }
            return;
        }

        Set<UUID> newlySleeping = new HashSet<>(sleepingNow);
        newlySleeping.removeAll(previous);
        if (!newlySleeping.isEmpty()) {
            for (UUID sleeper : newlySleeping) {
                PlayerRef ref = playerById.get(sleeper);
                if (ref == null) continue;
                broadcastSleepMessage(playerRefs, ref.getUsername(), sleepingPlayers, neededPlayers);
            }
        }
        lastSleepingByWorld.put(worldKey, new HashSet<>(sleepingNow));
    }

    private void broadcastSleepMessage(@Nonnull Collection<PlayerRef> playerRefs,
                                       @Nonnull String playerName,
                                       int sleepingPlayers,
                                       int neededPlayers) {
        Map<String, String> placeholders = Map.of(
                "player", playerName,
                "sleeping", Integer.toString(sleepingPlayers),
                "needed", Integer.toString(neededPlayers)
        );
        for (PlayerRef playerRef : playerRefs) {
            if (playerRef == null) continue;
            Messages.sendPrefixedKey(playerRef, "sleep.message", placeholders);
        }
    }
}

