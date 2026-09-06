package xyz.thelegacyvoyage.hyessentialsx.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class PermissionProviderUtil {

    private PermissionProviderUtil() {}

    @Nullable
    public static String getPrimaryGroup(@Nonnull UUID uuid) {
        String primary = LuckPermsUtil.getPrimaryGroup(uuid);
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        primary = HyperPermsUtil.getPrimaryGroup(uuid);
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return null;
    }

    @Nonnull
    public static String getPrefix(@Nonnull UUID uuid) {
        String prefix = LuckPermsUtil.getPrefix(uuid);
        if (!prefix.isBlank()) {
            return prefix;
        }
        return HyperPermsUtil.getPrefix(uuid);
    }

    @Nonnull
    public static String getSuffix(@Nonnull UUID uuid) {
        String suffix = LuckPermsUtil.getSuffix(uuid);
        if (!suffix.isBlank()) {
            return suffix;
        }
        return HyperPermsUtil.getSuffix(uuid);
    }

    @Nullable
    public static String getMetaValue(@Nonnull UUID uuid, @Nonnull String key) {
        return LuckPermsUtil.getMetaValue(uuid, key);
    }

    @Nonnull
    public static Set<String> getGroupsFallback(@Nonnull UUID uuid) {
        LinkedHashSet<String> groups = new LinkedHashSet<>();
        String primary = getPrimaryGroup(uuid);
        if (primary != null && !primary.isBlank()) {
            groups.add(primary);
        }
        groups.addAll(HyperPermsUtil.getGroupsFallback(uuid));
        groups.addAll(LuckPermsUtil.getGroupsFallback(uuid));
        return groups;
    }
}
