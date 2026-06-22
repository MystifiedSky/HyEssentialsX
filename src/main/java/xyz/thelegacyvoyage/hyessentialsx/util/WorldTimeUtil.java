package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;

public final class WorldTimeUtil {

    private WorldTimeUtil() {}

    public static boolean setDayTimeFraction(@Nonnull World world, double dayTime) {
        if (trySetWorldTimeResource(world, dayTime)) return true;
        if (trySetWorldTimeResourceGameTime(world, dayTime)) return true;
        long ticks = Math.round(dayTime * 24000.0);
        return setTimeTicks(world, ticks);
    }

    public static boolean isNight(@Nonnull World world) {
        Double dayProgress = getDayProgress(world);
        if (dayProgress != null) {
            return dayProgress < 0.25d || dayProgress > 0.75d;
        }
        Integer hour = getCurrentHour(world);
        if (hour != null) {
            return hour >= 18 || hour < 6;
        }
        return false;
    }

    public static boolean isDay(@Nonnull World world) {
        Double dayProgress = getDayProgress(world);
        if (dayProgress != null) {
            return dayProgress >= 0.25d && dayProgress <= 0.75d;
        }
        Integer hour = getCurrentHour(world);
        if (hour != null) {
            return hour >= 6 && hour < 18;
        }
        return false;
    }

    public static boolean setTimeTicks(@Nonnull World world, long ticks) {
        if (tryInvoke(world, "setTimeOfDay", ticks)) return true;
        if (tryInvoke(world, "setTime", ticks)) return true;
        if (tryInvoke(world, "setWorldTime", ticks)) return true;
        if (tryInvoke(world, "setDayTime", ticks)) return true;
        if (tryInvoke(world, "setTimeOfDayTicks", ticks)) return true;
        if (tryInvoke(world, "setTimeTicks", ticks)) return true;

        float f = (float) ticks;
        double d = (double) ticks;
        if (tryInvoke(world, "setTimeOfDay", f)) return true;
        if (tryInvoke(world, "setTime", f)) return true;
        if (tryInvoke(world, "setWorldTime", f)) return true;
        if (tryInvoke(world, "setDayTime", f)) return true;
        if (tryInvoke(world, "setTimeOfDayTicks", f)) return true;
        if (tryInvoke(world, "setTimeTicks", f)) return true;

        if (tryInvoke(world, "setTimeOfDay", d)) return true;
        if (tryInvoke(world, "setTime", d)) return true;
        if (tryInvoke(world, "setWorldTime", d)) return true;
        if (tryInvoke(world, "setDayTime", d)) return true;
        if (tryInvoke(world, "setTimeOfDayTicks", d)) return true;
        if (tryInvoke(world, "setTimeTicks", d)) return true;

        return false;
    }

    private static boolean trySetWorldTimeResource(@Nonnull World world, double dayTime) {
        try {
            Object store = resolveStore(world);
            if (store == null) return false;
            Object resource = resolveWorldTimeResource(store);
            if (resource == null) return false;

            Method setDayTime = null;
            for (Method m : resource.getClass().getMethods()) {
                if (!m.getName().equals("setDayTime")) continue;
                if (m.getParameterCount() != 3) continue;
                Class<?>[] params = m.getParameterTypes();
                if (params[0] != double.class && params[0] != Double.class) continue;
                if (!World.class.isAssignableFrom(params[1])) continue;
                setDayTime = m;
                break;
            }
            if (setDayTime == null) return false;
            setDayTime.invoke(resource, dayTime, world, store);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean trySetWorldTimeResourceGameTime(@Nonnull World world, double dayTime) {
        try {
            Object store = resolveStore(world);
            if (store == null) return false;
            Object resource = resolveWorldTimeResource(store);
            if (resource == null) return false;

            Method setGameTime = null;
            for (Method m : resource.getClass().getMethods()) {
                if (!m.getName().equals("setGameTime")) continue;
                if (m.getParameterCount() != 3) continue;
                Class<?>[] params = m.getParameterTypes();
                if (!java.time.Instant.class.isAssignableFrom(params[0])) continue;
                if (!World.class.isAssignableFrom(params[1])) continue;
                setGameTime = m;
                break;
            }
            if (setGameTime == null) return false;

            java.time.LocalDateTime dateTime = null;
            try {
                Method getGameDateTime = resource.getClass().getMethod("getGameDateTime");
                Object dt = getGameDateTime.invoke(resource);
                if (dt instanceof java.time.LocalDateTime) {
                    dateTime = (java.time.LocalDateTime) dt;
                }
            } catch (Throwable ignored) {
            }
            if (dateTime == null) {
                try {
                    Method getGameTime = resource.getClass().getMethod("getGameTime");
                    Object instantObj = getGameTime.invoke(resource);
                    if (instantObj instanceof java.time.Instant) {
                        java.time.Instant instant = (java.time.Instant) instantObj;
                        dateTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneOffset.UTC);
                    }
                } catch (Throwable ignored) {
                }
            }
            if (dateTime == null) return false;

            int hour = (int) Math.floor((dayTime % 1.0) * 24.0);
            if (hour < 0) hour = 0;
            if (hour > 23) hour = 23;
            java.time.LocalDateTime updated = dateTime.withHour(hour).withMinute(0).withSecond(0).withNano(0);
            java.time.Instant updatedInstant = updated.toInstant(java.time.ZoneOffset.UTC);
            setGameTime.invoke(resource, updatedInstant, world, store);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object resolveStore(@Nonnull World world) throws Exception {
        Method getEntityStore = world.getClass().getMethod("getEntityStore");
        Object entityStore = getEntityStore.invoke(world);
        if (entityStore == null) return null;
        Method getStore = entityStore.getClass().getMethod("getStore");
        return getStore.invoke(entityStore);
    }

    private static Object resolveWorldTimeResource(@Nonnull Object store) throws Exception {
        Class<?> resourceClass = Class.forName("com.hypixel.hytale.server.core.modules.time.WorldTimeResource");
        Method getResourceType = resourceClass.getMethod("getResourceType");
        Object resourceType = getResourceType.invoke(null);
        if (resourceType == null) return null;
        Method getResource = null;
        for (Method m : store.getClass().getMethods()) {
            if (!m.getName().equals("getResource")) continue;
            if (m.getParameterCount() != 1) continue;
            getResource = m;
            break;
        }
        if (getResource == null) return null;
        return getResource.invoke(store, resourceType);
    }

    private static boolean tryInvoke(@Nonnull Object target, @Nonnull String method, long value) {
        try {
            Method m = target.getClass().getMethod(method, long.class);
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

    private static boolean tryInvoke(@Nonnull Object target, @Nonnull String method, double value) {
        try {
            Method m = target.getClass().getMethod(method, double.class);
            m.invoke(target, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Double getDayProgress(@Nonnull World world) {
        try {
            Object store = resolveStore(world);
            if (store == null) return null;
            Object resource = resolveWorldTimeResource(store);
            if (resource == null) return null;
            Method getDayProgress = resource.getClass().getMethod("getDayProgress");
            Object value = getDayProgress.invoke(resource);
            if (value instanceof Float f) return (double) f;
            if (value instanceof Double d) return d;
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Integer getCurrentHour(@Nonnull World world) {
        try {
            Object store = resolveStore(world);
            if (store == null) return null;
            Object resource = resolveWorldTimeResource(store);
            if (resource == null) return null;
            Method getCurrentHour = resource.getClass().getMethod("getCurrentHour");
            Object value = getCurrentHour.invoke(resource);
            if (value instanceof Integer i) return i;
            if (value instanceof Number n) return n.intValue();
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
