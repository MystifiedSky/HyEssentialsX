package xyz.thelegacyvoyage.hyessentialsx.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

public final class TimeUtil {

    private TimeUtil() {}

    /**
     * Parses a single time token like 10m, 2h, 3d, 1y. Returns seconds or -1 if invalid.
     */
    public static long parseDurationSeconds(@Nonnull String input) {
        String s = input.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return -1;

        long multiplier;
        char suffix = s.charAt(s.length() - 1);
        String numberPart;

        if (Character.isDigit(suffix)) {
            numberPart = s;
            multiplier = 1L;
        } else {
            numberPart = s.substring(0, s.length() - 1);
            switch (suffix) {
                case 's' -> multiplier = 1L;
                case 'm' -> multiplier = 60L;
                case 'h' -> multiplier = 60L * 60L;
                case 'd' -> multiplier = 60L * 60L * 24L;
                case 'w' -> multiplier = 60L * 60L * 24L * 7L;
                case 'y' -> multiplier = 60L * 60L * 24L * 365L;
                default -> {
                    return -1;
                }
            }
        }

        try {
            long value = Long.parseLong(numberPart);
            if (value < 0) return -1;
            return value * multiplier;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Nonnull
    public static String formatDurationSeconds(long seconds) {
        if (seconds <= 0) return "0s";
        long remaining = seconds;
        StringBuilder out = new StringBuilder();
        boolean hasParts = false;

        long years = remaining / (60L * 60L * 24L * 365L);
        remaining %= (60L * 60L * 24L * 365L);
        if (years > 0) {
            out.append(years).append("y");
            hasParts = true;
        }

        long months = remaining / (60L * 60L * 24L * 30L);
        remaining %= (60L * 60L * 24L * 30L);
        if (months > 0) {
            if (hasParts) out.append(" ");
            out.append(months).append("mo");
            hasParts = true;
        }

        long days = remaining / (60L * 60L * 24L);
        remaining %= (60L * 60L * 24L);
        if (days > 0) {
            if (hasParts) out.append(" ");
            out.append(days).append("d");
            hasParts = true;
        }

        long hours = remaining / (60L * 60L);
        remaining %= (60L * 60L);
        if (hours > 0) {
            if (hasParts) out.append(" ");
            out.append(hours).append("h");
            hasParts = true;
        }

        long mins = remaining / 60L;
        remaining %= 60L;
        if (mins > 0 || hasParts) {
            if (hasParts) out.append(" ");
            out.append(mins).append("m");
            hasParts = true;
        }

        if (remaining > 0 || hasParts) {
            if (hasParts) out.append(" ");
            out.append(remaining).append("s");
        }

        return out.toString();
    }

    @Nonnull
    public static String formatRemaining(@Nullable Long expiresAt) {
        if (expiresAt == null || expiresAt <= 0) return "permanent";
        long now = System.currentTimeMillis();
        long diff = Math.max(0, expiresAt - now);
        return formatDurationSeconds(diff / 1000L);
    }
}

