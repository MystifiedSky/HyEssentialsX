package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

public final class NicknameManager {

    public static final String COLOR_PERMISSION = "hyessentialsx.nick.color";
    public static final String FORMAT_PERMISSION = "hyessentialsx.nick.format";

    private final StorageManager storage;
    private final ConfigManager config;

    public NicknameManager(@Nonnull StorageManager storage, @Nonnull ConfigManager config) {
        this.storage = storage;
        this.config = config;
    }

    @Nonnull
    public Result setNickname(@Nonnull PlayerRef actor, @Nonnull PlayerRef target, @Nonnull String rawNickname) {
        String nickname = sanitizeAllowedFormatting(actor, rawNickname.trim());
        String plain = plain(nickname);
        if (plain.length() < config.getNicknameMinLength()) {
            return Result.tooShort();
        }
        if (plain.length() > config.getNicknameMaxLength()) {
            return Result.tooLong();
        }
        if (isBlacklisted(plain)) {
            return Result.blacklisted();
        }
        if (config.isNicknamePreventDuplicates() && isDuplicate(target.getUuid(), plain)) {
            return Result.duplicate();
        }

        PlayerDataModel data = storage.getPlayerData(target.getUuid());
        data.setNickname(nickname);
        storage.savePlayerDataAsync(target.getUuid(), data);
        applyDisplayName(target);
        return Result.ok(nickname);
    }

    public void clearNickname(@Nonnull PlayerRef player) {
        PlayerDataModel data = storage.getPlayerData(player.getUuid());
        data.setNickname(null);
        storage.savePlayerDataAsync(player.getUuid(), data);
        applyDisplayName(player);
    }

    public void clearNickname(@Nonnull UUID playerId) {
        PlayerDataModel data = storage.getPlayerData(playerId);
        data.setNickname(null);
        storage.savePlayerDataAsync(playerId, data);
    }

    @Nullable
    public String getNickname(@Nonnull UUID playerId) {
        return storage.getPlayerData(playerId).getNickname();
    }

    @Nonnull
    public String displayName(@Nonnull PlayerRef player) {
        String nickname = getNickname(player.getUuid());
        return nickname == null || nickname.isBlank() ? player.getUsername() : nickname;
    }

    public boolean hasNickname(@Nonnull PlayerRef player) {
        String nickname = getNickname(player.getUuid());
        return nickname != null && !nickname.isBlank();
    }

    public void applyDisplayName(@Nonnull PlayerRef player) {
        String display = displayName(player);
        World world = resolveWorld(player);
        if (world == null) {
            return;
        }
        world.execute(() -> applyDisplayNameNow(player, display));
    }

    private void applyDisplayNameNow(@Nonnull PlayerRef player, @Nonnull String display) {
        Ref<EntityStore> ref = player.getReference();
        Store<EntityStore> store = ref != null ? ref.getStore() : null;
        if (ref == null || store == null) {
            return;
        }
        applyDisplayNameComponent(store, ref, display);
        applyNameplateComponent(store, ref, display);
    }

    @Nullable
    private World resolveWorld(@Nonnull PlayerRef player) {
        try {
            if (player.getWorldUuid() != null) {
                World world = Universe.get().getWorld(player.getWorldUuid());
                if (world != null) {
                    return world;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            Ref<EntityStore> ref = player.getReference();
            Store<EntityStore> store = ref != null ? ref.getStore() : null;
            if (store != null && store.getExternalData() != null) {
                return store.getExternalData().getWorld();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    public Match findByNicknameOrName(@Nonnull String query) {
        String normalized = plain(query);
        if (normalized.isBlank()) {
            return null;
        }
        for (UUID id : storage.listPlayerIds()) {
            PlayerDataModel data = storage.getPlayerData(id);
            String nick = data.getNickname();
            if (nick != null && plain(nick).equalsIgnoreCase(normalized)) {
                return new Match(id, safeName(data), nick, true);
            }
            String name = data.getLastKnownName();
            if (name != null && name.equalsIgnoreCase(query.trim())) {
                return new Match(id, name, nick, false);
            }
        }
        return null;
    }

    @Nonnull
    public static String plain(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("(?i)&[0-9a-fk-or]", "")
                .replaceAll("(?i)<[^>]+>", "")
                .trim();
    }

    @Nonnull
    private String sanitizeAllowedFormatting(@Nonnull PlayerRef actor, @Nonnull String value) {
        String out = value;
        if (!CommandPermissionUtil.hasPermission(actor, COLOR_PERMISSION)) {
            out = out.replaceAll("(?i)&[0-9a-f]", "");
            out = out.replaceAll("(?i)<#[0-9a-f]{6}>", "");
        }
        if (!CommandPermissionUtil.hasPermission(actor, FORMAT_PERMISSION)) {
            out = out.replaceAll("(?i)&[k-or]", "");
            out = out.replaceAll("(?i)</?[^>]+>", "");
        }
        return out.trim();
    }

    private boolean isBlacklisted(@Nonnull String plain) {
        String normalized = plain.toLowerCase(Locale.ROOT);
        for (String word : config.getNicknameBlacklist()) {
            if (word != null && !word.isBlank() && normalized.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean isDuplicate(@Nonnull UUID targetId, @Nonnull String plain) {
        for (UUID id : storage.listPlayerIds()) {
            if (targetId.equals(id)) {
                continue;
            }
            PlayerDataModel data = storage.getPlayerData(id);
            String nick = data.getNickname();
            if (nick != null && plain(nick).equalsIgnoreCase(plain)) {
                return true;
            }
            String real = data.getLastKnownName();
            if (real != null && real.equalsIgnoreCase(plain)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void applyDisplayNameComponent(@Nonnull Store<EntityStore> store,
                                           @Nonnull Ref<EntityStore> ref,
                                           @Nonnull String displayName) {
        try {
            Class<?> cls = Class.forName("com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent");
            Method getType = cls.getMethod("getComponentType");
            @SuppressWarnings("rawtypes")
            com.hypixel.hytale.component.ComponentType type =
                    (com.hypixel.hytale.component.ComponentType) getType.invoke(null);
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object component = ((Store) store).getComponent(ref, type);
            Message message = Messages.m(displayName);
            if (component == null) {
                Object created = createDisplayNameComponent(cls, message);
                if (created != null) {
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    Store rawStore = (Store) store;
                    rawStore.addComponent(ref, type, (com.hypixel.hytale.component.Component) created);
                }
                return;
            }
            if (trySetDisplayName(component, message)) {
                return;
            }
            Field field = cls.getDeclaredField("displayName");
            field.setAccessible(true);
            field.set(component, message);
        } catch (Throwable ignored) {
        }
    }

    private void applyNameplateComponent(@Nonnull Store<EntityStore> store,
                                         @Nonnull Ref<EntityStore> ref,
                                         @Nonnull String displayName) {
        Nameplate nameplate = store.getComponent(ref, Nameplate.getComponentType());
        if (nameplate == null) {
            store.addComponent(ref, Nameplate.getComponentType(), new Nameplate(displayName));
        } else {
            nameplate.setText(displayName);
        }
    }

    @Nullable
    private Object createDisplayNameComponent(@Nonnull Class<?> cls, @Nonnull Message message) {
        for (Constructor<?> constructor : cls.getDeclaredConstructors()) {
            try {
                constructor.setAccessible(true);
                Class<?>[] types = constructor.getParameterTypes();
                if (types.length == 1 && types[0].isAssignableFrom(Message.class)) {
                    return constructor.newInstance(message);
                }
                if (types.length == 0) {
                    Object instance = constructor.newInstance();
                    trySetDisplayName(instance, message);
                    return instance;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private boolean trySetDisplayName(@Nonnull Object component, @Nonnull Message message) {
        for (String methodName : new String[]{"setDisplayName", "setName"}) {
            try {
                Method method = component.getClass().getMethod(methodName, Message.class);
                method.invoke(component, message);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @Nonnull
    private String safeName(@Nonnull PlayerDataModel data) {
        String name = data.getLastKnownName();
        return name == null || name.isBlank() ? Messages.tr(null, "unknown", java.util.Map.of()) : name;
    }

    public record Match(@Nonnull UUID uuid, @Nonnull String realName, @Nullable String nickname, boolean matchedNickname) {
    }

    public record Result(boolean success, @Nullable String nickname, @Nonnull String reasonKey) {
        private static Result ok(@Nonnull String nickname) {
            return new Result(true, nickname, "");
        }

        private static Result tooShort() {
            return new Result(false, null, "nick.too_short");
        }

        private static Result tooLong() {
            return new Result(false, null, "nick.too_long");
        }

        private static Result duplicate() {
            return new Result(false, null, "nick.duplicate");
        }

        private static Result blacklisted() {
            return new Result(false, null, "nick.blacklisted");
        }
    }
}
