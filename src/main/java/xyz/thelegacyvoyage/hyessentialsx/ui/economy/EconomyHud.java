package xyz.thelegacyvoyage.hyessentialsx.ui.economy;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class EconomyHud extends CustomUIHud {

    public static final String LAYOUT = "hyessentialsx/EconomyHud.ui";
    public static final String HUD_ID = "HyEssentialsX_EconomyHud";

    @Nullable
    private volatile State state;

    public EconomyHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, HUD_ID);
    }

    public void update(@Nonnull Player player,
                       @Nonnull PlayerRef playerRef,
                       @Nonnull State state) {
        this.state = state;
        player.getHudManager().addCustomHud(playerRef, this);
        show();
    }

    public void hide(@Nonnull Player player,
                     @Nonnull PlayerRef playerRef) {
        player.getHudManager().removeCustomHud(playerRef, HUD_ID);
    }

    @Override
    protected void onRemove() {
        this.state = null;
    }

    @Override
    protected void build(@Nonnull UICommandBuilder builder) {
        builder.append(LAYOUT);
        State state = this.state;
        if (state == null) {
            builder.set("#EconomyHudRoot.Visible", false);
            return;
        }
        builder.set("#EconomyHudRoot.Visible", true);
        builder.set("#CurrencyName.Text", state.labelText);
        builder.set("#BalanceSymbol.Text", state.symbolText);
        builder.set("#BalanceAmount.Text", state.amountText);
        builder.set("#CurrencyName.Style.TextColor", sanitizeColor(state.labelColor, "#888888"));
        builder.set("#BalanceSymbol.Style.TextColor", sanitizeColor(state.symbolColor, "#e8b923"));
        builder.set("#BalanceAmount.Style.TextColor", sanitizeColor(state.amountColor, "#ffffff"));
        builder.set("#EconomyHudRoot.Background", sanitizeBackground(state.backgroundColor, "#1a1e2dd8"));
        builder.setObject("#EconomyHudRoot.Anchor", buildAnchor(state));
    }

    @Nonnull
    private Anchor buildAnchor(@Nonnull State state) {
        Anchor anchor = new Anchor();
        anchor.setWidth(Value.of(Math.max(1, state.width)));
        anchor.setHeight(Value.of(Math.max(1, state.height)));
        int x = Math.max(0, state.offsetX);
        int y = Math.max(0, state.offsetY);
        switch (state.anchor) {
            case "top_left" -> {
                anchor.setLeft(Value.of(x));
                anchor.setTop(Value.of(y));
            }
            case "top_right" -> {
                anchor.setRight(Value.of(x));
                anchor.setTop(Value.of(y));
            }
            case "bottom_left" -> {
                anchor.setLeft(Value.of(x));
                anchor.setBottom(Value.of(y));
            }
            default -> {
                anchor.setRight(Value.of(x));
                anchor.setBottom(Value.of(y));
            }
        }
        return anchor;
    }

    @Nonnull
    private String sanitizeColor(@Nullable String color, @Nonnull String fallback) {
        if (color == null || color.isBlank()) {
            return fallback;
        }
        String trimmed = color.trim();
        if (!trimmed.startsWith("#")) {
            trimmed = "#" + trimmed;
        }
        if (isHex(trimmed, 6) || isHex(trimmed, 8)) {
            return trimmed;
        }
        return fallback;
    }

    @Nonnull
    private String sanitizeBackground(@Nullable String value, @Nonnull String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("#")) {
            trimmed = "#" + trimmed;
        }
        int alphaStart = trimmed.indexOf('(');
        if (alphaStart > 0 && trimmed.endsWith(")")) {
            String colorPart = trimmed.substring(0, alphaStart).trim();
            String alphaPart = trimmed.substring(alphaStart + 1, trimmed.length() - 1).trim();
            if (isHex(colorPart, 6)) {
                try {
                    double alpha = Double.parseDouble(alphaPart);
                    alpha = Math.max(0.0, Math.min(1.0, alpha));
                    int alphaByte = (int) Math.round(alpha * 255.0);
                    return colorPart + String.format(java.util.Locale.ROOT, "%02x", alphaByte);
                } catch (NumberFormatException ignored) {
                    return fallback;
                }
            }
            return fallback;
        }
        if (isHex(trimmed, 6) || isHex(trimmed, 8)) {
            return trimmed;
        }
        return fallback;
    }

    private boolean isHex(@Nonnull String value, int digits) {
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

    public static final class State {
        public final String labelText;
        public final String symbolText;
        public final String amountText;
        public final String anchor;
        public final int offsetX;
        public final int offsetY;
        public final int width;
        public final int height;
        public final String backgroundColor;
        public final String labelColor;
        public final String symbolColor;
        public final String amountColor;

        public State(@Nonnull String labelText,
                     @Nonnull String symbolText,
                     @Nonnull String amountText,
                     @Nonnull String anchor,
                     int offsetX,
                     int offsetY,
                     int width,
                     int height,
                     @Nonnull String backgroundColor,
                     @Nonnull String labelColor,
                     @Nonnull String symbolColor,
                     @Nonnull String amountColor) {
            this.labelText = labelText;
            this.symbolText = symbolText;
            this.amountText = amountText;
            this.anchor = anchor;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.width = width;
            this.height = height;
            this.backgroundColor = backgroundColor;
            this.labelColor = labelColor;
            this.symbolColor = symbolColor;
            this.amountColor = amountColor;
        }
    }
}
