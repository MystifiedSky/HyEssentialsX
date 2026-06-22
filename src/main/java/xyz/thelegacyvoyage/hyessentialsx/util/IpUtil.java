package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.netty.NettyUtil;
import io.netty.channel.Channel;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.regex.Pattern;

public final class IpUtil {

    private static final Pattern IPV4_PATTERN = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");
    private static final Pattern IPV6_CHARS_PATTERN = Pattern.compile("^[0-9a-fA-F:]+$");

    private IpUtil() {}

    @Nullable
    public static String extractIp(@Nullable PacketHandler handler) {
        if (handler == null) return null;
        try {
            Channel channel = handler.getChannel();
            if (channel == null) return null;
            try {
                SocketAddress socketAddress = NettyUtil.getRemoteSocketAddress(channel);
                String resolved = extractFromSocketAddress(socketAddress);
                if (resolved != null) return resolved;
            } catch (NoClassDefFoundError | Exception ignored) {
            }

            SocketAddress direct = channel.remoteAddress();
            SocketAddress parent = channel.parent() != null ? channel.parent().remoteAddress() : null;
            SocketAddress address = findRemoteAddress(direct, 4);
            if (address == null) {
                address = findRemoteAddress(parent, 4);
            }
            String resolved = extractFromSocketAddress(address);
            if (resolved != null) return resolved;
            SocketAddress rawAddress = direct;
            if (rawAddress == null || isQuicStreamAddress(rawAddress)) {
                rawAddress = parent;
            }
            String raw = rawAddress != null ? rawAddress.toString() : null;
            String normalized = normalizeIp(raw);
            if (normalized != null) return normalized;
            return extractFromString(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static String extractFromSocketAddress(@Nullable SocketAddress address) {
        if (!(address instanceof InetSocketAddress inet)) return null;
        InetAddress inetAddress = inet.getAddress();
        if (inetAddress != null) {
            return normalizeIp(inetAddress.getHostAddress());
        }
        return normalizeIp(inet.getHostString());
    }

    @Nullable
    public static String normalizeIp(@Nullable String raw) {
        if (raw == null) return null;
        String ip = raw.trim();
        if (ip.isEmpty()) return null;
        if (ip.startsWith("/")) {
            ip = ip.substring(1);
        }
        if (ip.startsWith("[")) {
            int end = ip.indexOf(']');
            if (end > 0) {
                ip = ip.substring(1, end);
            }
        }
        int colon = ip.lastIndexOf(':');
        int dot = ip.lastIndexOf('.');
        if (colon > -1 && dot > -1 && colon > dot) {
            String port = ip.substring(colon + 1);
            if (port.matches("\\d+")) {
                ip = ip.substring(0, colon);
            }
        }
        if (ip.isEmpty()) return null;
        return isValidIp(ip) ? ip : null;
    }

    @Nullable
    private static String extractFromString(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return null;
        String text = raw;
        int slash = text.indexOf('/');
        if (slash >= 0) {
            text = text.substring(slash + 1);
        }
        int space = text.indexOf(' ');
        if (space > 0) {
            text = text.substring(0, space);
        }
        String normalized = normalizeIp(text);
        if (normalized != null) return normalized;
        java.util.regex.Matcher ipv4 = java.util.regex.Pattern.compile("(\\d{1,3}\\.){3}\\d{1,3}").matcher(raw);
        if (ipv4.find()) {
            return ipv4.group();
        }
        java.util.regex.Matcher ipv6 = java.util.regex.Pattern.compile("([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}").matcher(raw);
        if (ipv6.find()) {
            return ipv6.group();
        }
        return null;
    }

    @Nullable
    private static SocketAddress findRemoteAddress(@Nullable Object value, int depth) {
        if (value == null || depth <= 0) return null;
        if (value instanceof SocketAddress address) {
            if (address instanceof InetSocketAddress) return address;
        }
        if (value instanceof Channel channel) {
            SocketAddress direct = findRemoteAddress(channel.remoteAddress(), depth - 1);
            if (direct != null) return direct;
            Channel parent = channel.parent();
            if (parent != null) {
                SocketAddress parentAddr = findRemoteAddress(parent, depth - 1);
                if (parentAddr != null) return parentAddr;
            }
        }
        String[] methods = {"remoteAddress", "getRemoteAddress", "parent", "getParent"};
        for (String method : methods) {
            try {
                java.lang.reflect.Method m = value.getClass().getMethod(method);
                Object result = m.invoke(value);
                SocketAddress found = findRemoteAddress(result, depth - 1);
                if (found != null) return found;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static boolean isQuicStreamAddress(@Nullable Object value) {
        if (value == null) return false;
        return value.getClass().getName().contains("QuicStreamAddress");
    }

    private static boolean isValidIp(@Nullable String ip) {
        if (ip == null || ip.isBlank()) return false;
        if (IPV4_PATTERN.matcher(ip).matches()) {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) return false;
            for (String part : parts) {
                int value;
                try {
                    value = Integer.parseInt(part);
                } catch (NumberFormatException ex) {
                    return false;
                }
                if (value < 0 || value > 255) return false;
            }
            return true;
        }
        if (!ip.contains(":")) return false;
        return IPV6_CHARS_PATTERN.matcher(ip).matches();
    }
}

