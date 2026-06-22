package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.permissions.provider.PermissionProvider;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class ExplicitPermissionUtil {

    private ExplicitPermissionUtil() {
    }

    public static boolean hasExplicitPermission(@Nonnull UUID uuid, @Nonnull String permission) {
        String normalized = normalize(permission);
        if (normalized.isBlank()) {
            return false;
        }
        try {
            PermissionProvider provider = PermissionsModule.get().getFirstPermissionProvider();
            if (provider == null) {
                return false;
            }
            if (containsExact(provider.getUserPermissions(uuid), normalized)) {
                return true;
            }
            Set<String> groups = provider.getGroupsForUser(uuid);
            if (groups == null || groups.isEmpty()) {
                return false;
            }
            for (String group : groups) {
                if (group == null || group.isBlank()) {
                    continue;
                }
                if (containsExact(provider.getGroupPermissions(group), normalized)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            return false;
        }
        return false;
    }

    private static boolean containsExact(Set<String> permissions, @Nonnull String normalized) {
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        for (String permission : permissions) {
            if (permission != null && normalize(permission).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private static String normalize(@Nonnull String permission) {
        return permission.trim().toLowerCase(Locale.ROOT);
    }
}
