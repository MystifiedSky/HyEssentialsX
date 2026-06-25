package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AnnouncementPresetModel {

    private String name;
    private boolean enabled;
    @Nullable
    private String permission;
    private List<String> chatMessages;
    @Nullable
    private NotificationAction notification;
    @Nullable
    private TitleAction title;
    @Nullable
    private SoundAction sound;
    @Nullable
    private ParticleAction particle;
    private List<String> serverCommands;
    private List<String> playerCommands;

    public AnnouncementPresetModel() {
        this("server_tip");
    }

    public AnnouncementPresetModel(@Nonnull String name) {
        this.name = sanitizeName(name);
        this.enabled = true;
        this.chatMessages = new ArrayList<>();
        this.serverCommands = new ArrayList<>();
        this.playerCommands = new ArrayList<>();
    }

    @Nonnull
    public AnnouncementPresetModel copy() {
        AnnouncementPresetModel copy = new AnnouncementPresetModel(name);
        copy.enabled = enabled;
        copy.permission = permission;
        copy.chatMessages = new ArrayList<>(chatMessages == null ? List.of() : chatMessages);
        copy.notification = notification == null ? null : notification.copy();
        copy.title = title == null ? null : title.copy();
        copy.sound = sound == null ? null : sound.copy();
        copy.particle = particle == null ? null : particle.copy();
        copy.serverCommands = new ArrayList<>(serverCommands == null ? List.of() : serverCommands);
        copy.playerCommands = new ArrayList<>(playerCommands == null ? List.of() : playerCommands);
        return copy;
    }

    @Nonnull
    public String getName() {
        return name == null || name.isBlank() ? "announcement" : name;
    }

    public void setName(@Nonnull String name) {
        this.name = sanitizeName(name);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Nonnull
    public String getPermission() {
        return permission == null ? "" : permission.trim();
    }

    public void setPermission(@Nullable String permission) {
        String trimmed = permission == null ? "" : permission.trim();
        this.permission = trimmed.isBlank() || "none".equalsIgnoreCase(trimmed) ? null : trimmed;
    }

    @Nonnull
    public List<String> getChatMessages() {
        return chatMessages == null ? List.of() : chatMessages;
    }

    public void setChatMessages(@Nonnull List<String> chatMessages) {
        this.chatMessages = sanitizeList(chatMessages);
    }

    @Nullable
    public NotificationAction getNotification() {
        return notification;
    }

    public void setNotification(@Nullable NotificationAction notification) {
        this.notification = notification;
    }

    @Nullable
    public TitleAction getTitle() {
        return title;
    }

    public void setTitle(@Nullable TitleAction title) {
        this.title = title;
    }

    @Nullable
    public SoundAction getSound() {
        return sound;
    }

    public void setSound(@Nullable SoundAction sound) {
        this.sound = sound;
    }

    @Nullable
    public ParticleAction getParticle() {
        return particle;
    }

    public void setParticle(@Nullable ParticleAction particle) {
        this.particle = particle;
    }

    @Nonnull
    public List<String> getServerCommands() {
        return serverCommands == null ? List.of() : serverCommands;
    }

    public void setServerCommands(@Nonnull List<String> serverCommands) {
        this.serverCommands = sanitizeList(serverCommands);
    }

    @Nonnull
    public List<String> getPlayerCommands() {
        return playerCommands == null ? List.of() : playerCommands;
    }

    public void setPlayerCommands(@Nonnull List<String> playerCommands) {
        this.playerCommands = sanitizeList(playerCommands);
    }

    @Nonnull
    private static String sanitizeName(@Nonnull String input) {
        String trimmed = input.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < trimmed.length() && out.length() < 32; i++) {
            char c = trimmed.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                out.append(c);
            }
        }
        return out.isEmpty() ? "announcement" : out.toString();
    }

    @Nonnull
    private static List<String> sanitizeList(@Nonnull List<String> input) {
        List<String> out = new ArrayList<>();
        for (String line : input) {
            if (line == null || line.isBlank()) continue;
            out.add(line.trim());
        }
        return out;
    }

    public static final class NotificationAction {
        private String title = "";
        private String message = "";
        private String icon = "";
        private String style = "Default";

        @Nonnull
        public NotificationAction copy() {
            NotificationAction copy = new NotificationAction();
            copy.title = title;
            copy.message = message;
            copy.icon = icon;
            copy.style = style;
            return copy;
        }

        @Nonnull public String getTitle() { return title == null ? "" : title; }
        public void setTitle(@Nonnull String title) { this.title = title; }
        @Nonnull public String getMessage() { return message == null ? "" : message; }
        public void setMessage(@Nonnull String message) { this.message = message; }
        @Nonnull public String getIcon() { return icon == null ? "" : icon; }
        public void setIcon(@Nonnull String icon) { this.icon = icon; }
        @Nonnull public String getStyle() { return style == null ? "Default" : style; }
        public void setStyle(@Nonnull String style) { this.style = style; }
    }

    public static final class TitleAction {
        private String primary = "";
        private String secondary = "";
        private boolean major = false;

        @Nonnull
        public TitleAction copy() {
            TitleAction copy = new TitleAction();
            copy.primary = primary;
            copy.secondary = secondary;
            copy.major = major;
            return copy;
        }

        @Nonnull public String getPrimary() { return primary == null ? "" : primary; }
        public void setPrimary(@Nonnull String primary) { this.primary = primary; }
        @Nonnull public String getSecondary() { return secondary == null ? "" : secondary; }
        public void setSecondary(@Nonnull String secondary) { this.secondary = secondary; }
        public boolean isMajor() { return major; }
        public void setMajor(boolean major) { this.major = major; }
    }

    public static final class SoundAction {
        private int soundEventIndex = -1;
        private String category = "Music";
        private float volume = 1.0f;
        private float pitch = 1.0f;

        @Nonnull
        public SoundAction copy() {
            SoundAction copy = new SoundAction();
            copy.soundEventIndex = soundEventIndex;
            copy.category = category;
            copy.volume = volume;
            copy.pitch = pitch;
            return copy;
        }

        public int getSoundEventIndex() { return soundEventIndex; }
        public void setSoundEventIndex(int soundEventIndex) { this.soundEventIndex = soundEventIndex; }
        @Nonnull public String getCategory() { return category == null ? "Music" : category; }
        public void setCategory(@Nonnull String category) { this.category = category; }
        public float getVolume() { return volume; }
        public void setVolume(float volume) { this.volume = volume; }
        public float getPitch() { return pitch; }
        public void setPitch(float pitch) { this.pitch = pitch; }
    }

    public static final class ParticleAction {
        private String particleSystemId = "";
        private float scale = 1.0f;
        private String color = "";

        @Nonnull
        public ParticleAction copy() {
            ParticleAction copy = new ParticleAction();
            copy.particleSystemId = particleSystemId;
            copy.scale = scale;
            copy.color = color;
            return copy;
        }

        @Nonnull public String getParticleSystemId() { return particleSystemId == null ? "" : particleSystemId; }
        public void setParticleSystemId(@Nonnull String particleSystemId) { this.particleSystemId = particleSystemId; }
        public float getScale() { return scale; }
        public void setScale(float scale) { this.scale = scale; }
        @Nonnull public String getColor() { return color == null ? "" : color; }
        public void setColor(@Nonnull String color) { this.color = color; }
    }
}
