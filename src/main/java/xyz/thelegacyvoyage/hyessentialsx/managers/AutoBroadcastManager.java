package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.protocol.packets.world.PlaySoundEvent2D;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import org.joml.Vector3d;
import xyz.thelegacyvoyage.hyessentialsx.models.AnnouncementPresetModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.PlaceholderApiUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
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
            Thread t = new Thread(r, "HyEssentialsX-Announcements");
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized void start() {
        if (scheduler.isShutdown() || scheduler.isTerminated()) {
            return;
        }
        if (!config.isAnnouncementsEnabled()) {
            cancelTaskLocked();
            return;
        }
        int interval = Math.max(30, config.getAnnouncementsIntervalSeconds());
        ScheduledFuture<?> existing = task;
        if (existing != null && !existing.isCancelled() && !existing.isDone()
                && scheduledIntervalSeconds == interval) {
            return;
        }
        cancelTaskLocked();
        task = scheduler.scheduleAtFixedRate(this::tick, interval, interval, TimeUnit.SECONDS);
        scheduledIntervalSeconds = interval;
        Log.info("Announcement scheduler enabled (interval " + interval + "s)");
    }

    public synchronized void reload() {
        cancelTaskLocked();
        start();
    }

    public synchronized void shutdown() {
        cancelTaskLocked();
        scheduler.shutdownNow();
    }

    @Nonnull
    public List<AnnouncementPresetModel> presets() {
        return config.getAnnouncementPresets();
    }

    @Nullable
    public AnnouncementPresetModel getPreset(@Nonnull String name) {
        return config.getAnnouncementPreset(name);
    }

    public void savePreset(@Nonnull AnnouncementPresetModel preset) {
        config.saveAnnouncementPreset(preset);
        reload();
    }

    public boolean deletePreset(@Nonnull String name) {
        boolean removed = config.deleteAnnouncementPreset(name);
        if (removed) {
            reload();
        }
        return removed;
    }

    public void setEnabled(boolean enabled) {
        config.setAnnouncementsEnabled(enabled);
        reload();
    }

    public void setRandom(boolean random) {
        config.setAnnouncementsRandom(random);
        reload();
    }

    public void setIntervalSeconds(int seconds) {
        config.setAnnouncementsIntervalSeconds(seconds);
        reload();
    }

    public boolean triggerNext() {
        AnnouncementPresetModel preset = selectNextPreset(false);
        if (preset == null) {
            return false;
        }
        execute(preset, true);
        return true;
    }

    public boolean trigger(@Nonnull String name) {
        AnnouncementPresetModel preset = config.getAnnouncementPreset(name);
        if (preset == null) {
            return false;
        }
        execute(preset, true);
        return true;
    }

    public int execute(@Nonnull AnnouncementPresetModel preset, boolean force) {
        if (!force && !preset.isEnabled()) {
            return 0;
        }
        List<PlayerRef> targets = eligiblePlayers(preset);
        if (targets.isEmpty()) {
            return 0;
        }
        executeChat(preset, targets);
        executeTitle(preset, targets);
        executeNotification(preset, targets);
        executeSound(preset, targets);
        executeParticle(preset, targets);
        executeCommands(preset, targets);
        return targets.size();
    }

    private void tick() {
        try {
            AnnouncementPresetModel preset = selectNextPreset(true);
            if (preset != null) {
                execute(preset, false);
            }
        } catch (Throwable t) {
            Log.warn("Announcement tick failed: " + t.getMessage());
        }
    }

    @Nullable
    private AnnouncementPresetModel selectNextPreset(boolean enabledOnly) {
        List<AnnouncementPresetModel> presets = new ArrayList<>();
        for (AnnouncementPresetModel preset : config.getAnnouncementPresets()) {
            if (preset == null) continue;
            if (enabledOnly && !preset.isEnabled()) continue;
            presets.add(preset);
        }
        if (presets.isEmpty()) {
            return null;
        }
        if (config.isAnnouncementsRandom()) {
            return presets.get(random.nextInt(presets.size()));
        }
        AnnouncementPresetModel preset = presets.get(nextIndex % presets.size());
        nextIndex = (nextIndex + 1) % presets.size();
        return preset;
    }

    @Nonnull
    private List<PlayerRef> eligiblePlayers(@Nonnull AnnouncementPresetModel preset) {
        List<PlayerRef> out = new ArrayList<>();
        Universe universe = Universe.get();
        if (universe == null) {
            return out;
        }
        String permission = preset.getPermission();
        for (PlayerRef player : universe.getPlayers()) {
            if (player == null) continue;
            if (permission.isBlank() || CommandPermissionUtil.hasPermission(player, permission)) {
                out.add(player);
            }
        }
        return out;
    }

    private void executeChat(@Nonnull AnnouncementPresetModel preset, @Nonnull List<PlayerRef> players) {
        for (String raw : preset.getChatMessages()) {
            if (raw == null || raw.isBlank()) continue;
            for (PlayerRef player : players) {
                player.sendMessage(resolveMessage(player, raw));
            }
        }
    }

    private void executeTitle(@Nonnull AnnouncementPresetModel preset, @Nonnull List<PlayerRef> players) {
        AnnouncementPresetModel.TitleAction title = preset.getTitle();
        if (title == null || (title.getPrimary().isBlank() && title.getSecondary().isBlank())) {
            return;
        }
        for (PlayerRef player : players) {
            try {
                EventTitleUtil.showEventTitleToPlayer(
                        player,
                        Messages.m(resolveString(player, title.getPrimary())),
                        Messages.m(resolveString(player, title.getSecondary())),
                        title.isMajor()
                );
            } catch (Throwable t) {
                Log.warn("Failed to send announcement title '" + preset.getName() + "': " + t.getMessage());
            }
        }
    }

    private void executeNotification(@Nonnull AnnouncementPresetModel preset, @Nonnull List<PlayerRef> players) {
        AnnouncementPresetModel.NotificationAction notification = preset.getNotification();
        if (notification == null || (notification.getTitle().isBlank() && notification.getMessage().isBlank())) {
            return;
        }
        NotificationStyle style = notificationStyle(notification.getStyle());
        for (PlayerRef player : players) {
            try {
                Message title = Messages.m(resolveString(player, notification.getTitle()));
                Message message = Messages.m(resolveString(player, notification.getMessage()));
                String icon = notification.getIcon();
                if (icon.isBlank()) {
                    NotificationUtil.sendNotification(player.getPacketHandler(), title, message, style);
                } else {
                    NotificationUtil.sendNotification(player.getPacketHandler(), title, message, icon, style);
                }
            } catch (Throwable t) {
                Log.warn("Failed to send announcement notification '" + preset.getName() + "': " + t.getMessage());
            }
        }
    }

    private void executeSound(@Nonnull AnnouncementPresetModel preset, @Nonnull List<PlayerRef> players) {
        AnnouncementPresetModel.SoundAction sound = preset.getSound();
        if (sound == null || sound.getSoundEventIndex() < 0) {
            return;
        }
        SoundCategory category = soundCategory(sound.getCategory());
        PlaySoundEvent2D packet = new PlaySoundEvent2D(sound.getSoundEventIndex(), category,
                Math.max(0f, sound.getVolume()), Math.max(0f, sound.getPitch()));
        for (PlayerRef player : players) {
            try {
                player.getPacketHandler().writeNoCache(packet);
            } catch (Throwable t) {
                Log.warn("Failed to play announcement sound '" + preset.getName() + "': " + t.getMessage());
            }
        }
    }

    private void executeParticle(@Nonnull AnnouncementPresetModel preset, @Nonnull List<PlayerRef> players) {
        AnnouncementPresetModel.ParticleAction particle = preset.getParticle();
        if (particle == null || particle.getParticleSystemId().isBlank()) {
            return;
        }
        Color color = parseColor(particle.getColor());
        for (PlayerRef player : players) {
            try {
                Vector3d playerPos = player.getTransform().getPosition();
                Position position = new Position(playerPos.x, playerPos.y, playerPos.z);
                SpawnParticleSystem packet = new SpawnParticleSystem(
                        particle.getParticleSystemId(),
                        position,
                        (Direction) null,
                        Math.max(0.1f, particle.getScale()),
                        color,
                        5.0f
                );
                player.getPacketHandler().writeNoCache(packet);
            } catch (Throwable t) {
                Log.warn("Failed to spawn announcement particle '" + preset.getName() + "': " + t.getMessage());
            }
        }
    }

    private void executeCommands(@Nonnull AnnouncementPresetModel preset, @Nonnull List<PlayerRef> players) {
        for (String command : preset.getServerCommands()) {
            if (command == null || command.isBlank()) continue;
            String clean = stripSlash(command);
            if (clean.contains("{player}") || clean.contains("%player%")) {
                for (PlayerRef player : players) {
                    dispatchConsoleCommand(resolveString(player, clean), player);
                }
            } else {
                dispatchConsoleCommand(resolveGlobal(clean), players.get(0));
            }
        }
        for (PlayerRef player : players) {
            for (String command : preset.getPlayerCommands()) {
                if (command == null || command.isBlank()) continue;
                dispatchPlayerCommand(stripSlash(resolveString(player, command)), player);
            }
        }
    }

    @Nonnull
    private Message resolveMessage(@Nonnull PlayerRef player, @Nonnull String input) {
        return PlaceholderApiUtil.apply(player, applyBasicPlaceholders(input, player));
    }

    @Nonnull
    private String resolveString(@Nonnull PlayerRef player, @Nonnull String input) {
        return PlaceholderApiUtil.applyString(player, applyBasicPlaceholders(input, player));
    }

    @Nonnull
    private String resolveGlobal(@Nonnull String input) {
        Universe universe = Universe.get();
        int online = universe == null || universe.getPlayers() == null ? 0 : universe.getPlayers().size();
        return input
                .replace("{online}", String.valueOf(online))
                .replace("%online%", String.valueOf(online));
    }

    @Nonnull
    private String applyBasicPlaceholders(@Nonnull String input, @Nonnull PlayerRef player) {
        String playerName = player.getUsername() == null ? "Player" : player.getUsername();
        return resolveGlobal(input)
                .replace("{player}", playerName)
                .replace("%player%", playerName)
                .replace("{uuid}", player.getUuid().toString())
                .replace("%uuid%", player.getUuid().toString());
    }

    @Nonnull
    private String stripSlash(@Nonnull String command) {
        String trimmed = command.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1).trim() : trimmed;
    }

    private NotificationStyle notificationStyle(@Nonnull String raw) {
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "success" -> NotificationStyle.Success;
            case "warning", "warn" -> NotificationStyle.Warning;
            case "danger", "error" -> NotificationStyle.Danger;
            default -> NotificationStyle.Default;
        };
    }

    private SoundCategory soundCategory(@Nonnull String raw) {
        try {
            return SoundCategory.valueOf(raw.trim());
        } catch (Throwable ignored) {
            return SoundCategory.Music;
        }
    }

    @Nullable
    private Color parseColor(@Nonnull String raw) {
        String color = raw.trim();
        if (color.isBlank()) {
            return null;
        }
        if (color.startsWith("#")) {
            color = color.substring(1);
        }
        if (color.length() != 6) {
            return null;
        }
        try {
            int rgb = Integer.parseInt(color, 16);
            return new Color((byte) ((rgb >> 16) & 0xff), (byte) ((rgb >> 8) & 0xff), (byte) (rgb & 0xff));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void dispatchConsoleCommand(@Nonnull String command, @Nonnull PlayerRef fallback) {
        Object manager = resolveCommandManager();
        if (manager == null || command.isBlank()) return;
        Object consoleSender = resolveConsoleSender();
        if (consoleSender == null) {
            consoleSender = createProxyConsoleSender();
        }
        Object sender = consoleSender != null ? consoleSender : fallback;
        invokeHandleCommand(manager, sender, command, "server");
    }

    private void dispatchPlayerCommand(@Nonnull String command, @Nonnull PlayerRef player) {
        Object manager = resolveCommandManager();
        if (manager == null || command.isBlank()) return;
        invokeHandleCommand(manager, player, command, player.getUsername());
    }

    private void invokeHandleCommand(@Nonnull Object manager,
                                     @Nonnull Object sender,
                                     @Nonnull String command,
                                     @Nonnull String actor) {
        try {
            Method handle = findHandleCommand(manager.getClass(), sender);
            if (handle != null) {
                handle.invoke(manager, sender, command);
            }
        } catch (Throwable t) {
            Log.warn("Failed to execute announcement command as " + actor + " ('" + command + "'): " + t.getMessage());
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
        Class<?> senderInterface = CommandSender.class;
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

    private void cancelTaskLocked() {
        ScheduledFuture<?> existing = task;
        task = null;
        scheduledIntervalSeconds = -1;
        if (existing != null) {
            existing.cancel(false);
        }
    }
}
