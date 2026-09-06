package xyz.thelegacyvoyage.hyessentialsx.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

public final class HyperPermsUtil {

    private static volatile boolean checked;
    @Nullable
    private static Class<?> hyperPermsClass;
    @Nullable
    private static Class<?> chatApiClass;

    private HyperPermsUtil() {}

    public static boolean isHyperPermsAvailable() {
        return getApi() != null;
    }

    @Nullable
    public static String getPrimaryGroup(@Nonnull UUID uuid) {
        String fromChatApi = invokeChatApiString("getPrimaryGroup", uuid);
        if (fromChatApi != null && !fromChatApi.isBlank() && !"default".equalsIgnoreCase(fromChatApi)) {
            return fromChatApi;
        }

        Object user = getUser(uuid);
        if (user == null) {
            return fromChatApi != null && !fromChatApi.isBlank() ? fromChatApi : null;
        }
        try {
            Object value = user.getClass().getMethod("getPrimaryGroup").invoke(user);
            if (value == null) return null;
            String out = value.toString();
            return out.isBlank() ? null : out;
        } catch (Exception e) {
            return fromChatApi != null && !fromChatApi.isBlank() ? fromChatApi : null;
        }
    }

    @Nonnull
    public static String getPrefix(@Nonnull UUID uuid) {
        return valueOrEmpty(invokeChatApiString("getPrefix", uuid));
    }

    @Nonnull
    public static String getSuffix(@Nonnull UUID uuid) {
        return valueOrEmpty(invokeChatApiString("getSuffix", uuid));
    }

    @Nonnull
    public static Set<String> getGroupsFallback(@Nonnull UUID uuid) {
        Object user = getUser(uuid);
        if (user == null) return Set.of();
        try {
            Object value = user.getClass().getMethod("getInheritedGroups").invoke(user);
            if (value instanceof Set<?> set) {
                java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
                for (Object group : set) {
                    if (group != null && !group.toString().isBlank()) {
                        out.add(group.toString());
                    }
                }
                return out;
            }
        } catch (Exception ignored) {
        }
        return Set.of();
    }

    @Nullable
    private static String invokeChatApiString(@Nonnull String methodName, @Nonnull UUID uuid) {
        ensureInitialized();
        if (chatApiClass == null) return null;
        try {
            Method method = chatApiClass.getMethod(methodName, UUID.class);
            Object value = method.invoke(null, uuid);
            return value == null ? null : value.toString();
        } catch (InvocationTargetException ignored) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static Object getUser(@Nonnull UUID uuid) {
        Object api = getApi();
        if (api == null) return null;
        try {
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            if (userManager == null) return null;
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, uuid);
            if (user != null) {
                return user;
            }
            Object future = userManager.getClass().getMethod("loadUser", UUID.class).invoke(userManager, uuid);
            if (future == null) return null;
            Object optional = future.getClass().getMethod("join").invoke(future);
            if (optional == null) return null;
            Method isPresent = optional.getClass().getMethod("isPresent");
            if (!Boolean.TRUE.equals(isPresent.invoke(optional))) return null;
            return optional.getClass().getMethod("get").invoke(optional);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static Object getApi() {
        ensureInitialized();
        if (hyperPermsClass == null) return null;
        try {
            return hyperPermsClass.getMethod("getApi").invoke(null);
        } catch (InvocationTargetException ignored) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void ensureInitialized() {
        if (checked) return;
        synchronized (HyperPermsUtil.class) {
            if (checked) return;
            checked = true;
            try {
                hyperPermsClass = Class.forName("com.hyperperms.HyperPerms");
            } catch (Exception ignored) {
                hyperPermsClass = null;
            }
            try {
                chatApiClass = Class.forName("com.hyperperms.api.ChatAPI");
            } catch (Exception ignored) {
                chatApiClass = null;
            }
        }
    }

    @Nonnull
    private static String valueOrEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }
}
