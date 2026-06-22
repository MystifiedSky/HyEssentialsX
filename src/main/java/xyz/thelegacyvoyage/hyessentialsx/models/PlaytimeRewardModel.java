package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class PlaytimeRewardModel {

    private String id;
    private long requiredSeconds;
    private long requiredCost;
    private String rank = "";
    private boolean autoClaim = true;
    private List<String> commands = new ArrayList<>();
    private String broadcastMessage = "";

    @SuppressWarnings("unused")
    public PlaytimeRewardModel() {
    }

    public PlaytimeRewardModel(@Nonnull String id,
                               long requiredSeconds,
                               @Nonnull List<String> commands,
                               @Nullable String broadcastMessage) {
        this(id, requiredSeconds, 0L, "", true, commands, broadcastMessage);
    }

    public PlaytimeRewardModel(@Nonnull String id,
                               long requiredSeconds,
                               long requiredCost,
                               @Nullable String rank,
                               boolean autoClaim,
                               @Nonnull List<String> commands,
                               @Nullable String broadcastMessage) {
        this.id = id;
        this.requiredSeconds = Math.max(0L, requiredSeconds);
        this.requiredCost = Math.max(0L, requiredCost);
        this.rank = rank == null ? "" : rank.trim();
        this.autoClaim = autoClaim;
        this.commands = new ArrayList<>(commands);
        this.broadcastMessage = broadcastMessage == null ? "" : broadcastMessage;
    }

    @Nonnull
    public String getId() {
        return id == null ? "" : id;
    }

    public void setId(@Nonnull String id) {
        this.id = id;
    }

    public long getRequiredSeconds() {
        return Math.max(0L, requiredSeconds);
    }

    public void setRequiredSeconds(long requiredSeconds) {
        this.requiredSeconds = Math.max(0L, requiredSeconds);
    }

    public long getRequiredCost() {
        return Math.max(0L, requiredCost);
    }

    public void setRequiredCost(long requiredCost) {
        this.requiredCost = Math.max(0L, requiredCost);
    }

    @Nonnull
    public String getRank() {
        return rank == null ? "" : rank;
    }

    public void setRank(@Nullable String rank) {
        this.rank = rank == null ? "" : rank.trim();
    }

    public boolean isAutoClaim() {
        return autoClaim;
    }

    public void setAutoClaim(boolean autoClaim) {
        this.autoClaim = autoClaim;
    }

    public boolean isRankupTier() {
        String targetRank = getRank();
        return !targetRank.isBlank();
    }

    @Nonnull
    public List<String> getCommands() {
        if (commands == null) {
            commands = new ArrayList<>();
        }
        return commands;
    }

    public void setCommands(@Nonnull List<String> commands) {
        this.commands = new ArrayList<>(commands);
    }

    @Nonnull
    public String getBroadcastMessage() {
        return broadcastMessage == null ? "" : broadcastMessage;
    }

    public void setBroadcastMessage(@Nullable String broadcastMessage) {
        this.broadcastMessage = broadcastMessage == null ? "" : broadcastMessage;
    }
}
