package xyz.thelegacyvoyage.hyessentialsx.util;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;

public final class CommandPermissionUtil {

    private CommandPermissionUtil() {}

    public static void apply(@Nonnull Object command, @Nonnull String permission) {
        if (permission.isBlank()) return;
        String[] methods = {"requirePermission", "setPermission", "setRequiredPermission", "setPermissionNode"};
        Class<?> type = command.getClass();
        while (type != null) {
            for (String name : methods) {
                try {
                    Method method = type.getDeclaredMethod(name, String.class);
                    method.setAccessible(true);
                    method.invoke(command, permission);
                    return;
                } catch (Exception ignored) {
                }
            }
            type = type.getSuperclass();
        }
    }
}

