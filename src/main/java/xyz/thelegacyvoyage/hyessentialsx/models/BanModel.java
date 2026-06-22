package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nullable;

public final class BanModel {

    private String playerName;
    private String actorName;
    private String reason;
    private long expiresAt;
    private long createdAt;

    @SuppressWarnings("unused")
    public BanModel() {}

    public BanModel(@Nullable String playerName,
                    @Nullable String actorName,
                    @Nullable String reason,
                    long expiresAt,
                    long createdAt) {
        this.playerName = playerName;
        this.actorName = actorName;
        this.reason = reason;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    @Nullable
    public String getPlayerName() {
        return playerName;
    }

    @Nullable
    public String getActorName() {
        return actorName;
    }

    @Nullable
    public String getReason() {
        return reason;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}

