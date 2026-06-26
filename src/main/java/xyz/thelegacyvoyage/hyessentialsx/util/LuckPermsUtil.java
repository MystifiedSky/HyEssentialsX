package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.permissions.PermissionsModule;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

public final class LuckPermsUtil {

    private static volatile boolean checked;
    @Nullable
    private static Object luckPerms;

    private LuckPermsUtil() {}

    public static boolean isLuckPermsAvailable() {
        ensureInitialized();
        return luckPerms != null;
    }

    @Nullable
    public static String getPrimaryGroup(@Nonnull UUID uuid) {
        Object user = getUser(uuid);
        if (user == null) return null;
        try {
            Method getPrimaryGroup = user.getClass().getMethod("getPrimaryGroup");
            Object group = getPrimaryGroup.invoke(user);
            if (group == null) return null;
            String name = group.toString();
            return name.isBlank() ? null : name;
        } catch (Exception e) {
            return null;
        }
    }

    @Nonnull
    public static String getPrefix(@Nonnull UUID uuid) {
        return getMetaString(uuid, "getPrefix");
    }

    @Nonnull
    public static String getSuffix(@Nonnull UUID uuid) {
        return getMetaString(uuid, "getSuffix");
    }

    @Nullable
    public static String getMetaValue(@Nonnull UUID uuid, @Nonnull String key) {
        Object meta = getMetaData(uuid);
        if (meta == null) return null;
        try {
            Method getMetaValue = meta.getClass().getMethod("getMetaValue", String.class);
            Object value = getMetaValue.invoke(meta, key);
            if (value == null) return null;
            String out = value.toString();
            return out.isBlank() ? null : out;
        } catch (Exception e) {
            return null;
        }
    }

    @Nonnull
    private static String getMetaString(@Nonnull UUID uuid, @Nonnull String methodName) {
        Object meta = getMetaData(uuid);
        if (meta == null) return "";
        try {
            Method method = meta.getClass().getMethod(methodName);
            Object value = method.invoke(meta);
            return value == null ? "" : value.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Nullable
    private static Object getMetaData(@Nonnull UUID uuid) {
        Object user = getUser(uuid);
        if (user == null) return null;
        try {
            Method getCachedData = user.getClass().getMethod("getCachedData");
            Object cachedData = getCachedData.invoke(user);
            if (cachedData == null) return null;
            Method getMetaData = cachedData.getClass().getMethod("getMetaData");
            return getMetaData.invoke(cachedData);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static Object getUser(@Nonnull UUID uuid) {
        ensureInitialized();
        if (luckPerms == null) return null;
        try {
            Method getUserManager = luckPerms.getClass().getMethod("getUserManager");
            Object userManager = getUserManager.invoke(luckPerms);
            if (userManager == null) return null;
            Method getUser = userManager.getClass().getMethod("getUser", UUID.class);
            Object user = getUser.invoke(userManager, uuid);
            if (user != null) {
                return user;
            }
            try {
                Method loadUser = userManager.getClass().getMethod("loadUser", UUID.class);
                Object future = loadUser.invoke(userManager, uuid);
                if (future != null) {
                    Method join = future.getClass().getMethod("join");
                    return join.invoke(future);
                }
            } catch (Exception ignored) {
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Nonnull
    public static Set<String> getGroupsFallback(@Nonnull UUID uuid) {
        return PermissionsModule.get().getGroupsForUser(uuid);
    }

    private static void ensureInitialized() {
        if (checked) return;
        synchronized (LuckPermsUtil.class) {
            if (checked) return;
            checked = true;
            try {
                Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
                Method getMethod = providerClass.getMethod("get");
                luckPerms = getMethod.invoke(null);
            } catch (Exception ignored) {
                luckPerms = null;
            }
        }
    }
}

