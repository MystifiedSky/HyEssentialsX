package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;

public final class ChatGroupFormat {
    private final String name;
    private final String permission;
    private final String format;

    public ChatGroupFormat(@Nonnull String name, @Nonnull String permission, @Nonnull String format) {
        this.name = name;
        this.permission = permission;
        this.format = format;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    @Nonnull
    public String getPermission() {
        return permission;
    }

    @Nonnull
    public String getFormat() {
        return format;
    }
}
