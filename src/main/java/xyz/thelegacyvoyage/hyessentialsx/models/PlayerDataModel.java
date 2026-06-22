package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private boolean frozen;
    private List<MailMessageModel> mailInbox = new ArrayList<>();
    private List<MailMessageModel> mailSent = new ArrayList<>();
    private int mailNextId = 1;
    private int mailSentNextId = 1;
    private String lastKnownIp;
    private List<IpHistoryModel> ipHistory = new ArrayList<>();

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

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    @Nonnull
    public List<MailMessageModel> getMailInbox() {
        if (mailInbox == null) {
            mailInbox = new ArrayList<>();
        }
        return mailInbox;
    }

    public void setMailInbox(@Nonnull List<MailMessageModel> mailInbox) {
        this.mailInbox = mailInbox;
    }

    @Nonnull
    public List<MailMessageModel> getMailSent() {
        if (mailSent == null) {
            mailSent = new ArrayList<>();
        }
        return mailSent;
    }

    public void setMailSent(@Nonnull List<MailMessageModel> mailSent) {
        this.mailSent = mailSent;
    }

    public int getMailNextId() {
        return Math.max(1, mailNextId);
    }

    public void setMailNextId(int mailNextId) {
        this.mailNextId = Math.max(1, mailNextId);
    }

    public int getMailSentNextId() {
        return Math.max(1, mailSentNextId);
    }

    public void setMailSentNextId(int mailSentNextId) {
        this.mailSentNextId = Math.max(1, mailSentNextId);
    }

    @Nullable
    public String getLastKnownIp() {
        return lastKnownIp;
    }

    public void setLastKnownIp(@Nullable String lastKnownIp) {
        this.lastKnownIp = lastKnownIp;
    }

    @Nonnull
    public List<IpHistoryModel> getIpHistory() {
        if (ipHistory == null) {
            ipHistory = new ArrayList<>();
        }
        return ipHistory;
    }

    public void setIpHistory(@Nonnull List<IpHistoryModel> ipHistory) {
        this.ipHistory = ipHistory;
    }

    public void addOrUpdateIp(@Nonnull String ip) {
        String trimmed = ip.trim();
        if (trimmed.isBlank()) return;
        List<IpHistoryModel> history = getIpHistory();
        long now = System.currentTimeMillis();
        for (IpHistoryModel entry : history) {
            if (entry == null || entry.getIp() == null) continue;
            if (entry.getIp().equals(trimmed)) {
                entry.setLastUsed(now);
                this.lastKnownIp = trimmed;
                return;
            }
        }
        history.add(new IpHistoryModel(trimmed, now));
        if (history.size() > 5) {
            IpHistoryModel oldest = null;
            for (IpHistoryModel entry : history) {
                if (entry == null) continue;
                if (oldest == null || entry.getLastUsed() < oldest.getLastUsed()) {
                    oldest = entry;
                }
            }
            if (oldest != null) {
                history.remove(oldest);
            }
        }
        this.lastKnownIp = trimmed;
    }

    @Nullable
    public String getCurrentIp() {
        List<IpHistoryModel> history = getIpHistory();
        IpHistoryModel latest = null;
        for (IpHistoryModel entry : history) {
            if (entry == null || entry.getIp() == null || entry.getIp().isBlank()) continue;
            if (latest == null || entry.getLastUsed() > latest.getLastUsed()) {
                latest = entry;
            }
        }
        if (latest != null) {
            return latest.getIp();
        }
        return (lastKnownIp == null || lastKnownIp.isBlank()) ? null : lastKnownIp;
    }
}

