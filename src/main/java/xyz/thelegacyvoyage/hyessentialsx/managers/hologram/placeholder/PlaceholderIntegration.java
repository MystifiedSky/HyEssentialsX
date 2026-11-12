package xyz.thelegacyvoyage.hyessentialsx.managers.hologram.placeholder;

import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.HologramService;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.PlaceholderApiUtil;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public class PlaceholderIntegration {

    private final HologramService plugin;
    private final ConfigManager config;

    public PlaceholderIntegration(@Nonnull HologramService plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public boolean isAvailable() {
        return config.isHologramsEnabled() && config.isHologramPlaceholdersEnabled();
    }

    @Nonnull
    public String processPlaceholders(@Nonnull String text, @Nullable UUID playerUuid, @Nullable String playerName) {
        String processed = applyBasicPlaceholders(text, playerUuid, playerName, null, 0, 0, 0);
        return applyPlaceholderApi(processed, playerUuid);
    }

    @Nonnull
    public String processPlaceholdersWithFullContext(@Nonnull String text,
                                                     @Nullable Player player,
                                                     @Nullable UUID playerUuid,
                                                     @Nullable String playerName,
                                                     @Nullable String worldName,
                                                     int x,
                                                     int y,
                                                     int z) {
        String processed = applyBasicPlaceholders(text, playerUuid, playerName, worldName, x, y, z);
        return applyPlaceholderApi(processed, playerUuid);
    }

    @Nonnull
    public String processPlaceholders(@Nonnull String text, @Nullable UUID playerUuid) {
        return processPlaceholders(text, playerUuid, null);
    }

    @Nonnull
    public String processPlaceholders(@Nonnull String text) {
        String processed = applyBasicPlaceholders(text, null, null, null, 0, 0, 0);
        return applyPlaceholderApi(processed, null);
    }

    public boolean containsPlaceholders(@Nonnull String text) {
        return containsPlaceholderTokens(text);
    }

    static boolean containsPlaceholderTokens(@Nonnull String text) {
        // The former whole-line pattern allowed line breaks inside a token only.
        int firstLineBreak = text.length();
        int lastLineBreak = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029') {
                firstLineBreak = Math.min(firstLineBreak, i);
                lastLineBreak = i;
            }
        }
        return containsDelimitedToken(text, '{', '}', firstLineBreak, lastLineBreak)
                || containsDelimitedToken(text, '%', '%', firstLineBreak, lastLineBreak);
    }

    private static boolean containsDelimitedToken(String text, char opening, char closing,
                                                   int firstLineBreak, int lastLineBreak) {
        int start = text.indexOf(opening);
        while (start >= 0 && start <= firstLineBreak) {
            int end = text.indexOf(closing, start + 1);
            if (end < 0) {
                return false;
            }
            if (end > start + 1 && end >= lastLineBreak) {
                return true;
            }
            start = text.indexOf(opening, end);
        }
        return false;
    }

    @Nonnull
    private String applyBasicPlaceholders(@Nonnull String text,
                                          @Nullable UUID playerUuid,
                                          @Nullable String playerName,
                                          @Nullable String worldName,
                                          int x,
                                          int y,
                                          int z) {
        String out = text;
        if (playerName != null) {
            out = out.replace("{player}", playerName).replace("{player_name}", playerName);
        }
        if (playerUuid != null) {
            out = out.replace("{player_uuid}", playerUuid.toString());
        }
        if (worldName != null) {
            out = out.replace("{world}", worldName);
        }
        out = out.replace("{x}", String.valueOf(x))
                 .replace("{y}", String.valueOf(y))
                 .replace("{z}", String.valueOf(z));
        return out;
    }

    @Nonnull
    private String applyPlaceholderApi(@Nonnull String text, @Nullable UUID playerUuid) {
        if (!isAvailable()) {
            return text;
        }
        try {
            PlayerRef playerRef = findPlayerRef(playerUuid);
            return PlaceholderApiUtil.applyString(playerRef, text);
        } catch (Throwable ignored) {
            return text;
        }
    }

    @Nullable
    private PlayerRef findPlayerRef(@Nullable UUID playerUuid) {
        if (playerUuid == null) return null;
        try {
            for (PlayerRef ref : Universe.get().getPlayers()) {
                if (playerUuid.equals(ref.getUuid())) {
                    return ref;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

}

