package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import xyz.thelegacyvoyage.hyessentialsx.managers.LanguageManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One safe messaging gateway.
 *
 * IMPORTANT:
 * - Message.raw() does NOT parse "{#RRGGBB}" or "§" formatting codes.
 * - We build a Message with colored segments using Message.color("#RRGGBB").
 *
 * INPUT supported:
 *  - &0..&f / §0..§f (legacy)
 *  - &#RRGGBB / §#RRGGBB (hex)
 *  - {#RRGGBB} (legacy from old system; backwards compatible)
 *  - &r / §r reset (resets to white + clears formatting)
 *
 * Formatting codes:
 *  - &l/§l bold
 *  - &o/§o italic
 *  - &n/§n underline (ignored - not supported by Message)
 *  - &m/§m strikethrough (ignored - not supported by Message)
 *  - &k/§k obfuscated (ignored - not supported by Message)
 */
public final class Messages {

    private Messages() {}

    // Standard colors (hex strings)
    private static final String OK_COLOR = "#55FF55";
    private static final String WARN_COLOR = "#FFFF55";
    private static final String ERR_COLOR = "#FF5555";
    private static final String WHITE_COLOR = "#FFFFFF";
    private static final String COMMAND_PREFIX = "&7[HyEssentialsX]&f ";
    private static LanguageManager languageManager;

    public static void setLanguageManager(LanguageManager manager) {
        languageManager = manager;
    }

    /**
     * Builds a colored Message from the given input string.
     */
    @Nonnull
    public static Message m(@Nonnull String text) {
        text = normalizeMiniTags(text);
        List<Message> parts = new ArrayList<>();

        // Use mutable holders so we can "change" while still reusing flush logic
        final String[] currentColor = new String[]{ WHITE_COLOR };
        final String[] currentLink = new String[]{ null };
        final boolean[] currentBold = new boolean[]{ false };
        final boolean[] currentItalic = new boolean[]{ false };
        final boolean[] currentMono = new boolean[]{ false };

        StringBuilder buf = new StringBuilder();

        final int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);

            // URL: <url:https://example.com>
            if (c == '<') {
                int close = text.indexOf('>', i + 1);
                if (close > i) {
                    String tag = text.substring(i + 1, close).trim();
                    if (tag.length() >= 4 && tag.regionMatches(true, 0, "url:", 0, 4)) {
                        String url = tag.substring(4).trim();
                        flush(parts, buf, currentColor[0], currentLink[0], currentBold[0], currentItalic[0], currentMono[0]);
                        currentLink[0] = url.isEmpty() ? null : url;
                        i = close;
                        continue;
                    }
                    if (tag.equalsIgnoreCase("/url")) {
                        flush(parts, buf, currentColor[0], currentLink[0], currentBold[0], currentItalic[0], currentMono[0]);
                        currentLink[0] = null;
                        i = close;
                        continue;
                    }
                }
            }

            // Backwards compat: {#RRGGBB}
            if (c == '{' && i + 8 < len && text.charAt(i + 1) == '#' && text.charAt(i + 8) == '}') {
                String hex = text.substring(i + 2, i + 8);
                if (isHex6(hex)) {
                    flush(parts, buf, currentColor[0], currentLink[0], currentBold[0], currentItalic[0], currentMono[0]);
                    currentColor[0] = "#" + hex.toUpperCase();
                    i += 8; // skip "{#RRGGBB}"
                    continue;
                }
            }

            // Hex: &#RRGGBB or §#RRGGBB
            if ((c == '&' || c == '§') && i + 7 < len && text.charAt(i + 1) == '#') {
                String hex = text.substring(i + 2, i + 8);
                if (isHex6(hex)) {
                    flush(parts, buf, currentColor[0], currentLink[0], currentBold[0], currentItalic[0], currentMono[0]);
                    currentColor[0] = "#" + hex.toUpperCase();
                    i += 7; // skip "&#RRGGBB"
                    continue;
                }
            }

            // Legacy: &a/§a etc + reset &r/§r + formatting
            if ((c == '&' || c == '§') && i + 1 < len) {
                char code = Character.toLowerCase(text.charAt(i + 1));

                if (code == 'r') {
                    flush(parts, buf, currentColor[0], currentLink[0], currentBold[0], currentItalic[0], currentMono[0]);
                    currentColor[0] = WHITE_COLOR;
                    currentBold[0] = false;
                    currentItalic[0] = false;
                    currentMono[0] = false;
                    i += 1; // skip "&r"
                    continue;
                }

                int idx = Character.digit(code, 16);
                if (idx >= 0 && idx <= 15) {
                    flush(parts, buf, currentColor[0], currentLink[0], currentBold[0], currentItalic[0], currentMono[0]);
                    currentColor[0] = legacyToHex(idx);
                    i += 1; // skip "&<code>"
                    continue;
                }

                // formatting codes
                if (code == 'l') {
                    flush(parts, buf, currentColor[0], currentLink[0], currentBold[0], currentItalic[0], currentMono[0]);
                    currentBold[0] = true;
                    i += 1;
                    continue;
                }
                if (code == 'o') {
                    flush(parts, buf, currentColor[0], currentLink[0], currentBold[0], currentItalic[0], currentMono[0]);
                    currentItalic[0] = true;
                    i += 1;
                    continue;
                }
                // &k, &m, &n are ignored (no support)
                if (code == 'k' || code == 'm' || code == 'n') {
                    i += 1;
                    continue;
                }
            }

            buf.append(c);
        }

        flush(parts, buf, currentColor[0], currentLink[0], currentBold[0], currentItalic[0], currentMono[0]);

        if (parts.isEmpty()) return Message.raw("");
        if (parts.size() == 1) return parts.get(0);
        return Message.join(parts.toArray(new Message[0]));
    }

    private static void flush(@Nonnull List<Message> parts,
                              @Nonnull StringBuilder buf,
                              @Nonnull String color,
                              @Nullable String link,
                              boolean bold,
                              boolean italic,
                              boolean mono) {
        if (buf.length() == 0) return;
        Message msg = Message.raw(buf.toString()).color(color);
        if (bold) msg = msg.bold(true);
        if (italic) msg = msg.italic(true);
        if (mono) msg = msg.monospace(true);
        if (link != null && !link.isBlank()) {
            msg = msg.link(link);
        }
        parts.add(msg);
        buf.setLength(0);
    }

    @Nonnull
    private static String normalizeMiniTags(@Nonnull String text) {
        String out = text;
        out = out.replaceAll("<#([0-9a-fA-F]{6})>", "&#$1");
        out = out.replaceAll("</#([0-9a-fA-F]{6})>", "&#$1");
        out = out.replaceAll("(?i)</?(bold|italic|underlined|strikethrough|obfuscated)>", "");
        return out;
    }

    public static void send(@Nonnull CommandContext ctx, @Nonnull String text) {
        ctx.sendMessage(m(prefix(null) + translateIfKey(null, text)));
    }

    public static void send(@Nonnull PlayerRef player, @Nonnull String text) {
        player.sendMessage(m(translateIfKey(player, text)));
    }

    public static void sendPrefixed(@Nonnull PlayerRef player, @Nonnull String text) {
        player.sendMessage(m(prefix(player) + translateIfKey(player, text)));
    }

    public static void ok(@Nonnull CommandContext ctx, @Nonnull String text) {
        send(ctx, "&#55FF55" + translateIfKey(null, text));
    }

    public static void warn(@Nonnull CommandContext ctx, @Nonnull String text) {
        send(ctx, "&#FFFF55" + translateIfKey(null, text));
    }

    public static void err(@Nonnull CommandContext ctx, @Nonnull String text) {
        send(ctx, "&#FF5555" + translateIfKey(null, text));
    }

    public static void noPerm(@Nonnull CommandContext ctx, @Nonnull String commandName) {
        errKey(ctx, "error.no_permission", Map.of("command", commandName));
    }

    public static void prefix(@Nonnull CommandContext ctx, @Nonnull String prefix, @Nonnull String text) {
        send(ctx, "&#55FF55" + prefix + "&#FFFFFF" + ": " + "&#FFFFFF" + text);
    }

    // -------------------------
    // translation helpers
    // -------------------------

    @Nonnull
    public static String tr(@Nonnull String key) {
        return tr(null, key, Map.of());
    }

    @Nonnull
    public static String tr(@Nullable PlayerRef player, @Nonnull String key, @Nonnull Map<String, String> placeholders) {
        if (languageManager == null) return key;
        return languageManager.translate(player, key, placeholders);
    }

    public static void sendKey(@Nonnull CommandContext ctx, @Nonnull String key, @Nonnull Map<String, String> placeholders) {
        send(ctx, tr(null, key, placeholders));
    }

    public static void sendKey(@Nonnull PlayerRef player, @Nonnull String key, @Nonnull Map<String, String> placeholders) {
        player.sendMessage(m(tr(player, key, placeholders)));
    }

    public static void sendPrefixedKey(@Nonnull PlayerRef player, @Nonnull String key, @Nonnull Map<String, String> placeholders) {
        player.sendMessage(m(prefix(player) + tr(player, key, placeholders)));
    }

    public static void okKey(@Nonnull CommandContext ctx, @Nonnull String key, @Nonnull Map<String, String> placeholders) {
        ok(ctx, tr(null, key, placeholders));
    }

    public static void warnKey(@Nonnull CommandContext ctx, @Nonnull String key, @Nonnull Map<String, String> placeholders) {
        warn(ctx, tr(null, key, placeholders));
    }

    public static void errKey(@Nonnull CommandContext ctx, @Nonnull String key, @Nonnull Map<String, String> placeholders) {
        err(ctx, tr(null, key, placeholders));
    }

    @Nonnull
    private static String prefix(@Nullable PlayerRef player) {
        return COMMAND_PREFIX;
    }

    @Nonnull
    private static String translateIfKey(@Nullable PlayerRef player, @Nonnull String text) {
        if (languageManager == null) return text;
        if (!languageManager.hasKey(text)) return text;
        return languageManager.translate(player, text, Map.of());
    }

    // -------------------------
    // helpers
    // -------------------------

    private static boolean isHex6(@Nonnull String s) {
        if (s.length() != 6) return false;
        for (int i = 0; i < 6; i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!ok) return false;
        }
        return true;
    }

    @Nonnull
    private static String legacyToHex(int idx) {
        return switch (idx) {
            case 0 -> "#000000";
            case 1 -> "#0000AA";
            case 2 -> "#00AA00";
            case 3 -> "#00AAAA";
            case 4 -> "#AA0000";
            case 5 -> "#AA00AA";
            case 6 -> "#FFAA00";
            case 7 -> "#AAAAAA";
            case 8 -> "#555555";
            case 9 -> "#5555FF";
            case 10 -> "#55FF55";
            case 11 -> "#55FFFF";
            case 12 -> "#FF5555";
            case 13 -> "#FF55FF";
            case 14 -> "#FFFF55";
            case 15 -> "#FFFFFF";
            default -> WHITE_COLOR;
        };
    }
}
