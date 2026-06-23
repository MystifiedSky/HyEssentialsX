package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nullable;

public final class WarningModel {

    private String id;
    private String playerName;
    private String issuer;
    private String reason;
    private long createdAt;
    private long expiresAt;
    private boolean active = true;

    @SuppressWarnings("unused")
    public WarningModel() {
    }

    public WarningModel(@Nullable String id,
                        @Nullable String playerName,
                        @Nullable String issuer,
                        @Nullable String reason,
                        long createdAt,
                        long expiresAt) {
        this.id = id;
        this.playerName = playerName;
        this.issuer = issuer;
        this.reason = reason;
        this.createdAt = Math.max(0L, createdAt);
        this.expiresAt = Math.max(0L, expiresAt);
        this.active = true;
    }

    @Nullable
    public String getId() {
        return id;
    }

    public void setId(@Nullable String id) {
        this.id = id;
    }

    @Nullable
    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(@Nullable String playerName) {
        this.playerName = playerName;
    }

    @Nullable
    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(@Nullable String issuer) {
        this.issuer = issuer;
    }

    @Nullable
    public String getReason() {
        return reason;
    }

    public void setReason(@Nullable String reason) {
        this.reason = reason;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = Math.max(0L, createdAt);
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = Math.max(0L, expiresAt);
    }

    public boolean isActive() {
        return active && (expiresAt <= 0L || expiresAt > System.currentTimeMillis());
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
