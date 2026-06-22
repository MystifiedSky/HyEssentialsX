package xyz.thelegacyvoyage.hyessentialsx.managers;

import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;

import javax.annotation.Nonnull;
import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class StatsManager {

    public static final String CATEGORY_CUSTOM = "custom";
    public static final String CATEGORY_MINED = "mined";
    public static final String CATEGORY_PLACED = "placed";
    public static final String CATEGORY_KILLED = "killed";
    public static final String CATEGORY_KILLED_BY = "killed_by";

    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,###");

    private final StorageManager storage;
    private final ConfigManager config;

    public StatsManager(@Nonnull StorageManager storage, @Nonnull ConfigManager config) {
        this.storage = storage;
        this.config = config;
    }

    public boolean isEnabled() {
        return config.isStatsEnabled();
    }

    public boolean shouldTrackMovement() {
        return config.isStatsTrackMovement();
    }

    public void increment(@Nonnull UUID uuid, @Nonnull String category, @Nonnull String stat) {
        increment(uuid, category, stat, 1L);
    }

    public void increment(@Nonnull UUID uuid, @Nonnull String category, @Nonnull String stat, long amount) {
        if (!isEnabled() || amount <= 0L || !isValidKey(category) || !isValidKey(stat)) {
            return;
        }
        PlayerDataModel data = storage.getPlayerData(uuid);
        data.incrementStat(category, stat, amount);
        storage.savePlayerDataAsync(uuid, data);
    }

    public long get(@Nonnull UUID uuid, @Nonnull String category, @Nonnull String stat) {
        PlayerDataModel data = storage.getPlayerData(uuid);
        return data.getStat(category, stat);
    }

    @Nonnull
    public Map<String, Long> getCategory(@Nonnull UUID uuid, @Nonnull String category) {
        PlayerDataModel data = storage.getPlayerData(uuid);
        return data.getStatCategory(category);
    }

    @Nonnull
    public List<Map.Entry<String, Long>> topStats(@Nonnull UUID uuid, @Nonnull String category, int limit) {
        return getCategory(uuid, category).entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0L)
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                .limit(Math.max(1, limit))
                .toList();
    }

    @Nonnull
    public String formatNumber(long value) {
        return NUMBER_FORMAT.format(Math.max(0L, value));
    }

    @Nonnull
    public String formatDistance(long centimeters) {
        if (centimeters < 100L) {
            return centimeters + " cm";
        }
        double meters = centimeters / 100.0D;
        if (meters < 1000.0D) {
            return String.format(Locale.ROOT, "%.1f m", meters);
        }
        return String.format(Locale.ROOT, "%.2f km", meters / 1000.0D);
    }

    @Nonnull
    public String displayName(@Nonnull String id) {
        String normalized = id.replace("hystats__environment_damage_", "")
                .replace(':', ' ')
                .replace('.', ' ')
                .replace('_', ' ')
                .replace('-', ' ')
                .trim();
        if (normalized.isBlank()) {
            return id;
        }
        String[] parts = normalized.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.isEmpty() ? id : builder.toString();
    }

    private boolean isValidKey(@Nonnull String key) {
        return !key.isBlank() && key.matches("^[.\\w:-]+$");
    }
}
