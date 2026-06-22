package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public final class PlayerDataModel {

    private String lastKnownName;
    private long lastSeenAt;
    private Map<String, HomeModel> homes = new HashMap<>();
    private Map<String, Long> kitCooldowns = new HashMap<>();
    private Map<String, Long> commandCooldowns = new HashMap<>();
    private MuteModel mute;
    private BanModel ban;
    private String language;
    private long balance;
    private BackPointModel back;
    private long playtimeSeconds;
    private long lastJoinAt;
    private String rankupTier;
    private boolean flyEnabled;
    private long lastPaycheckAt;

    @SuppressWarnings("unused")
    public PlayerDataModel() {}

    @Nullable
    public String getLastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(@Nullable String lastKnownName) {
        this.lastKnownName = lastKnownName;
    }

    public long getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(long lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    @Nonnull
    public Map<String, HomeModel> getHomes() {
        return homes;
    }

    public void setHomes(@Nonnull Map<String, HomeModel> homes) {
        this.homes = homes;
    }

    @Nonnull
    public Map<String, Long> getKitCooldowns() {
        return kitCooldowns;
    }

    public void setKitCooldowns(@Nonnull Map<String, Long> kitCooldowns) {
        this.kitCooldowns = kitCooldowns;
    }

    @Nonnull
    public Map<String, Long> getCommandCooldowns() {
        return commandCooldowns;
    }

    public void setCommandCooldowns(@Nonnull Map<String, Long> commandCooldowns) {
        this.commandCooldowns = commandCooldowns;
    }

    @Nullable
    public MuteModel getMute() {
        return mute;
    }

    public void setMute(@Nullable MuteModel mute) {
        this.mute = mute;
    }

    @Nullable
    public BanModel getBan() {
        return ban;
    }

    public void setBan(@Nullable BanModel ban) {
        this.ban = ban;
    }

    @Nullable
    public String getLanguage() {
        return language;
    }

    public void setLanguage(@Nullable String language) {
        this.language = language;
    }

    public long getBalance() {
        return balance;
    }

    public void setBalance(long balance) {
        this.balance = balance;
    }

    @Nullable
    public BackPointModel getBack() {
        return back;
    }

    public void setBack(@Nullable BackPointModel back) {
        this.back = back;
    }

    public long getPlaytimeSeconds() {
        return playtimeSeconds;
    }

    public void setPlaytimeSeconds(long playtimeSeconds) {
        this.playtimeSeconds = Math.max(0L, playtimeSeconds);
    }

    public long getLastJoinAt() {
        return lastJoinAt;
    }

    public void setLastJoinAt(long lastJoinAt) {
        this.lastJoinAt = Math.max(0L, lastJoinAt);
    }

    @Nullable
    public String getRankupTier() {
        return rankupTier;
    }

    public void setRankupTier(@Nullable String rankupTier) {
        this.rankupTier = rankupTier;
    }

    public boolean isFlyEnabled() {
        return flyEnabled;
    }

    public void setFlyEnabled(boolean flyEnabled) {
        this.flyEnabled = flyEnabled;
    }

    public long getLastPaycheckAt() {
        return lastPaycheckAt;
    }

    public void setLastPaycheckAt(long lastPaycheckAt) {
        this.lastPaycheckAt = Math.max(0L, lastPaycheckAt);
    }
}
