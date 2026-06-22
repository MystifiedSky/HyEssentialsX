package xyz.thelegacyvoyage.hyessentialsx.managers.hologram.placeholder;

import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.HologramService;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.PlaceholderApiUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.regex.Pattern;

public class PlaceholderIntegration {

    private static final Pattern GENERIC_PLACEHOLDER_PATTERN = Pattern.compile(".*(\\{[^}]+}|%[^%]+%).*");

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
        return applyBasicPlaceholders(text, null, null, null, 0, 0, 0);
    }

    public boolean containsPlaceholders(@Nonnull String text) {
        return GENERIC_PLACEHOLDER_PATTERN.matcher(text).matches();
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
        PlayerRef playerRef = findPlayerRef(playerUuid);
        if (playerRef == null) {
            return text;
        }
        try {
            Message msg = PlaceholderApiUtil.apply(playerRef, text);
            return extractMessageText(msg, text);
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

    @Nonnull
    private String extractMessageText(@Nonnull Message message, @Nonnull String fallback) {
        try {
            Method getText = message.getClass().getMethod("getText");
            Object value = getText.invoke(message);
            if (value instanceof String s) {
                return s;
            }
        } catch (Throwable ignored) {
        }
        try {
            Method getRaw = message.getClass().getMethod("getRaw");
            Object value = getRaw.invoke(message);
            if (value instanceof String s) {
                return s;
            }
        } catch (Throwable ignored) {
        }
        try {
            Field text = message.getClass().getDeclaredField("text");
            text.setAccessible(true);
            Object value = text.get(message);
            if (value instanceof String s) {
                return s;
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }
}

