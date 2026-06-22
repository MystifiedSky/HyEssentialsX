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
    private MuteModel mute;
    private BanModel ban;

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
}
