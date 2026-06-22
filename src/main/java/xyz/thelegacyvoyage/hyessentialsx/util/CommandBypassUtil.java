package xyz.thelegacyvoyage.hyessentialsx.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

public final class CommandBypassUtil {

    private static final String BYPASS_SUFFIX = ".bypass";

    private CommandBypassUtil() {}

    public static boolean hasCooldownBypass(@Nullable Object principal,
                                            @Nonnull String key,
                                            @Nonnull String commandBypassPermission) {
        // Legacy compatibility: hyessentialsx.<command>.bypass
        if (CommandPermissionUtil.hasPermission(principal, commandBypassPermission)) {
            return true;
        }
        String commandRoot = resolveCommandRoot(commandBypassPermission);
        if (commandRoot != null
                && CommandPermissionUtil.hasPermission(principal, commandRoot + ".cooldown.bypass")) {
            return true;
        }
        if (CommandPermissionUtil.hasPermission(principal, "hyessentialsx.cooldown.bypass")) {
            return true;
        }
        String normalizedKey = normalizeKey(key);
        if (normalizedKey.isEmpty()) {
            return false;
        }
        return CommandPermissionUtil.hasPermission(principal, "hyessentialsx.cooldown.bypass." + normalizedKey);
    }

    public static boolean hasWarmupBypass(@Nullable Object principal,
                                          @Nonnull String key,
                                          @Nonnull String commandBypassPermission) {
        // Legacy compatibility: hyessentialsx.<command>.bypass
        if (CommandPermissionUtil.hasPermission(principal, commandBypassPermission)) {
            return true;
        }
        String commandRoot = resolveCommandRoot(commandBypassPermission);
        if (commandRoot != null
                && CommandPermissionUtil.hasPermission(principal, commandRoot + ".warmup.bypass")) {
            return true;
        }
        if (CommandPermissionUtil.hasPermission(principal, "hyessentialsx.warmup.bypass")) {
            return true;
        }
        String normalizedKey = normalizeKey(key);
        if (normalizedKey.isEmpty()) {
            return false;
        }
        return CommandPermissionUtil.hasPermission(principal, "hyessentialsx.warmup.bypass." + normalizedKey);
    }

    @Nullable
    private static String resolveCommandRoot(@Nonnull String commandBypassPermission) {
        String normalized = commandBypassPermission.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        if (!normalized.endsWith(BYPASS_SUFFIX)) {
            return normalized;
        }
        String root = normalized.substring(0, normalized.length() - BYPASS_SUFFIX.length());
        return root.isBlank() ? null : root;
    }

    @Nonnull
    private static String normalizeKey(@Nonnull String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }
}
