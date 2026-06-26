package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class CommandWrapperUtil {

    private CommandWrapperUtil() {}

    public static void mirrorCommandShape(@Nonnull AbstractCommand wrapper, @Nonnull AbstractCommand delegate) {
        ensureRegistration(delegate);

        for (String alias : delegate.getAliases()) {
            wrapper.addAliases(alias);
        }

        String permission = delegate.getPermission();
        if (permission != null && !permission.isBlank()) {
            wrapper.requirePermission(permission);
        }

        copyListField(wrapper, delegate, "requiredArguments");
        copyMapField(wrapper, delegate, "optionalArguments");
        copyMapField(wrapper, delegate, "subCommands");
        copyMapField(wrapper, delegate, "subCommandsAliases");
        copyMapField(wrapper, delegate, "variantCommands");

        copyField(wrapper, delegate, "permissionGroups");
        copyField(wrapper, delegate, "argumentAbbreviationMap");
        copyField(wrapper, delegate, "totalNumRequiredParameters");
        copyField(wrapper, delegate, "unavailableInSingleplayer");
        copyField(wrapper, delegate, "allowsExtraArguments");
        copyField(wrapper, delegate, "hasGreedyStringArg");
    }

    private static void ensureRegistration(@Nonnull AbstractCommand command) {
        if (command.hasBeenRegistered()) {
            return;
        }
        try {
            command.completeRegistration();
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void copyListField(@Nonnull AbstractCommand target,
                                      @Nonnull AbstractCommand source,
                                      @Nonnull String fieldName) {
        Object targetValue = readField(target, fieldName);
        Object sourceValue = readField(source, fieldName);
        if (targetValue instanceof List targetList && sourceValue instanceof Collection sourceCollection) {
            targetList.clear();
            targetList.addAll(sourceCollection);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void copyMapField(@Nonnull AbstractCommand target,
                                     @Nonnull AbstractCommand source,
                                     @Nonnull String fieldName) {
        Object targetValue = readField(target, fieldName);
        Object sourceValue = readField(source, fieldName);
        if (targetValue instanceof Map targetMap && sourceValue instanceof Map sourceMap) {
            targetMap.clear();
            targetMap.putAll(sourceMap);
            return;
        }
        if (isFastutilIntMap(targetValue) && isFastutilIntMap(sourceValue)) {
            invokeClear(targetValue);
            invokePutAll(targetValue, sourceValue);
        }
    }

    private static void copyField(@Nonnull AbstractCommand target,
                                  @Nonnull AbstractCommand source,
                                  @Nonnull String fieldName) {
        Field field = findField(AbstractCommand.class, fieldName);
        if (field == null) {
            return;
        }
        try {
            field.setAccessible(true);
            field.set(target, field.get(source));
        } catch (Exception ignored) {
        }
    }

    @Nullable
    private static Object readField(@Nonnull AbstractCommand command, @Nonnull String fieldName) {
        Field field = findField(AbstractCommand.class, fieldName);
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(command);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static Field findField(@Nullable Class<?> type, @Nonnull String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static boolean isFastutilIntMap(@Nullable Object value) {
        return value != null && value.getClass().getName().contains("fastutil")
                && value.getClass().getName().contains("Int2Object");
    }

    private static void invokeClear(@Nonnull Object target) {
        try {
            target.getClass().getMethod("clear").invoke(target);
        } catch (Exception ignored) {
        }
    }

    private static void invokePutAll(@Nonnull Object target, @Nullable Object source) {
        if (source == null) {
            return;
        }
        for (Method method : target.getClass().getMethods()) {
            if (!"putAll".equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> parameterType = method.getParameterTypes()[0];
            if (!parameterType.isAssignableFrom(source.getClass())) {
                continue;
            }
            try {
                method.invoke(target, source);
                return;
            } catch (Exception ignored) {
            }
        }
    }
}
