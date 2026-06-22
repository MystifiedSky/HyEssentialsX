package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;
import java.util.List;

public final class RankupTier {

    private final String rank;
    private final long playtimeSeconds;
    private final long cost;
    private final List<String> commands;

    public RankupTier(@Nonnull String rank,
                      long playtimeSeconds,
                      long cost,
                      @Nonnull List<String> commands) {
        this.rank = rank;
        this.playtimeSeconds = Math.max(0L, playtimeSeconds);
        this.cost = Math.max(0L, cost);
        this.commands = List.copyOf(commands);
    }

    @Nonnull
    public String getRank() {
        return rank;
    }

    public long getPlaytimeSeconds() {
        return playtimeSeconds;
    }

    public double getPlaytimeHours() {
        return playtimeSeconds / 3600.0;
    }

    public long getCost() {
        return cost;
    }

    @Nonnull
    public List<String> getCommands() {
        return commands;
    }
}

