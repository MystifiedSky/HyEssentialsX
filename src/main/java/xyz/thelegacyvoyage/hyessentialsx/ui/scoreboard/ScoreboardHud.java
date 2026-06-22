package xyz.thelegacyvoyage.hyessentialsx.ui.scoreboard;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import xyz.thelegacyvoyage.hyessentialsx.util.MultipleHudBridge;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ScoreboardHud extends CustomUIHud {

    public static final String LAYOUT = "hyessentialsx/ScoreboardHud.ui";
    public static final String HUD_ID = "HyEssentialsX_Scoreboard";
    private static final String DEFAULT_TEXT_COLOR = "#f6f8ff";
    private static final AtomicBoolean LOGGED_MULTIPLEHUD_MISSING = new AtomicBoolean(false);

    @Nullable
    private volatile State state;

    public ScoreboardHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    public void update(@Nonnull Player player,
                       @Nonnull PlayerRef playerRef,
                       @Nonnull State state,
                       boolean useMultipleHud) {
        this.state = state;
        if (useMultipleHud) {
            if (MultipleHudBridge.isAvailable()) {
                MultipleHudBridge.setCustomHud(player, playerRef, HUD_ID, this);
            } else if (LOGGED_MULTIPLEHUD_MISSING.compareAndSet(false, true)) {
                Log.warn("[HyEssentialsX] MultipleHUD not available; scoreboard HUD disabled to avoid overriding other HUDs.");
            }
            return;
        }
        player.getHudManager().setCustomHud(playerRef, this);
    }

    public void hide(@Nonnull Player player, @Nonnull PlayerRef playerRef, boolean useMultipleHud) {
        if (useMultipleHud) {
            if (MultipleHudBridge.isAvailable()) {
                MultipleHudBridge.hideCustomHud(player, playerRef, HUD_ID);
            }
            return;
        }
        player.getHudManager().setCustomHud(playerRef, null);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder builder) {
        builder.append(LAYOUT);
        State state = this.state;
        if (state == null) {
            builder.set("#ScoreboardRoot.Visible", false);
            return;
        }

        builder.set("#ScoreboardRoot.Visible", true);
        String background = normalizeBackgroundColor(state.backgroundColor);
        if (!background.isBlank()) {
            builder.set("#ScoreboardRoot.Background", background);
        }
        builder.set("#ScoreboardRoot.Padding.Top", state.paddingTop);
        builder.set("#ScoreboardRoot.Padding.Bottom", state.paddingBottom);
        builder.set("#ScoreboardRoot.Padding.Left", state.paddingLeft);
        builder.set("#ScoreboardRoot.Padding.Right", state.paddingRight);

        Anchor rootAnchor = buildRootAnchor(state);
        builder.setObject("#ScoreboardRoot.Anchor", rootAnchor);

        int contentWidth = Math.max(0, state.width - state.paddingLeft - state.paddingRight);
        int lineCount = state.lines.size();
        int lineHeight = Math.max(1, state.lineHeight);
        int lineSpacing = Math.max(0, state.lineSpacing);
        int linesHeight = lineCount <= 0 ? 0 : lineCount * lineHeight + (lineCount - 1) * lineSpacing;
        int headerHeight = state.logoVisible ? state.logoHeight + state.logoPaddingBottom : 0;

        builder.set("#ScoreboardHeader.Visible", state.logoVisible);
        builder.set("#ScoreboardLogo.Visible", state.logoVisible);
        builder.set("#ScoreboardLogoSpacer.Visible", state.logoVisible);

        Anchor headerAnchor = new Anchor();
        headerAnchor.setWidth(Value.of(contentWidth));
        headerAnchor.setHeight(Value.of(Math.max(0, headerHeight)));
        builder.setObject("#ScoreboardHeader.Anchor", headerAnchor);

        Anchor linesAnchor = new Anchor();
        linesAnchor.setWidth(Value.of(contentWidth));
        linesAnchor.setHeight(Value.of(Math.max(0, linesHeight)));
        builder.setObject("#ScoreboardLines.Anchor", linesAnchor);

        if (state.logoVisible) {
            Anchor logoAnchor = new Anchor();
            logoAnchor.setWidth(Value.of(Math.max(1, state.logoWidth)));
            logoAnchor.setHeight(Value.of(Math.max(1, state.logoHeight)));
            builder.setObject("#ScoreboardLogo.Anchor", logoAnchor);

            Anchor spacerAnchor = new Anchor();
            spacerAnchor.setHeight(Value.of(Math.max(0, state.logoPaddingBottom)));
            builder.setObject("#ScoreboardLogoSpacer.Anchor", spacerAnchor);
        }

        builder.clear("#ScoreboardLines");
        String lineColor = (state.textColor == null || state.textColor.isBlank()) ? DEFAULT_TEXT_COLOR : state.textColor;

        int fontSize = Math.max(8, state.fontSize);
        for (int i = 0; i < state.lines.size(); i++) {
            builder.append("#ScoreboardLines", "hyessentialsx/ScoreboardLineItem.ui");
            String selector = "#ScoreboardLines[" + i + "]";
            String lineText = state.lines.get(i);
            if (lineText == null) {
                lineText = "";
            }
            if (lineText.isEmpty()) {
                lineText = " ";
            }

            int rowHeight = lineHeight + ((i < lineCount - 1) ? lineSpacing : 0);
            Anchor itemAnchor = new Anchor();
            itemAnchor.setWidth(Value.of(contentWidth));
            itemAnchor.setHeight(Value.of(rowHeight));
            builder.setObject(selector + ".Anchor", itemAnchor);

            CenteredLine centered = parseCenteredLine(lineText);
            String resolvedLine = centered.text;
            if (resolvedLine.isEmpty()) {
                resolvedLine = " ";
            }
            List<LineSegment> segments = parseSegments(resolvedLine, lineColor);
            if (segments.isEmpty()) {
                segments = List.of(new LineSegment(" ", lineColor, false));
            }

            builder.appendInline(selector,
                    "Label { Anchor: (Height: " + rowHeight + ", Width: " + contentWidth + "); " +
                            "Style: (VerticalAlignment: Center); }");
            String labelSelector = selector + "[0]";
            builder.set(labelSelector + ".Style.FontSize", fontSize);
            if (centered.center) {
                builder.set(labelSelector + ".Style.HorizontalAlignment", "Center");
            }
            builder.set(labelSelector + ".TextSpans", buildMessage(segments, lineColor));
        }
    }

    private Anchor buildRootAnchor(@Nonnull State state) {
        Anchor anchor = new Anchor();
        anchor.setWidth(Value.of(Math.max(1, state.width)));
        anchor.setHeight(Value.of(Math.max(1, state.height)));
        switch (state.anchor) {
            case "top_left" -> {
                anchor.setLeft(Value.of(state.offsetX));
                anchor.setTop(Value.of(state.offsetY));
            }
            case "bottom_left" -> {
                anchor.setLeft(Value.of(state.offsetX));
                anchor.setBottom(Value.of(state.offsetY));
            }
            case "bottom_right" -> {
                anchor.setRight(Value.of(state.offsetX));
                anchor.setBottom(Value.of(state.offsetY));
            }
            default -> {
                anchor.setRight(Value.of(state.offsetX));
                anchor.setTop(Value.of(state.offsetY));
            }
        }
        return anchor;
    }

    @Nonnull
    private static List<LineSegment> parseSegments(@Nonnull String text, @Nonnull String defaultColor) {
        String normalized = normalizeMiniTags(text);
        List<LineSegment> segments = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        String currentColor = (defaultColor == null || defaultColor.isBlank()) ? DEFAULT_TEXT_COLOR : defaultColor;
        boolean bold = false;

        int len = normalized.length();
        for (int i = 0; i < len; i++) {
            char c = normalized.charAt(i);

            if (c == '{' && i + 8 < len && normalized.charAt(i + 1) == '#' && normalized.charAt(i + 8) == '}') {
                String hex = normalized.substring(i + 2, i + 8);
                if (isHex6(hex)) {
                    flushSegment(segments, buf, currentColor, bold);
                    currentColor = "#" + hex.toUpperCase();
                    i += 8;
                    continue;
                }
            }

            if ((c == '&' || c == '\u00A7') && i + 7 < len && normalized.charAt(i + 1) == '#') {
                String hex = normalized.substring(i + 2, i + 8);
                if (isHex6(hex)) {
                    flushSegment(segments, buf, currentColor, bold);
                    currentColor = "#" + hex.toUpperCase();
                    i += 7;
                    continue;
                }
            }

            if ((c == '&' || c == '\u00A7') && i + 1 < len) {
                char code = Character.toLowerCase(normalized.charAt(i + 1));
                if (code == 'r') {
                    flushSegment(segments, buf, currentColor, bold);
                    currentColor = (defaultColor == null || defaultColor.isBlank())
                            ? DEFAULT_TEXT_COLOR
                            : defaultColor;
                    bold = false;
                    i += 1;
                    continue;
                }
                int idx = Character.digit(code, 16);
                if (idx >= 0 && idx <= 15) {
                    flushSegment(segments, buf, currentColor, bold);
                    currentColor = legacyToHex(idx);
                    i += 1;
                    continue;
                }
                if (code == 'l') {
                    flushSegment(segments, buf, currentColor, bold);
                    bold = true;
                    i += 1;
                    continue;
                }
                if (code == 'k' || code == 'm' || code == 'n' || code == 'o') {
                    i += 1;
                    continue;
                }
            }

            buf.append(c);
        }

        flushSegment(segments, buf, currentColor, bold);
        return segments;
    }

    private static void flushSegment(@Nonnull List<LineSegment> segments,
                                     @Nonnull StringBuilder buf,
                                     @Nonnull String color,
                                     boolean bold) {
        if (buf.length() == 0) {
            return;
        }
        segments.add(new LineSegment(buf.toString(), color, bold));
        buf.setLength(0);
    }

    @Nonnull
    private static CenteredLine parseCenteredLine(@Nonnull String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        int open = lower.indexOf("<center>");
        int close = lower.indexOf("</center>");
        if (open >= 0 && close > open) {
            String inner = text.substring(open + "<center>".length(), close);
            String before = text.substring(0, open);
            String after = text.substring(close + "</center>".length());
            return new CenteredLine(true, before + inner + after);
        }
        return new CenteredLine(false, text);
    }

    @Nonnull
    private static Message buildMessage(@Nonnull List<LineSegment> segments, @Nonnull String fallbackColor) {
        List<Message> parts = new ArrayList<>(segments.size());
        for (LineSegment segment : segments) {
            if (segment.text.isEmpty()) {
                continue;
            }
            Message msg = Message.raw(segment.text);
            if (segment.color != null && !segment.color.isBlank()) {
                msg = msg.color(segment.color);
            } else if (fallbackColor != null && !fallbackColor.isBlank()) {
                msg = msg.color(fallbackColor);
            }
            if (segment.bold) {
                msg = msg.bold(true);
            }
            parts.add(msg);
        }
        if (parts.isEmpty()) {
            return Message.raw(" ");
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return Message.join(parts.toArray(new Message[0]));
    }

    @Nonnull
    private static String normalizeMiniTags(@Nonnull String text) {
        String out = text;
        out = out.replaceAll("<#([0-9a-fA-F]{6})>", "&#$1");
        out = out.replaceAll("</#([0-9a-fA-F]{6})>", "&#$1");
        out = out.replaceAll("(?i)</?(bold|italic|underlined|strikethrough|obfuscated)>", "");
        return out;
    }

    private static boolean isHex6(@Nonnull String s) {
        if (s.length() != 6) {
            return false;
        }
        for (int i = 0; i < 6; i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    @Nonnull
    private static String normalizeBackgroundColor(@Nullable String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (!trimmed.startsWith("#")) {
            trimmed = "#" + trimmed;
        }
        int alphaStart = trimmed.indexOf('(');
        if (alphaStart > 0 && trimmed.endsWith(")")) {
            String colorPart = trimmed.substring(0, alphaStart).trim();
            String alphaPart = trimmed.substring(alphaStart + 1, trimmed.length() - 1).trim();
            if (isHexColor(colorPart, 6)) {
                try {
                    double alpha = Double.parseDouble(alphaPart);
                    alpha = Math.max(0.0, Math.min(1.0, alpha));
                    int alphaByte = (int) Math.round(255.0 * alpha);
                    return colorPart + String.format(java.util.Locale.ROOT, "%02x", alphaByte);
                } catch (NumberFormatException ignored) {
                    return "";
                }
            }
        }
        if (isHexColor(trimmed, 6) || isHexColor(trimmed, 8)) {
            return trimmed;
        }
        return "";
    }

    private static boolean isHexColor(@Nonnull String value, int digits) {
        if (value.length() != digits + 1 || value.charAt(0) != '#') {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!ok) {
                return false;
            }
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
            default -> DEFAULT_TEXT_COLOR;
        };
    }

    private static final class LineSegment {
        private final String text;
        private final String color;
        private final boolean bold;

        private LineSegment(@Nonnull String text, @Nonnull String color, boolean bold) {
            this.text = text;
            this.color = color;
            this.bold = bold;
        }
    }

    private static final class CenteredLine {
        private final boolean center;
        private final String text;

        private CenteredLine(boolean center, @Nonnull String text) {
            this.center = center;
            this.text = text;
        }
    }

    public static final class State {
        public final List<String> lines;
        public final String anchor;
        public final int offsetX;
        public final int offsetY;
        public final int width;
        public final int height;
        public final int paddingTop;
        public final int paddingBottom;
        public final int paddingLeft;
        public final int paddingRight;
        public final int lineHeight;
        public final int lineSpacing;
        public final int fontSize;
        public final String backgroundColor;
        public final String textColor;
        public final boolean logoVisible;
        public final String logoTexture;
        public final int logoWidth;
        public final int logoHeight;
        public final int logoPaddingBottom;

        public State(@Nonnull List<String> lines,
                     @Nonnull String anchor,
                     int offsetX,
                     int offsetY,
                     int width,
                     int height,
                     int paddingTop,
                     int paddingBottom,
                     int paddingLeft,
                     int paddingRight,
                     int lineHeight,
                     int lineSpacing,
                     int fontSize,
                     @Nonnull String backgroundColor,
                     @Nonnull String textColor,
                     boolean logoVisible,
                     @Nonnull String logoTexture,
                     int logoWidth,
                     int logoHeight,
                     int logoPaddingBottom) {
            this.lines = lines;
            this.anchor = anchor;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.width = width;
            this.height = height;
            this.paddingTop = paddingTop;
            this.paddingBottom = paddingBottom;
            this.paddingLeft = paddingLeft;
            this.paddingRight = paddingRight;
            this.lineHeight = lineHeight;
            this.lineSpacing = lineSpacing;
            this.fontSize = fontSize;
            this.backgroundColor = backgroundColor;
            this.textColor = textColor;
            this.logoVisible = logoVisible;
            this.logoTexture = logoTexture;
            this.logoWidth = logoWidth;
            this.logoHeight = logoHeight;
            this.logoPaddingBottom = logoPaddingBottom;
        }
    }
}
