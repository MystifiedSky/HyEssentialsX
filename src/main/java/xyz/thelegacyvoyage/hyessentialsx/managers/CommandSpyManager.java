package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.commands.chat.CommandSpyCommandWrapper;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.StaffActionUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CommandSpyManager {

    public static final String PERMISSION_NODE = "hyessentialsx.commandspy";
    public static final String BYPASS_PERMISSION = "hyessentialsx.commandspy.bypass";

    private final ConfigManager config;
    private final StorageManager storage;
    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<String>> filters = new ConcurrentHashMap<>();
    private final Map<UUID, RecentCommand> recentNotifications = new ConcurrentHashMap<>();

    public CommandSpyManager(@Nonnull ConfigManager config, @Nonnull StorageManager storage) {
        this.config = config;
        this.storage = storage;
    }

    @Nonnull
    public AbstractCommand wrap(@Nonnull AbstractCommand command) {
        if (!config.isCommandSpyEnabled() || command instanceof CommandSpyCommandWrapper) {
            return command;
        }
        return new CommandSpyCommandWrapper(command, this);
    }

    public boolean toggle(@Nonnull UUID playerId) {
        if (enabled.remove(playerId)) {
            return false;
        }
        enabled.add(playerId);
        return true;
    }

    public boolean isEnabled(@Nonnull UUID playerId) {
        return enabled.contains(playerId);
    }

    public void clear(@Nonnull UUID playerId) {
        enabled.remove(playerId);
        filters.remove(playerId);
    }

    public void addFilter(@Nonnull UUID playerId, @Nonnull String command) {
        filters.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet()).add(normalize(command));
    }

    public void removeFilter(@Nonnull UUID playerId, @Nonnull String command) {
        Set<String> set = filters.get(playerId);
        if (set != null) {
            set.remove(normalize(command));
        }
    }

    @Nonnull
    public Set<String> filters(@Nonnull UUID playerId) {
        Set<String> set = filters.get(playerId);
        return set == null ? Set.of() : new HashSet<>(set);
    }

    public void notifyCommand(@Nonnull CommandSender sender, @Nonnull String commandName, @Nonnull String commandText) {
        if (!config.isCommandSpyEnabled()) {
            return;
        }
        PlayerRef actor = resolvePlayer(sender);
        if (actor == null) {
            return;
        }
        notifyCommand(actor, commandName, commandText);
    }

    public void notifyRawCommand(@Nonnull PlayerRef actor, @Nonnull String rawCommand) {
        String normalizedText = rawCommand.startsWith("/") ? rawCommand : "/" + rawCommand;
        notifyCommand(actor, normalize(normalizedText), normalizedText);
    }

    public void notifyCommand(@Nonnull PlayerRef actor, @Nonnull String commandName, @Nonnull String commandText) {
        if (!config.isCommandSpyEnabled()) {
            return;
        }
        String normalized = normalize(commandName);
        if (normalized.isBlank() || isIgnored(normalized) || hasBypass(actor)) {
            return;
        }
        String text = commandText == null || commandText.isBlank() ? "/" + normalized : commandText;
        if (isDuplicate(actor.getUuid(), text)) {
            return;
        }
        for (PlayerRef watcher : Universe.get().getPlayers()) {
            if (watcher == null || watcher.getUuid().equals(actor.getUuid())) continue;
            if (!enabled.contains(watcher.getUuid())) continue;
            if (!CommandPermissionUtil.hasPermission(watcher, PERMISSION_NODE)) continue;
            Set<String> watcherFilters = filters.get(watcher.getUuid());
            if (watcherFilters != null && !watcherFilters.isEmpty() && !watcherFilters.contains(normalized)) {
                continue;
            }
            Messages.sendPrefixedKey(watcher, "commandspy.format", Map.of(
                    "player", actor.getUsername(),
                    "command", text
            ));
        }
        if (config.isCommandSpyLogToActivity()) {
            StaffActionUtil.log(storage, actor.getUsername(), "Command Spy", actor.getUuid(), actor.getUsername(), text);
        }
    }

    private boolean isDuplicate(@Nonnull UUID actorId, @Nonnull String commandText) {
        long now = System.currentTimeMillis();
        String normalizedText = commandText.trim().toLowerCase(Locale.ROOT);
        RecentCommand previous = recentNotifications.put(actorId, new RecentCommand(normalizedText, now));
        return previous != null
                && previous.commandText().equals(normalizedText)
                && now - previous.timestampMillis() <= 750L;
    }

    private boolean isIgnored(@Nonnull String commandName) {
        for (String ignored : config.getCommandSpyIgnoredCommands()) {
            if (commandName.equals(normalize(ignored))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBypass(@Nonnull PlayerRef actor) {
        return CommandPermissionUtil.hasPermission(actor, BYPASS_PERMISSION);
    }

    @Nonnull
    private static String normalize(@Nullable String command) {
        if (command == null) {
            return "";
        }
        String out = command.trim().toLowerCase(Locale.ROOT);
        while (out.startsWith("/")) {
            out = out.substring(1);
        }
        int space = out.indexOf(' ');
        return space >= 0 ? out.substring(0, space) : out;
    }

    @Nullable
    private PlayerRef resolvePlayer(@Nonnull CommandSender sender) {
        if (sender instanceof PlayerRef playerRef) {
            return playerRef;
        }
        try {
            Method method = sender.getClass().getMethod("getPlayerRef");
            Object value = method.invoke(sender);
            if (value instanceof PlayerRef playerRef) {
                return playerRef;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private record RecentCommand(@Nonnull String commandText, long timestampMillis) {
    }
}
