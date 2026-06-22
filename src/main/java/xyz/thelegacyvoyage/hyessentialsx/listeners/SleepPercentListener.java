package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.event.EventRegistry;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.function.Consumer;

public final class SleepPercentListener {

    private final ConfigManager config;

    public SleepPercentListener(@Nonnull ConfigManager config) {
        this.config = config;
    }

    public void register(@Nonnull EventRegistry events) {
        try {
            Class<?> eventClass = Class.forName("com.hypixel.hytale.server.core.event.events.player.SleepPercentageEvent");
            Method register = events.getClass().getMethod("registerGlobal", Class.class, Consumer.class);
            register.invoke(events, eventClass, (Consumer<Object>) this::onSleepEvent);
            Log.info("[HyEssentialsX] SleepPercentageEvent listener registered.");
        } catch (Throwable t) {
            Log.warn("[HyEssentialsX] SleepPercentageEvent not available: " + t.getMessage());
        }
    }

    private void onSleepEvent(@Nonnull Object event) {
        int percent = config.getSleepPercentage();
        if (percent < 0) percent = 0;
        if (percent > 100) percent = 100;

        boolean applied = tryInvoke(event, "setRequiredPercentage", percent)
                || tryInvoke(event, "setPercentage", percent)
                || tryInvoke(event, "setRequiredPercent", percent)
                || tryInvoke(event, "setPercent", percent)
                || tryInvoke(event, "setRequired", percent);

        if (!applied) {
            tryInvoke(event, "setRequiredPercentage", (float) percent);
            tryInvoke(event, "setPercentage", (float) percent);
            tryInvoke(event, "setRequiredPercent", (float) percent);
            tryInvoke(event, "setPercent", (float) percent);
            tryInvoke(event, "setRequired", (float) percent);
        }
    }

    private static boolean tryInvoke(@Nonnull Object target, @Nonnull String method, int value) {
        try {
            Method m = target.getClass().getMethod(method, int.class);
            m.invoke(target, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean tryInvoke(@Nonnull Object target, @Nonnull String method, float value) {
        try {
            Method m = target.getClass().getMethod(method, float.class);
            m.invoke(target, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}

