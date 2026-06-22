package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlaytimeRewardModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.PlaceholderApiUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class PlaytimeRewardManager {

    private final ConfigManager config;
    private final PlaytimeManager playtime;
    private final StorageManager storage;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;

    public PlaytimeRewardManager(@Nonnull ConfigManager config,
                                 @Nonnull PlaytimeManager playtime,
                                 @Nonnull StorageManager storage) {
        this.config = config;
        this.playtime = playtime;
        this.storage = storage;
    }

    public void start() {
        shutdown();
        if (!config.isPlaytimeRewardsEnabled() || !config.isPlaytimeRewardsAutoClaim()) {
            return;
        }
        int interval = Math.max(5, config.getPlaytimeRewardsCheckIntervalSeconds());
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "HyEssentialsX-PlaytimeRewards");
            thread.setDaemon(true);
            return thread;
        });
        task = scheduler.scheduleAtFixedRate(this::tick, interval, interval, TimeUnit.SECONDS);
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

    public void processPlayer(@Nonnull PlayerRef playerRef) {
        if (!config.isPlaytimeRewardsEnabled()) {
            return;
        }
        List<PlaytimeRewardModel> rewards = config.getPlaytimeRewards();
        if (rewards.isEmpty()) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        PlayerDataModel data = storage.getPlayerData(playerId);
        long playtimeSeconds = playtime.getPlaytimeSeconds(playerId);
        boolean changed = false;

        for (PlaytimeRewardModel reward : rewards) {
            if (!reward.isAutoClaim()) {
                continue;
            }
            String rewardId = reward.getId().trim();
            if (rewardId.isBlank()) {
                continue;
            }
            if (reward.getRequiredSeconds() > playtimeSeconds) {
                continue;
            }
            if (data.hasClaimedPlaytimeReward(rewardId)) {
                continue;
            }

            if (grantReward(playerRef, reward, playtimeSeconds)) {
                data.addClaimedPlaytimeReward(rewardId);
                changed = true;
            }
        }

        if (changed) {
            storage.savePlayerDataAsync(playerId, data);
        }
    }

    private void tick() {
        try {
            Universe universe = Universe.get();
            if (universe == null) {
                return;
            }
            for (World world : universe.getWorlds().values()) {
                if (world == null) continue;
                world.execute(() -> {
                    for (PlayerRef playerRef : world.getPlayerRefs()) {
                        if (playerRef != null) {
                            processPlayer(playerRef);
                        }
                    }
                });
            }
        } catch (Exception e) {
            Log.warn("Playtime reward tick failed: " + e.getMessage());
        }
    }

    private boolean grantReward(@Nonnull PlayerRef playerRef,
                                @Nonnull PlaytimeRewardModel reward,
                                long playtimeSeconds) {
        String rewardId = reward.getId().trim();
        if (rewardId.isBlank()) {
            return false;
        }

        boolean executedCommand = false;
        for (String rawCommand : reward.getCommands()) {
            if (rawCommand == null || rawCommand.isBlank()) {
                continue;
            }
            String parsed = applyPlaceholders(rawCommand, playerRef, reward, playtimeSeconds).trim();
            if (parsed.startsWith("/")) {
                parsed = parsed.substring(1);
            }
            if (parsed.isBlank()) {
                continue;
            }
            dispatchConsoleCommand(parsed, playerRef);
            executedCommand = true;
        }
        boolean sentBroadcast = false;
        String broadcast = reward.getBroadcastMessage();
        if (!broadcast.isBlank()) {
            String message = applyPlaceholders(broadcast, playerRef, reward, playtimeSeconds);
            Universe universe = Universe.get();
            if (universe != null) {
                universe.sendMessage(Messages.m(message));
                sentBroadcast = true;
            }
        }
        return executedCommand || sentBroadcast;
    }

    @Nonnull
    private String applyPlaceholders(@Nonnull String input,
                                     @Nonnull PlayerRef playerRef,
                                     @Nonnull PlaytimeRewardModel reward,
                                     long playtimeSeconds) {
        String playerName = playerRef.getUsername();
        String rewardId = reward.getId();
        String required = TimeUtil.formatDurationSeconds(reward.getRequiredSeconds());
        String played = TimeUtil.formatDurationSeconds(playtimeSeconds);
        String cost = String.valueOf(reward.getRequiredCost());
        String rank = reward.getRank();
        String resolved = input
                .replace("%player%", playerName)
                .replace("{player}", playerName)
                .replace("%reward%", rewardId)
                .replace("{reward}", rewardId)
                .replace("%required%", required)
                .replace("{required}", required)
                .replace("%time%", played)
                .replace("{time}", played)
                .replace("%cost%", cost)
                .replace("{cost}", cost)
                .replace("%rank%", rank)
                .replace("{rank}", rank);
        return PlaceholderApiUtil.applyString(playerRef, resolved);
    }

    private void dispatchConsoleCommand(@Nonnull String command, @Nonnull PlayerRef fallback) {
        Object manager = resolveCommandManager();
        if (manager == null) return;
        Object consoleSender = resolveConsoleSender();
        if (consoleSender == null) {
            consoleSender = createProxyConsoleSender();
        }
        Object sender = consoleSender != null ? consoleSender : fallback;
        try {
            Method handle = findHandleCommand(manager.getClass(), sender);
            if (handle != null) {
                handle.invoke(manager, sender, command);
                return;
            }
            if (consoleSender == null && fallback != null) {
                Method playerHandle = findHandleCommand(manager.getClass(), fallback);
                if (playerHandle != null) {
                    playerHandle.invoke(manager, fallback, command);
                }
            }
        } catch (Throwable t) {
            Log.warn("Failed to execute playtime reward command '" + command + "': " + t.getMessage());
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

    @Nullable
    private Object createProxyConsoleSender() {
        Class<?> senderInterface = loadCommandSenderInterface();
        if (senderInterface == null || !senderInterface.isInterface()) return null;
        return java.lang.reflect.Proxy.newProxyInstance(
                senderInterface.getClassLoader(),
                new Class<?>[]{senderInterface},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getDisplayName".equals(name)) return "Console";
                    if ("getUuid".equals(name)) return new UUID(0L, 0L);
                    if ("hasPermission".equals(name)) return true;
                    if ("sendMessage".equals(name)) return null;
                    Class<?> returnType = method.getReturnType();
                    if (returnType == Void.TYPE) return null;
                    if (!returnType.isPrimitive()) return null;
                    if (returnType == Boolean.TYPE) return false;
                    if (returnType == Byte.TYPE) return (byte) 0;
                    if (returnType == Short.TYPE) return (short) 0;
                    if (returnType == Integer.TYPE) return 0;
                    if (returnType == Long.TYPE) return 0L;
                    if (returnType == Float.TYPE) return 0f;
                    if (returnType == Double.TYPE) return 0d;
                    if (returnType == Character.TYPE) return '\0';
                    return null;
                }
        );
    }

    @Nullable
    private Class<?> loadCommandSenderInterface() {
        try {
            return Class.forName("com.hypixel.hytale.server.core.command.system.CommandSender");
        } catch (Throwable ignored) {
        }
        try {
            return Class.forName("com.hypixel.hytale.server.core.command.CommandSender");
        } catch (Throwable ignored) {
            return null;
        }
    }
}
