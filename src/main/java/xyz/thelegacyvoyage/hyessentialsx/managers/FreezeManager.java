package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.commands.moderation.FreezeCommandWrapper;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FreezeManager {

    private final StorageManager storage;
    private final Map<java.util.UUID, FrozenState> frozenPlayers = new ConcurrentHashMap<>();
    private final java.util.Set<java.util.UUID> pendingInit = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<Object, Object> wrappedOriginals = new ConcurrentHashMap<>();

    public FreezeManager(@Nonnull StorageManager storage) {
        this.storage = storage;
    }

    public boolean isFrozen(@Nonnull java.util.UUID uuid) {
        return frozenPlayers.containsKey(uuid) || pendingInit.contains(uuid);
    }

    public boolean isFrozenOrStored(@Nonnull java.util.UUID uuid) {
        if (isFrozen(uuid)) return true;
        PlayerDataModel data = storage.getPlayerData(uuid);
        return data.isFrozen();
    }

    public boolean freeze(@Nonnull PlayerRef player) {
        FrozenState state = tryCaptureState(player);
        if (state != null) {
            frozenPlayers.put(player.getUuid(), state);
        } else {
            pendingInit.add(player.getUuid());
        }
        PlayerDataModel data = storage.getPlayerData(player.getUuid());
        data.setFrozen(true);
        storage.savePlayerDataAsync(player.getUuid(), data);
        return true;
    }

    public boolean unfreeze(@Nonnull java.util.UUID uuid) {
        FrozenState removed = frozenPlayers.remove(uuid);
        pendingInit.remove(uuid);
        PlayerDataModel data = storage.getPlayerData(uuid);
        data.setFrozen(false);
        storage.savePlayerDataAsync(uuid, data);
        return removed != null;
    }

    public void handleJoin(@Nonnull PlayerRef player) {
        PlayerDataModel data = storage.getPlayerData(player.getUuid());
        if (!data.isFrozen()) return;
        pendingInit.add(player.getUuid());
    }

    public void handleDisconnect(@Nonnull java.util.UUID uuid) {
        frozenPlayers.remove(uuid);
        pendingInit.remove(uuid);
    }

    @Nullable
    public FrozenState getState(@Nonnull java.util.UUID uuid) {
        return frozenPlayers.get(uuid);
    }

    public void updateState(@Nonnull PlayerRef player) {
        if (!pendingInit.contains(player.getUuid())) return;
        FrozenState state = tryCaptureState(player);
        if (state == null) return;
        frozenPlayers.put(player.getUuid(), state);
        pendingInit.remove(player.getUuid());
    }

    @Nonnull
    private FrozenState captureState(@Nonnull PlayerRef player) {
        FrozenState state = tryCaptureState(player);
        return state != null ? state : new FrozenState("default", 0, 0, 0, 0f, 0f);
    }

    @Nullable
    private FrozenState tryCaptureState(@Nonnull PlayerRef player) {
        double x = 0;
        double y = 0;
        double z = 0;
        float yaw = 0f;
        float pitch = 0f;
        String worldName = "default";

        try {
            if (player.getTransform() != null && player.getTransform().getPosition() != null) {
                var pos = player.getTransform().getPosition();
                x = pos.getX();
                y = pos.getY();
                z = pos.getZ();
                var rot = player.getTransform().getRotation();
                if (rot != null) {
                    pitch = rot.getX();
                    yaw = rot.getY();
                }
            } else {
                return null;
            }
        } catch (Exception ignored) {
            return null;
        }

        try {
            World world = null;
            if (player.getWorldUuid() != null) {
                world = Universe.get().getWorld(player.getWorldUuid());
            }
            if (world == null) {
                Ref<EntityStore> ref = player.getReference();
                Store<EntityStore> store = ref.getStore();
                if (store != null && store.getExternalData().getWorld() != null) {
                    world = store.getExternalData().getWorld();
                }
            }
            if (world != null && world.getName() != null && !world.getName().isBlank()) {
                worldName = world.getName();
            }
        } catch (Exception ignored) {
        }

        return new FrozenState(worldName, x, y, z, yaw, pitch);
    }

    public int wrapAllCommands(@Nullable Object commandRegistry) {
        if (commandRegistry == null) return 0;

        Object manager = resolveManager(commandRegistry);
        if (manager == null) return 0;

        Map<?, ?> commandMap = readCommandMap(manager);
        if (commandMap == null || commandMap.isEmpty()) return 0;

        Map<?, ?> aliasMap = readAliasMap(manager);
        int wrapped = 0;

        Map<AbstractCommand, java.util.List<Object>> toWrap = new java.util.HashMap<>();
        Map<Object, CommandHolder> holderTargets = new java.util.HashMap<>();
        for (Map.Entry<?, ?> entry : commandMap.entrySet()) {
            Object value = entry.getValue();
            CommandHolder holder = resolveCommandHolder(value);
            if (holder == null) continue;
            if (holder.command instanceof FreezeCommandWrapper) continue;
            if (holder.isDirect()) {
                toWrap.computeIfAbsent(holder.command, ignored -> new java.util.ArrayList<>()).add(entry.getKey());
            } else {
                holderTargets.putIfAbsent(holder.holder, holder);
            }
        }

        for (Map.Entry<AbstractCommand, java.util.List<Object>> entry : toWrap.entrySet()) {
            AbstractCommand original = entry.getKey();
            AbstractCommand wrappedCmd = new FreezeCommandWrapper(original, this);
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
            AbstractCommand wrappedCmd = new FreezeCommandWrapper(holder.command, this);
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

    public void unwrapAllCommands(@Nullable Object commandRegistry) {
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
                if (current instanceof FreezeCommandWrapper) {
                    if (setCommandOnHolder(cmd, commandField, (AbstractCommand) original)) {
                        restored = true;
                        if (aliasMap != null) {
                            replaceAliases(aliasMap, current, original);
                        }
                    }
                }
            }
            if (!restored) {
                Object current = commandMap.get(cmd);
                if (current instanceof FreezeCommandWrapper) {
                    ((Map<Object, Object>) commandMap).put(cmd, original);
                    if (aliasMap != null) {
                        replaceAliases(aliasMap, current, original);
                    }
                }
            }
        }
        wrappedOriginals.clear();
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
        return registry;
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
        if (value instanceof AbstractCommand cmd) {
            return new CommandHolder(null, cmd, null);
        }
        Field field = findCommandField(value.getClass());
        if (field == null) return null;
        Object current = readField(value, field);
        if (current instanceof AbstractCommand cmd) {
            return new CommandHolder(value, cmd, field);
        }
        return null;
    }

    @Nullable
    private Field findCommandField(@Nonnull Class<?> type) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (AbstractCommand.class.isAssignableFrom(field.getType())) {
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
                                       @Nonnull AbstractCommand cmd) {
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
        private final AbstractCommand command;
        private final Field field;

        private CommandHolder(@Nullable Object holder,
                              @Nonnull AbstractCommand command,
                              @Nullable Field field) {
            this.holder = holder;
            this.command = command;
            this.field = field;
        }

        private boolean isDirect() {
            return field == null;
        }
    }

    public record FrozenState(@Nonnull String worldName,
                              double x,
                              double y,
                              double z,
                              float yaw,
                              float pitch) { }
}
