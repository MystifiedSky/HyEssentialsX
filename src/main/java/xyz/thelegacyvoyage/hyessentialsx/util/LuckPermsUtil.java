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
        ensureInitialized();
        if (luckPerms == null) return null;
        try {
            Method getUserManager = luckPerms.getClass().getMethod("getUserManager");
            Object userManager = getUserManager.invoke(luckPerms);
            if (userManager == null) return null;
            Method getUser = userManager.getClass().getMethod("getUser", UUID.class);
            Object user = getUser.invoke(userManager, uuid);
            if (user == null) return null;
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

