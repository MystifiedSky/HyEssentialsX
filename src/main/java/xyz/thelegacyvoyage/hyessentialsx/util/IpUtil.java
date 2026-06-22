package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.io.PacketHandler;
import io.netty.channel.Channel;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public final class IpUtil {

    private IpUtil() {}

    @Nullable
    public static String extractIp(@Nullable PacketHandler handler) {
        if (handler == null) return null;
        try {
            Channel channel = handler.getChannel();
            if (channel == null) return null;
            SocketAddress address = channel.remoteAddress();
            if (address == null && channel.parent() != null) {
                address = channel.parent().remoteAddress();
            }
            if (address instanceof InetSocketAddress inet) {
                InetAddress inetAddress = inet.getAddress();
                if (inetAddress != null) {
                    return normalizeIp(inetAddress.getHostAddress());
                }
                return normalizeIp(inet.getHostString());
            }
            String raw = address != null ? address.toString() : null;
            if ((raw == null || raw.isBlank()) && channel.parent() != null) {
                SocketAddress parentAddr = channel.parent().remoteAddress();
                if (parentAddr != null) {
                    raw = parentAddr.toString();
                }
            }
            String normalized = normalizeIp(raw);
            if (normalized != null) return normalized;
            return extractFromString(raw);
        } catch (Exception ignored) {
            return null;
        }
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
        return ip.isEmpty() ? null : ip;
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
}
