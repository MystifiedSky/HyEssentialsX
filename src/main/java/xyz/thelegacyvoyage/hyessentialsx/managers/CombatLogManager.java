package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.component.Ref;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.commands.combat.CombatLogCommandWrapper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatLogManager {

    public static final String BYPASS_PERMISSION = "hyessentialsx.combatlog.bypass";
    private static final long NOTIFY_INTERVAL_MS = 15_000L;

    private final Map<UUID, Long> combatTimers = new ConcurrentHashMap<>();
    private final Map<UUID, String> combatWorlds = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerRef> playerRefs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastNotify = new ConcurrentHashMap<>();
    private final Map<Object, Object> wrappedOriginals = new ConcurrentHashMap<>();
    private long lastTickTime = 0L;
    private volatile boolean shutdownInProgress = false;

    private ConfigManager config;

    public CombatLogManager(@Nonnull ConfigManager config) {
        this.config = config;
    }

    public void setConfig(@Nonnull ConfigManager config) {
        this.config = config;
    }

    public void setShutdownInProgress(boolean shutdownInProgress) {
        this.shutdownInProgress = shutdownInProgress;
    }

    public boolean isEnabled() {
        return config != null && config.isCombatLogEnabled();
    }

    public boolean isInCombat(@Nonnull UUID uuid) {
        if (!isEnabled()) return false;
        Long lastCombat = combatTimers.get(uuid);
        if (lastCombat == null) return false;
        long elapsed = System.currentTimeMillis() - lastCombat;
        return elapsed <= (long) config.getCombatLogTimeSeconds() * 1000L;
    }

    public int getRemainingSeconds(@Nonnull UUID uuid) {
        Long lastCombat = combatTimers.get(uuid);
        if (lastCombat == null) return 0;
        long elapsed = System.currentTimeMillis() - lastCombat;
        long remaining = (long) config.getCombatLogTimeSeconds() * 1000L - elapsed;
        return remaining > 0L ? (int) (remaining / 1000L) : 0;
    }

    public void tag(@Nonnull PlayerRef player, @Nullable String worldName) {
        if (!isEnabled()) return;
        UUID uuid = player.getUuid();
        boolean fresh = !isInCombat(uuid);
        combatTimers.put(uuid, System.currentTimeMillis());
        playerRefs.put(uuid, player);
        if (worldName != null && !worldName.isBlank()) {
            combatWorlds.put(uuid, worldName);
        }
        if (fresh) {
            sendCombatEnter(player);
            if (config.isCombatLogShowTitle()) {
                showCombatTitle(player, config.getCombatLogTimeSeconds());
            }
        }
    }

    public void remove(@Nonnull UUID uuid) {
        combatTimers.remove(uuid);
        combatWorlds.remove(uuid);
        playerRefs.remove(uuid);
        lastNotify.remove(uuid);
    }

    public void tickExpiry() {
        if (!isEnabled()) return;
        long now = System.currentTimeMillis();
        if (now - lastTickTime < 1000L) return;
        lastTickTime = now;

        Iterator<Map.Entry<UUID, Long>> it = combatTimers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            UUID uuid = entry.getKey();
            long lastCombat = entry.getValue();
            long elapsed = now - lastCombat;
            long durationMs = (long) config.getCombatLogTimeSeconds() * 1000L;
            PlayerRef ref = playerRefs.get(uuid);

            if (elapsed > durationMs) {
                if (ref != null) {
                    sendCombatExit(ref);
                    if (config.isCombatLogShowTitle()) {
                        hideCombatTitle(ref);
                    }
                }
                it.remove();
                combatWorlds.remove(uuid);
                playerRefs.remove(uuid);
                lastNotify.remove(uuid);
                continue;
            }

            if (ref != null) {
                Long last = lastNotify.get(uuid);
                if (last == null || now - last >= NOTIFY_INTERVAL_MS) {
                    int remaining = (int) ((durationMs - elapsed) / 1000L);
                    sendCombatTimeRemaining(ref, remaining);
                    lastNotify.put(uuid, now);
                }
            }
        }
    }

    public void handleDisconnect(@Nonnull PlayerRef player) {
        if (!isEnabled()) return;
        if (shutdownInProgress || isServerShuttingDown()) {
            remove(player.getUuid());
            return;
        }
        UUID uuid = player.getUuid();
        if (!isInCombat(uuid)) return;

        String worldName = combatWorlds.get(uuid);
        if (worldName != null) {
            World world = Universe.get().getWorld(worldName);
            if (world != null) {
                EntityStore entityStore = world.getEntityStore();
                Ref<EntityStore> ref = entityStore.getRefFromUUID(uuid);
                if (ref != null && ref.isValid()) {
                    world.execute(() -> applyCombatLogPenalty(entityStore, ref));
                }
            }
        }

        broadcastCombatLog(player.getUsername());
        remove(uuid);
    }

    public void removeIfDead(@Nonnull UUID uuid) {
        if (!isEnabled()) return;
        if (isInCombat(uuid)) {
            remove(uuid);
        }
    }

    public int wrapBlockedCommands(@Nullable Object commandRegistry) {
        if (!isEnabled() || !config.isCombatLogBlockCommands()) return 0;
        if (commandRegistry == null) return 0;

        Object manager = resolveManager(commandRegistry);
        if (manager == null) return 0;

        Map<?, ?> commandMap = readCommandMap(manager);
        if (commandMap == null || commandMap.isEmpty()) return 0;

        Map<?, ?> aliasMap = readAliasMap(manager);
        int wrapped = 0;
        var blocked = config.getCombatLogBlockedCommands();
        if (blocked.isEmpty()) return 0;

        Map<com.hypixel.hytale.server.core.command.system.AbstractCommand, java.util.List<Object>> toWrap =
                new java.util.HashMap<>();
        Map<Object, CommandHolder> holderTargets = new java.util.HashMap<>();
        for (Map.Entry<?, ?> entry : commandMap.entrySet()) {
            Object value = entry.getValue();
            CommandHolder holder = resolveCommandHolder(value);
            if (holder == null) continue;
            if (holder.command instanceof xyz.thelegacyvoyage.hyessentialsx.commands.combat.CombatLogCommandWrapper) {
                continue;
            }
            if (!isBlockedCommand(holder.command, blocked)) {
                continue;
            }
            if (holder.isDirect()) {
                toWrap.computeIfAbsent(holder.command, ignored -> new java.util.ArrayList<>()).add(entry.getKey());
            } else {
                holderTargets.putIfAbsent(holder.holder, holder);
            }
        }

        for (Map.Entry<com.hypixel.hytale.server.core.command.system.AbstractCommand, java.util.List<Object>> entry
                : toWrap.entrySet()) {
            com.hypixel.hytale.server.core.command.system.AbstractCommand original = entry.getKey();
            var wrappedCmd = new xyz.thelegacyvoyage.hyessentialsx.commands.combat.CombatLogCommandWrapper(
                    original, this, config
            );
            for (Object key : entry.getValue()) {
                if (key == null) continue;
                wrappedOriginals.putIfAbsent(key, original);
                ((Map<Object, Object>) commandMap).put(key, wrappedCmd);
                wrapped++;
            }
            if (aliasMap != null) {
                replaceAliases(aliasMap, original, wrappedCmd);
            }
        }
        for (CommandHolder holder : holderTargets.values()) {
            if (holder == null || holder.holder == null || holder.command == null || holder.field == null) continue;
            if (wrappedOriginals.containsKey(holder.holder)) continue;
            var wrappedCmd = new xyz.thelegacyvoyage.hyessentialsx.commands.combat.CombatLogCommandWrapper(
                    holder.command, this, config
            );
            if (setCommandOnHolder(holder.holder, holder.field, wrappedCmd)) {
                wrappedOriginals.putIfAbsent(holder.holder, holder.command);
                wrapped++;
                if (aliasMap != null) {
                    replaceAliases(aliasMap, holder.command, wrappedCmd);
                }
            }
        }
        return wrapped;
    }

    public void unwrapBlockedCommands(@Nullable Object commandRegistry) {
        if (commandRegistry == null || wrappedOriginals.isEmpty()) return;
        Object manager = resolveManager(commandRegistry);
        if (manager == null) return;
        Map<?, ?> commandMap = readCommandMap(manager);
        if (commandMap == null) return;
        Map<?, ?> aliasMap = readAliasMap(manager);
        for (Map.Entry<Object, Object> entry : wrappedOriginals.entrySet()) {
            Object cmd = entry.getKey();
            Object original = entry.getValue();
            if (cmd == null || original == null) continue;
            boolean restored = false;
            Field commandField = findCommandField(cmd.getClass());
            if (commandField != null) {
                Object current = readField(cmd, commandField);
                if (current instanceof xyz.thelegacyvoyage.hyessentialsx.commands.combat.CombatLogCommandWrapper) {
                    if (setCommandOnHolder(cmd, commandField,
                            (com.hypixel.hytale.server.core.command.system.AbstractCommand) original)) {
                        restored = true;
                        if (aliasMap != null) {
                            replaceAliases(aliasMap, current, original);
                        }
                    }
                }
            }
            if (!restored) {
                Object current = commandMap.get(cmd);
                if (current instanceof xyz.thelegacyvoyage.hyessentialsx.commands.combat.CombatLogCommandWrapper) {
                    ((Map<Object, Object>) commandMap).put(cmd, original);
                    if (aliasMap != null) {
                        replaceAliases(aliasMap, current, original);
                    }
                }
            }
        }
        wrappedOriginals.clear();
    }

    private boolean isBlockedCommand(@Nonnull com.hypixel.hytale.server.core.command.system.AbstractCommand cmd,
                                     @Nonnull java.util.List<String> blocked) {
        String name = cmd.getName();
        for (String raw : blocked) {
            if (raw == null || raw.isBlank()) continue;
            if (name != null && name.equalsIgnoreCase(raw)) return true;
            for (String alias : cmd.getAliases()) {
                if (alias != null && alias.equalsIgnoreCase(raw)) return true;
            }
        }
        return false;
    }

    public boolean isBlockedCommand(@Nonnull AbstractCommand cmd) {
        if (!isEnabled() || !config.isCombatLogBlockCommands()) return false;
        return isBlockedCommand(cmd, config.getCombatLogBlockedCommands());
    }

    public boolean isServerShuttingDown() {
        try {
            HytaleServer server = HytaleServer.get();
            return server != null && server.isShuttingDown();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Nonnull
    public AbstractCommand wrapIfBlocked(@Nonnull AbstractCommand cmd) {
        if (isBlockedCommand(cmd)) {
            return new CombatLogCommandWrapper(cmd, this, config);
        }
        return cmd;
    }

    @Nullable
    private Object resolveManager(@Nonnull Object registry) {
        String name = registry.getClass().getName();
        if (name.contains("CommandManager")) {
            return registry;
        }
        Field field = findField(registry.getClass(), "commandManager", "manager");
        if (field != null) {
            try {
                field.setAccessible(true);
                Object value = field.get(registry);
                if (value != null) return value;
            } catch (Exception ignored) {
            }
        }
        for (var method : registry.getClass().getMethods()) {
            if (method.getParameterCount() != 0) continue;
            String methodName = method.getName().toLowerCase();
            if (!methodName.contains("commandmanager")) continue;
            try {
                Object value = method.invoke(registry);
                if (value != null) return value;
            } catch (Exception ignored) {
            }
        }
        Object manager = resolveGlobalCommandManager();
        if (manager != null) {
            return manager;
        }
        return registry;
    }

    @Nullable
    private Object resolveGlobalCommandManager() {
        try {
            Class<?> cls = Class.forName("com.hypixel.hytale.server.core.command.system.CommandManager");
            java.lang.reflect.Method get = cls.getMethod("get");
            return get.invoke(null);
        } catch (Exception ignored) {
        }
        try {
            Class<?> cls = Class.forName("com.hypixel.hytale.server.core.command.CommandManager");
            java.lang.reflect.Method get = cls.getMethod("get");
            return get.invoke(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private Map<?, ?> readCommandMap(@Nonnull Object manager) {
        Field field = findField(manager.getClass(),
                "commandRegistration",
                "commandRegistrations",
                "commandMap",
                "commands",
                "registeredCommands");
        Map<?, ?> map = readMapField(manager, field);
        return map != null ? map : findFirstMap(manager);
    }

    @Nullable
    private Map<?, ?> readAliasMap(@Nonnull Object manager) {
        Field field = findField(manager.getClass(),
                "aliases",
                "aliasMap",
                "commandAliases",
                "aliasMappings");
        return readMapField(manager, field);
    }

    @Nullable
    private Map<?, ?> readMapField(@Nonnull Object target, @Nullable Field field) {
        if (field == null) return null;
        try {
            field.setAccessible(true);
            Object value = field.get(target);
            if (value instanceof Map<?, ?> map) {
                return map;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    private Map<?, ?> findFirstMap(@Nonnull Object target) {
        for (Field field : target.getClass().getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(field.getType())) continue;
            Map<?, ?> map = readMapField(target, field);
            if (map != null) return map;
        }
        return null;
    }

    @Nullable
    private Field findField(@Nonnull Class<?> type, @Nonnull String... names) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (String name : names) {
                try {
                    return current.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private void replaceAliases(@Nonnull Map<?, ?> aliasMap, @Nonnull Object from, @Nonnull Object to) {
        for (Map.Entry<?, ?> entry : aliasMap.entrySet()) {
            if (entry.getValue() == from) {
                ((Map<Object, Object>) aliasMap).put(entry.getKey(), to);
            }
        }
    }

    @Nullable
    private CommandHolder resolveCommandHolder(@Nonnull Object value) {
        if (value instanceof com.hypixel.hytale.server.core.command.system.AbstractCommand cmd) {
            return new CommandHolder(null, cmd, null);
        }
        Field field = findCommandField(value.getClass());
        if (field == null) return null;
        Object current = readField(value, field);
        if (current instanceof com.hypixel.hytale.server.core.command.system.AbstractCommand cmd) {
            return new CommandHolder(value, cmd, field);
        }
        return null;
    }

    @Nullable
    private Field findCommandField(@Nonnull Class<?> type) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (com.hypixel.hytale.server.core.command.system.AbstractCommand.class
                        .isAssignableFrom(field.getType())) {
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    @Nullable
    private Object readField(@Nonnull Object target, @Nonnull Field field) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean setCommandOnHolder(@Nonnull Object holder,
                                       @Nonnull Field field,
                                       @Nonnull com.hypixel.hytale.server.core.command.system.AbstractCommand cmd) {
        try {
            field.setAccessible(true);
            field.set(holder, cmd);
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    private static final class CommandHolder {
        private final Object holder;
        private final com.hypixel.hytale.server.core.command.system.AbstractCommand command;
        private final Field field;

        private CommandHolder(@Nullable Object holder,
                              @Nonnull com.hypixel.hytale.server.core.command.system.AbstractCommand command,
                              @Nullable Field field) {
            this.holder = holder;
            this.command = command;
            this.field = field;
        }

        private boolean isDirect() {
            return field == null;
        }
    }

    private void applyCombatLogPenalty(@Nonnull EntityStore store, @Nonnull Ref<EntityStore> ref) {
        try {
            Damage damage = new Damage(Damage.NULL_SOURCE, DamageCause.COMMAND, 99999.0F);
            DamageSystems.executeDamage(ref, store.getStore(), damage);
        } catch (Throwable ignored) {
        }
    }

    private void broadcastCombatLog(@Nullable String playerName) {
        if (playerName == null || playerName.isBlank()) return;
        Message message = buildPrefixedMessage(null, config.getCombatLogBroadcastMessage(),
                Map.of("player", playerName));
        for (PlayerRef target : Universe.get().getPlayers()) {
            if (target != null) {
                target.sendMessage(message);
            }
        }
    }

    private void sendCombatEnter(@Nonnull PlayerRef player) {
        player.sendMessage(buildPrefixedMessage(player, config.getCombatLogEnterMessage(), Map.of()));
    }

    private void sendCombatExit(@Nonnull PlayerRef player) {
        player.sendMessage(buildPrefixedMessage(player, config.getCombatLogExitMessage(), Map.of()));
    }

    private void sendCombatTimeRemaining(@Nonnull PlayerRef player, int seconds) {
        player.sendMessage(buildPrefixedMessage(player, config.getCombatLogTimeRemainingMessage(),
                Map.of("seconds", String.valueOf(seconds))));
    }

    private void showCombatTitle(@Nonnull PlayerRef player, int seconds) {
        try {
            String main = resolveText(player, config.getCombatLogTitleMain(), Map.of());
            String sub = resolveText(player, config.getCombatLogTitleSub(),
                    Map.of("seconds", String.valueOf(seconds)));
            EventTitleUtil.showEventTitleToPlayer(player, Messages.m(main), Messages.m(sub), false);
        } catch (Throwable ignored) {
        }
    }

    private void hideCombatTitle(@Nonnull PlayerRef player) {
        try {
            EventTitleUtil.hideEventTitleFromPlayer(player, 0.5f);
        } catch (Throwable ignored) {
        }
    }

    @Nonnull
    private String replace(@Nonnull String base, @Nonnull String key, @Nonnull String value) {
        return base.replace(key, value);
    }

    @Nonnull
    public Message buildPrefixedMessage(@Nullable PlayerRef player,
                                        @Nonnull String raw,
                                        @Nonnull Map<String, String> placeholders) {
        String prefix = resolveText(player, config.getCombatLogPrefix(), Map.of());
        String body = resolveText(player, raw, placeholders);
        return Messages.m(prefix + body);
    }

    @Nonnull
    public String resolveText(@Nullable PlayerRef player,
                              @Nonnull String raw,
                              @Nonnull Map<String, String> placeholders) {
        String resolved = Messages.tr(player, raw, placeholders);
        if (resolved.equals(raw)) {
            resolved = applyPlaceholders(raw, placeholders);
        }
        return resolved;
    }

    @Nonnull
    private String applyPlaceholders(@Nonnull String raw, @Nonnull Map<String, String> placeholders) {
        String out = raw;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            out = out.replace("{" + key + "}", value);
            out = out.replace("%" + key + "%", value);
        }
        return out;
    }
}
