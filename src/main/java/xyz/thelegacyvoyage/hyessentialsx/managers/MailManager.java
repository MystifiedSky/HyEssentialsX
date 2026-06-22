package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.models.MailMessageModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MailManager {

    public enum SendStatus {
        OK,
        DISABLED,
        MESSAGE_TOO_LONG,
        COOLDOWN,
        SIMILAR
    }

    public static final class SendResult {
        private final SendStatus status;
        private final long retrySeconds;

        public SendResult(@Nonnull SendStatus status, long retrySeconds) {
            this.status = status;
            this.retrySeconds = retrySeconds;
        }

        @Nonnull
        public SendStatus getStatus() {
            return status;
        }

        public long getRetrySeconds() {
            return retrySeconds;
        }
    }

    private static final class SpamState {
        private long lastSentAt;
        private String lastMessage;
    }

    private final StorageManager storage;
    private final ConfigManager config;
    private final Map<UUID, SpamState> spamStates = new ConcurrentHashMap<>();

    public MailManager(@Nonnull StorageManager storage, @Nonnull ConfigManager config) {
        this.storage = storage;
        this.config = config;
    }

    public boolean isEnabled() {
        return config.isMailEnabled();
    }

    @Nonnull
    public SendResult sendMail(@Nonnull UUID senderId,
                               @Nonnull String senderName,
                               @Nonnull UUID targetId,
                               @Nonnull String targetName,
                               @Nonnull String message,
                               boolean bypassSpam) {
        if (!config.isMailEnabled()) {
            return new SendResult(SendStatus.DISABLED, 0L);
        }
        String trimmed = message.trim();
        int maxLen = config.getMailMaxMessageLength();
        if (maxLen > 0 && trimmed.length() > maxLen) {
            return new SendResult(SendStatus.MESSAGE_TOO_LONG, 0L);
        }

        long now = System.currentTimeMillis();
        if (!bypassSpam) {
            SendResult spamCheck = checkSpam(senderId, trimmed, now);
            if (spamCheck.getStatus() != SendStatus.OK) {
                return spamCheck;
            }
        }

        PlayerDataModel senderData = storage.getPlayerData(senderId);
        PlayerDataModel targetData = storage.getPlayerData(targetId);

        ensureMailDefaults(senderData);
        ensureMailDefaults(targetData);

        int inboxId = targetData.getMailNextId();
        targetData.setMailNextId(inboxId + 1);
        MailMessageModel inboxEntry = new MailMessageModel(
                inboxId,
                senderName,
                senderId.toString(),
                targetName,
                targetId.toString(),
                trimmed,
                now,
                false,
                0L
        );
        targetData.getMailInbox().add(inboxEntry);

        int sentId = senderData.getMailSentNextId();
        senderData.setMailSentNextId(sentId + 1);
        MailMessageModel sentEntry = new MailMessageModel(
                sentId,
                senderName,
                senderId.toString(),
                targetName,
                targetId.toString(),
                trimmed,
                now,
                true,
                now
        );
        senderData.getMailSent().add(sentEntry);

        boolean senderChanged = pruneMail(senderId, senderData);
        boolean targetChanged = pruneMail(targetId, targetData);

        storage.savePlayerDataAsync(targetId, targetData);
        storage.savePlayerDataAsync(senderId, senderData);

        updateSpamState(senderId, trimmed, now);

        if (config.isMailNotifyOnReceive()) {
            PlayerRef online = Universe.get().getPlayer(targetId);
            if (online != null) {
                Messages.sendPrefixedKey(online, "mail.received", Map.of("player", senderName));
            }
        }

        return new SendResult(SendStatus.OK, 0L);
    }

    public int getUnreadCount(@Nonnull UUID uuid) {
        PlayerDataModel data = storage.getPlayerData(uuid);
        ensureMailDefaults(data);
        if (pruneMail(uuid, data)) {
            storage.savePlayerDataAsync(uuid, data);
        }
        int unread = 0;
        for (MailMessageModel entry : data.getMailInbox()) {
            if (entry != null && !entry.isRead()) {
                unread++;
            }
        }
        return unread;
    }

    @Nonnull
    public List<MailMessageModel> getInbox(@Nonnull UUID uuid) {
        PlayerDataModel data = storage.getPlayerData(uuid);
        ensureMailDefaults(data);
        if (pruneMail(uuid, data)) {
            storage.savePlayerDataAsync(uuid, data);
        }
        return new ArrayList<>(data.getMailInbox());
    }

    @Nonnull
    public List<MailMessageModel> getSent(@Nonnull UUID uuid) {
        PlayerDataModel data = storage.getPlayerData(uuid);
        ensureMailDefaults(data);
        if (pruneMail(uuid, data)) {
            storage.savePlayerDataAsync(uuid, data);
        }
        return new ArrayList<>(data.getMailSent());
    }

    @Nullable
    public MailMessageModel markRead(@Nonnull UUID uuid, int id) {
        PlayerDataModel data = storage.getPlayerData(uuid);
        ensureMailDefaults(data);
        for (MailMessageModel entry : data.getMailInbox()) {
            if (entry != null && entry.getId() == id) {
                if (!entry.isRead()) {
                    entry.setRead(true);
                    entry.setReadAt(System.currentTimeMillis());
                    storage.savePlayerDataAsync(uuid, data);
                }
                return entry;
            }
        }
        return null;
    }

    public boolean deleteInbox(@Nonnull UUID uuid, int id) {
        PlayerDataModel data = storage.getPlayerData(uuid);
        ensureMailDefaults(data);
        boolean removed = data.getMailInbox().removeIf(entry -> entry != null && entry.getId() == id);
        if (removed) {
            storage.savePlayerDataAsync(uuid, data);
        }
        return removed;
    }

    public boolean deleteSent(@Nonnull UUID uuid, int id) {
        PlayerDataModel data = storage.getPlayerData(uuid);
        ensureMailDefaults(data);
        boolean removed = data.getMailSent().removeIf(entry -> entry != null && entry.getId() == id);
        if (removed) {
            storage.savePlayerDataAsync(uuid, data);
        }
        return removed;
    }

    public int clearInbox(@Nonnull UUID uuid) {
        PlayerDataModel data = storage.getPlayerData(uuid);
        ensureMailDefaults(data);
        int count = data.getMailInbox().size();
        data.getMailInbox().clear();
        storage.savePlayerDataAsync(uuid, data);
        return count;
    }

    public int clearSent(@Nonnull UUID uuid) {
        PlayerDataModel data = storage.getPlayerData(uuid);
        ensureMailDefaults(data);
        int count = data.getMailSent().size();
        data.getMailSent().clear();
        storage.savePlayerDataAsync(uuid, data);
        return count;
    }

    public void notifyOnJoin(@Nonnull PlayerRef player) {
        if (!config.isMailNotifyOnJoin()) return;
        int unread = getUnreadCount(player.getUuid());
        if (unread > 0) {
            Messages.sendPrefixedKey(player, "mail.unread_join", Map.of("count", String.valueOf(unread)));
        }
    }

    private void ensureMailDefaults(@Nonnull PlayerDataModel data) {
        if (data.getMailInbox() == null) {
            data.setMailInbox(new ArrayList<>());
        }
        if (data.getMailSent() == null) {
            data.setMailSent(new ArrayList<>());
        }
        if (data.getMailNextId() < 1) {
            data.setMailNextId(1);
        }
        if (data.getMailSentNextId() < 1) {
            data.setMailSentNextId(1);
        }
    }

    private boolean pruneMail(@Nonnull UUID uuid, @Nonnull PlayerDataModel data) {
        boolean changed = false;
        int maxAgeDays = config.getMailMaxAgeDays();
        final long cutoff = maxAgeDays > 0
                ? System.currentTimeMillis() - (maxAgeDays * 24L * 60L * 60L * 1000L)
                : 0L;

        if (cutoff > 0) {
            boolean removedInbox = data.getMailInbox().removeIf(entry -> entry != null && entry.getSentAt() > 0 && entry.getSentAt() < cutoff);
            boolean removedSent = data.getMailSent().removeIf(entry -> entry != null && entry.getSentAt() > 0 && entry.getSentAt() < cutoff);
            changed = removedInbox || removedSent;
        }

        int maxInbox = config.getMailMaxInboxSize();
        if (maxInbox > 0 && data.getMailInbox().size() > maxInbox) {
            data.getMailInbox().sort(Comparator.comparingLong(MailMessageModel::getSentAt));
            while (data.getMailInbox().size() > maxInbox) {
                data.getMailInbox().remove(0);
                changed = true;
            }
        }

        int maxSent = config.getMailMaxSentSize();
        if (maxSent > 0 && data.getMailSent().size() > maxSent) {
            data.getMailSent().sort(Comparator.comparingLong(MailMessageModel::getSentAt));
            while (data.getMailSent().size() > maxSent) {
                data.getMailSent().remove(0);
                changed = true;
            }
        }

        return changed;
    }

    @Nonnull
    private SendResult checkSpam(@Nonnull UUID senderId, @Nonnull String message, long now) {
        SpamState state = spamStates.computeIfAbsent(senderId, id -> new SpamState());
        int cooldown = config.getMailCooldownSeconds();
        if (cooldown > 0 && state.lastSentAt > 0) {
            long diff = now - state.lastSentAt;
            long remaining = (cooldown * 1000L) - diff;
            if (remaining > 0) {
                return new SendResult(SendStatus.COOLDOWN, Math.max(1, remaining / 1000L));
            }
        }

        int window = config.getMailSimilarityWindowSeconds();
        double threshold = config.getMailSimilarityThreshold();
        if (window > 0 && threshold > 0 && state.lastMessage != null && state.lastSentAt > 0) {
            if (now - state.lastSentAt <= window * 1000L) {
                double similarity = similarity(message, state.lastMessage);
                if (similarity >= threshold) {
                    return new SendResult(SendStatus.SIMILAR, 0L);
                }
            }
        }

        return new SendResult(SendStatus.OK, 0L);
    }

    private void updateSpamState(@Nonnull UUID senderId, @Nonnull String message, long now) {
        SpamState state = spamStates.computeIfAbsent(senderId, id -> new SpamState());
        state.lastSentAt = now;
        state.lastMessage = normalize(message);
    }

    @Nonnull
    private static String normalize(@Nonnull String message) {
        return message.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private static double similarity(@Nonnull String a, @Nonnull String b) {
        if (a.equals(b)) return 1.0;
        int max = Math.max(a.length(), b.length());
        if (max == 0) return 1.0;
        int distance = levenshtein(a, b);
        return 1.0 - ((double) distance / max);
    }

    private static int levenshtein(@Nonnull String a, @Nonnull String b) {
        int lenA = a.length();
        int lenB = b.length();
        int[] prev = new int[lenB + 1];
        int[] curr = new int[lenB + 1];

        for (int j = 0; j <= lenB; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= lenA; i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= lenB; j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }
        return prev[lenB];
    }

    @Nonnull
    public static String formatAge(long sentAt) {
        if (sentAt <= 0) return "0s";
        long diff = Math.max(0L, System.currentTimeMillis() - sentAt);
        return TimeUtil.formatDurationSeconds(diff / 1000L);
    }
}

