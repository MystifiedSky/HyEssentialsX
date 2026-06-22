package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerDataModel {

    private String lastKnownName;
    private long lastSeenAt;
    private long firstJoinAt;
    private Map<String, HomeModel> homes = new HashMap<>();
    private Map<String, Long> kitCooldowns = new HashMap<>();
    private Map<String, Integer> kitUseCounts = new HashMap<>();
    private Map<String, Long> commandCooldowns = new HashMap<>();
    private boolean starterKitClaimed;
    private MuteModel mute;
    private BanModel ban;
    private String language;
    private long balance;
    private Integer balanceScale;
    private BackPointModel back;
    private long playtimeSeconds;
    private long lastJoinAt;
    private String rankupTier;
    private boolean flyEnabled;
    private float flySpeedMultiplier = 1.0F;
    private long lastPaycheckAt;
    private long lastPaycheckPlaytimeSeconds;
    private boolean frozen;
    private int scoreboardOffsetX;
    private int scoreboardOffsetY;
    private boolean scoreboardOffsetCustomized;
    private Boolean scoreboardHidden;
    private Boolean economyHudHidden;
    private List<MailMessageModel> mailInbox = new ArrayList<>();
    private List<MailMessageModel> mailSent = new ArrayList<>();
    private int mailNextId = 1;
    private int mailSentNextId = 1;
    private String lastKnownIp;
    private List<IpHistoryModel> ipHistory = new ArrayList<>();
    private List<String> ignoredPlayerIds = new ArrayList<>();
    private List<String> claimedPlaytimeRewards = new ArrayList<>();

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

    public long getFirstJoinAt() {
        return firstJoinAt;
    }

    public void setFirstJoinAt(long firstJoinAt) {
        this.firstJoinAt = Math.max(0L, firstJoinAt);
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
        if (kitCooldowns == null) {
            kitCooldowns = new HashMap<>();
        }
        return kitCooldowns;
    }

    public void setKitCooldowns(@Nonnull Map<String, Long> kitCooldowns) {
        this.kitCooldowns = kitCooldowns;
    }

    @Nonnull
    public Map<String, Integer> getKitUseCounts() {
        if (kitUseCounts == null) {
            kitUseCounts = new HashMap<>();
        }
        return kitUseCounts;
    }

    public void setKitUseCounts(@Nonnull Map<String, Integer> kitUseCounts) {
        this.kitUseCounts = kitUseCounts;
    }

    @Nonnull
    public Map<String, Long> getCommandCooldowns() {
        return commandCooldowns;
    }

    public void setCommandCooldowns(@Nonnull Map<String, Long> commandCooldowns) {
        this.commandCooldowns = commandCooldowns;
    }

    public boolean isStarterKitClaimed() {
        return starterKitClaimed;
    }

    public void setStarterKitClaimed(boolean starterKitClaimed) {
        this.starterKitClaimed = starterKitClaimed;
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
    public Integer getBalanceScale() {
        return balanceScale;
    }

    public void setBalanceScale(@Nullable Integer balanceScale) {
        this.balanceScale = balanceScale;
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

    public float getFlySpeedMultiplier() {
        if (!Float.isFinite(flySpeedMultiplier) || flySpeedMultiplier <= 0.0F) {
            flySpeedMultiplier = 1.0F;
        }
        return flySpeedMultiplier;
    }

    public void setFlySpeedMultiplier(float flySpeedMultiplier) {
        this.flySpeedMultiplier = Float.isFinite(flySpeedMultiplier) && flySpeedMultiplier > 0.0F
                ? flySpeedMultiplier
                : 1.0F;
    }

    public long getLastPaycheckAt() {
        return lastPaycheckAt;
    }

    public void setLastPaycheckAt(long lastPaycheckAt) {
        this.lastPaycheckAt = Math.max(0L, lastPaycheckAt);
    }

    public long getLastPaycheckPlaytimeSeconds() {
        return lastPaycheckPlaytimeSeconds;
    }

    public void setLastPaycheckPlaytimeSeconds(long lastPaycheckPlaytimeSeconds) {
        this.lastPaycheckPlaytimeSeconds = Math.max(0L, lastPaycheckPlaytimeSeconds);
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public int getScoreboardOffsetX() {
        return scoreboardOffsetX;
    }

    public void setScoreboardOffsetX(int scoreboardOffsetX) {
        this.scoreboardOffsetX = scoreboardOffsetX;
    }

    public int getScoreboardOffsetY() {
        return scoreboardOffsetY;
    }

    public void setScoreboardOffsetY(int scoreboardOffsetY) {
        this.scoreboardOffsetY = scoreboardOffsetY;
    }

    public boolean isScoreboardOffsetCustomized() {
        return scoreboardOffsetCustomized;
    }

    public void setScoreboardOffsetCustomized(boolean scoreboardOffsetCustomized) {
        this.scoreboardOffsetCustomized = scoreboardOffsetCustomized;
    }

    public boolean isScoreboardHidden() {
        return scoreboardHidden != null && scoreboardHidden;
    }

    @Nullable
    public Boolean getScoreboardHidden() {
        return scoreboardHidden;
    }

    public void setScoreboardHidden(@Nullable Boolean scoreboardHidden) {
        this.scoreboardHidden = scoreboardHidden;
    }

    public boolean isEconomyHudHidden() {
        return economyHudHidden != null && economyHudHidden;
    }

    @Nullable
    public Boolean getEconomyHudHidden() {
        return economyHudHidden;
    }

    public void setEconomyHudHidden(@Nullable Boolean economyHudHidden) {
        this.economyHudHidden = economyHudHidden;
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

    @Nonnull
    public List<String> getIgnoredPlayerIds() {
        if (ignoredPlayerIds == null) {
            ignoredPlayerIds = new ArrayList<>();
        }
        return ignoredPlayerIds;
    }

    public void setIgnoredPlayerIds(@Nonnull List<String> ignoredPlayerIds) {
        this.ignoredPlayerIds = ignoredPlayerIds;
    }

    public boolean isIgnoring(@Nonnull UUID playerId) {
        String id = playerId.toString();
        for (String ignored : getIgnoredPlayerIds()) {
            if (id.equalsIgnoreCase(ignored)) {
                return true;
            }
        }
        return false;
    }

    public boolean addIgnoredPlayer(@Nonnull UUID playerId) {
        if (isIgnoring(playerId)) {
            return false;
        }
        return getIgnoredPlayerIds().add(playerId.toString());
    }

    public boolean removeIgnoredPlayer(@Nonnull UUID playerId) {
        String id = playerId.toString();
        return getIgnoredPlayerIds().removeIf(v -> id.equalsIgnoreCase(v));
    }

    @Nonnull
    public List<String> getClaimedPlaytimeRewards() {
        if (claimedPlaytimeRewards == null) {
            claimedPlaytimeRewards = new ArrayList<>();
        }
        return claimedPlaytimeRewards;
    }

    public void setClaimedPlaytimeRewards(@Nonnull List<String> claimedPlaytimeRewards) {
        this.claimedPlaytimeRewards = claimedPlaytimeRewards;
    }

    public boolean hasClaimedPlaytimeReward(@Nonnull String rewardId) {
        String key = rewardId.trim();
        if (key.isBlank()) {
            return false;
        }
        for (String claimed : getClaimedPlaytimeRewards()) {
            if (claimed != null && claimed.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    public boolean addClaimedPlaytimeReward(@Nonnull String rewardId) {
        String key = rewardId.trim();
        if (key.isBlank() || hasClaimedPlaytimeReward(key)) {
            return false;
        }
        return getClaimedPlaytimeRewards().add(key);
    }

    public boolean removeClaimedPlaytimeReward(@Nonnull String rewardId) {
        String key = rewardId.trim();
        if (key.isBlank()) {
            return false;
        }
        return getClaimedPlaytimeRewards().removeIf(v -> key.equalsIgnoreCase(v));
    }

    public void sanitizeForStorage() {
        if (homes == null) {
            homes = new HashMap<>();
        } else {
            homes.entrySet().removeIf(entry -> entry == null
                    || entry.getKey() == null
                    || entry.getKey().isBlank()
                    || entry.getValue() == null);
            for (HomeModel home : homes.values()) {
                home.sanitize();
            }
        }

        if (back != null) {
            back.sanitize();
        }

        if (!Float.isFinite(flySpeedMultiplier) || flySpeedMultiplier <= 0.0F) {
            flySpeedMultiplier = 1.0F;
        }

        if (claimedPlaytimeRewards == null) {
            claimedPlaytimeRewards = new ArrayList<>();
        } else {
            List<String> sanitizedRewards = new ArrayList<>();
            for (String rewardId : claimedPlaytimeRewards) {
                if (rewardId == null) continue;
                String cleaned = rewardId.trim();
                if (cleaned.isBlank()) continue;
                boolean exists = false;
                for (String existing : sanitizedRewards) {
                    if (existing.equalsIgnoreCase(cleaned)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    sanitizedRewards.add(cleaned);
                }
            }
            claimedPlaytimeRewards = sanitizedRewards;
        }
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

