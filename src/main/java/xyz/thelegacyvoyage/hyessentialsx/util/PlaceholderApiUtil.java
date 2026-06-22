package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;

public final class PlaceholderApiUtil {

    private static final String PLACEHOLDER_API_CLASS = "at.helpch.placeholderapi.PlaceholderAPI";
    private static final Object INIT_LOCK = new Object();
    private static volatile boolean initialized = false;
    private static volatile boolean available = false;
    private static volatile Method setPlaceholdersMethod;

    private PlaceholderApiUtil() {
    }

    @Nonnull
    public static Message apply(@Nullable PlayerRef player,
                                @Nonnull String text,
                                @Nonnull ConfigManager config) {
        Message base = Messages.m(text);
        if (player == null) {
            return base;
        }
        if (!config.isPlaceholderApiEnabled()) {
            return base;
        }
        if (!isAvailable()) {
            return base;
        }
        try {
            Object result = setPlaceholdersMethod.invoke(null, player, base);
            if (result instanceof Message msg) {
                return msg;
            }
        } catch (Throwable ignored) {
        }
        return base;
    }

    private static boolean isAvailable() {
        if (!initialized) {
            synchronized (INIT_LOCK) {
                if (!initialized) {
                    try {
                        Class<?> apiClass = Class.forName(PLACEHOLDER_API_CLASS);
                        setPlaceholdersMethod = apiClass.getMethod("setPlaceholders", PlayerRef.class, Message.class);
                        available = true;
                    } catch (Throwable ignored) {
                        available = false;
                        setPlaceholdersMethod = null;
                    }
                    initialized = true;
                }
            }
        }
        return available && setPlaceholdersMethod != null;
    }
}
