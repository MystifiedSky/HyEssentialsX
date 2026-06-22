package xyz.thelegacyvoyage.hyessentialsx.managers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

public record LeaderboardDefinition(@Nonnull String category,
                                    @Nonnull String stat,
                                    @Nonnull String display,
                                    boolean distance) {

    public static final List<LeaderboardDefinition> DEFAULTS = List.of(
            custom("player_kills", "Player Kills"),
            custom("mob_kills", "Mob Kills"),
            custom("deaths", "Deaths"),
            custom("damage_dealt", "Damage Dealt"),
            custom("damage_taken", "Damage Taken"),
            new LeaderboardDefinition(StatsManager.CATEGORY_CUSTOM, "distance_traveled", "Distance Traveled", true),
            custom("messages_sent", "Messages Sent"),
            custom("times_connected", "Connections"),
            custom("drops", "Drops")
    );

    @Nullable
    public static LeaderboardDefinition resolve(@Nullable String raw) {
        String input = raw == null || raw.isBlank() ? "player_kills" : raw.trim();
        String normalized = input.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "connections", "times_connected" -> custom("times_connected", "Connections");
            case "messages", "messages_sent" -> custom("messages_sent", "Messages Sent");
            case "deaths" -> custom("deaths", "Deaths");
            case "damage_dealt", "damage" -> custom("damage_dealt", "Damage Dealt");
            case "damage_taken" -> custom("damage_taken", "Damage Taken");
            case "player_kills", "kills", "pvp" -> custom("player_kills", "Player Kills");
            case "mob_kills", "mobs" -> custom("mob_kills", "Mob Kills");
            case "drops" -> custom("drops", "Drops");
            case "distance", "distance_traveled" ->
                    new LeaderboardDefinition(StatsManager.CATEGORY_CUSTOM, "distance_traveled", "Distance Traveled", true);
            default -> resolveCategoryStat(input);
        };
    }

    @Nullable
    private static LeaderboardDefinition resolveCategoryStat(@Nonnull String input) {
        int separator = input.indexOf(':');
        if (separator <= 0 || separator >= input.length() - 1) {
            return null;
        }
        String category = input.substring(0, separator).toLowerCase(Locale.ROOT);
        String stat = input.substring(separator + 1);
        if (!isCategoryLeaderboard(category) || stat.isBlank()) {
            return null;
        }
        return new LeaderboardDefinition(category, stat, displayName(category) + ": " + displayName(stat), false);
    }

    public static boolean isCategoryLeaderboard(@Nonnull String category) {
        return StatsManager.CATEGORY_MINED.equals(category)
                || StatsManager.CATEGORY_PLACED.equals(category)
                || StatsManager.CATEGORY_KILLED.equals(category)
                || StatsManager.CATEGORY_KILLED_BY.equals(category)
                || "crafted".equals(category)
                || "dropped".equals(category)
                || "picked_up".equals(category);
    }

    @Nonnull
    public String key() {
        return category + ":" + stat;
    }

    private static LeaderboardDefinition custom(@Nonnull String stat, @Nonnull String display) {
        return new LeaderboardDefinition(StatsManager.CATEGORY_CUSTOM, stat, display, false);
    }

    @Nonnull
    private static String displayName(@Nonnull String id) {
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
}
