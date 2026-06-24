package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

public final class WarningEscalationRuleModel {

    private String id;
    private String name;
    private boolean enabled = true;
    private int threshold;
    private String action;
    private long durationSeconds;
    private long windowSeconds;
    private String reason;
    private String command;
    private long createdAt;
    private long updatedAt;

    @SuppressWarnings("unused")
    public WarningEscalationRuleModel() {
    }

    public WarningEscalationRuleModel(@Nonnull String id,
                                      @Nonnull String name,
                                      int threshold,
                                      @Nonnull String action,
                                      long durationSeconds,
                                      long windowSeconds,
                                      @Nullable String reason,
                                      @Nullable String command) {
        long now = System.currentTimeMillis();
        this.id = id;
        this.name = name;
        this.threshold = Math.max(1, threshold);
        this.action = normalizeAction(action);
        this.durationSeconds = Math.max(0L, durationSeconds);
        this.windowSeconds = Math.max(0L, windowSeconds);
        this.reason = reason == null ? "" : reason.trim();
        this.command = command == null ? "" : command.trim();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @Nonnull
    public String getId() {
        return id == null ? "" : id;
    }

    public void setId(@Nullable String id) {
        this.id = id;
    }

    @Nonnull
    public String getName() {
        return name == null || name.isBlank() ? getId() : name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
        touch();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        touch();
    }

    public int getThreshold() {
        return Math.max(1, threshold);
    }

    public void setThreshold(int threshold) {
        this.threshold = Math.max(1, threshold);
        touch();
    }

    @Nonnull
    public String getAction() {
        return normalizeAction(action);
    }

    public void setAction(@Nullable String action) {
        this.action = normalizeAction(action);
        touch();
    }

    public long getDurationSeconds() {
        return Math.max(0L, durationSeconds);
    }

    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = Math.max(0L, durationSeconds);
        touch();
    }

    public long getWindowSeconds() {
        return Math.max(0L, windowSeconds);
    }

    public void setWindowSeconds(long windowSeconds) {
        this.windowSeconds = Math.max(0L, windowSeconds);
        touch();
    }

    @Nonnull
    public String getReason() {
        return reason == null ? "" : reason;
    }

    public void setReason(@Nullable String reason) {
        this.reason = reason == null ? "" : reason.trim();
        touch();
    }

    @Nonnull
    public String getCommand() {
        return command == null ? "" : command;
    }

    public void setCommand(@Nullable String command) {
        this.command = command == null ? "" : command.trim();
        touch();
    }

    public long getCreatedAt() {
        return Math.max(0L, createdAt);
    }

    public long getUpdatedAt() {
        return Math.max(0L, updatedAt);
    }

    public void sanitize() {
        if (id == null || id.isBlank()) {
            id = "rule-" + Math.max(1, threshold);
        }
        if (name == null || name.isBlank()) {
            name = id;
        }
        threshold = Math.max(1, threshold);
        action = normalizeAction(action);
        durationSeconds = Math.max(0L, durationSeconds);
        windowSeconds = Math.max(0L, windowSeconds);
        if (reason == null) reason = "";
        if (command == null) command = "";
        createdAt = Math.max(0L, createdAt);
        updatedAt = Math.max(createdAt, updatedAt);
    }

    private void touch() {
        updatedAt = System.currentTimeMillis();
    }

    @Nonnull
    private static String normalizeAction(@Nullable String raw) {
        String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MUTE", "TEMPBAN", "BAN", "COMMAND" -> normalized;
            default -> "MUTE";
        };
    }
}
