package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.io.PacketHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;

public final class ServerVersion {

    private static final boolean HAS_TO_CLIENT_PACKET = classExists("com.hypixel.hytale.protocol.ToClientPacket");
    private static final boolean HAS_PACKET = classExists("com.hypixel.hytale.protocol.Packet");
    private static final String PACKET_WRITE_SIGNATURE = detectPacketWriteSignature();

    private ServerVersion() {
    }

    public static boolean hasToClientPacket() {
        return HAS_TO_CLIENT_PACKET;
    }

    public static boolean hasPacketInterface() {
        return HAS_PACKET;
    }

    @Nonnull
    public static String packetWriteSignature() {
        return PACKET_WRITE_SIGNATURE;
    }

    @Nonnull
    public static String runtimeVersionHint() {
        Package pkg = PacketHandler.class.getPackage();
        if (pkg == null) return "unknown";
        String impl = pkg.getImplementationVersion();
        if (impl != null && !impl.isBlank()) return impl;
        String spec = pkg.getSpecificationVersion();
        if (spec != null && !spec.isBlank()) return spec;
        return "unknown";
    }

    public static boolean sendPacket(@Nonnull PacketHandler handler, @Nullable Object packet) {
        if (packet == null) return false;
        try {
            Method[] methods = handler.getClass().getMethods();
            Method best = null;
            int bestDistance = Integer.MAX_VALUE;
            for (Method method : methods) {
                if (!"write".equals(method.getName())) continue;
                if (method.getParameterCount() != 1) continue;
                Class<?> param = method.getParameterTypes()[0];
                if (!param.isAssignableFrom(packet.getClass())) continue;
                int distance = typeDistance(packet.getClass(), param);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = method;
                }
            }
            if (best == null) {
                return false;
            }
            best.invoke(handler, packet);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int typeDistance(@Nonnull Class<?> from, @Nonnull Class<?> to) {
        if (from.equals(to)) return 0;
        int distance = 1;
        Class<?> cursor = from.getSuperclass();
        while (cursor != null) {
            if (cursor.equals(to)) return distance;
            distance++;
            cursor = cursor.getSuperclass();
        }
        for (Class<?> iface : from.getInterfaces()) {
            if (iface.equals(to)) return 1;
        }
        return 100;
    }

    private static boolean classExists(@Nonnull String name) {
        try {
            Class.forName(name, false, ServerVersion.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Nonnull
    private static String detectPacketWriteSignature() {
        try {
            Method[] methods = PacketHandler.class.getMethods();
            String best = null;
            for (Method method : methods) {
                if (!"write".equals(method.getName())) continue;
                if (method.getParameterCount() != 1) continue;
                String name = method.getParameterTypes()[0].getName();
                if (best == null || name.compareTo(best) < 0) {
                    best = name;
                }
            }
            return best != null ? best : "unknown";
        } catch (Throwable ignored) {
            return "unknown";
        }
    }
}
