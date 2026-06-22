package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nullable;

public final class IpBanModel {

    private String ip;
    private String playerName;
    private String playerUuid;
    private String actorName;
    private String reason;
    private long createdAt;

    @SuppressWarnings("unused")
    public IpBanModel() {}

    public IpBanModel(@Nullable String ip,
                      @Nullable String playerName,
                      @Nullable String playerUuid,
                      @Nullable String actorName,
                      @Nullable String reason,
                      long createdAt) {
        this.ip = ip;
        this.playerName = playerName;
        this.playerUuid = playerUuid;
        this.actorName = actorName;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    @Nullable
    public String getIp() {
        return ip;
    }

    @Nullable
    public String getPlayerName() {
        return playerName;
    }

    @Nullable
    public String getPlayerUuid() {
        return playerUuid;
    }

    @Nullable
    public String getActorName() {
        return actorName;
    }

    @Nullable
    public String getReason() {
        return reason;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
