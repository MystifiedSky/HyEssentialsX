package xyz.thelegacyvoyage.hyessentialsx.managers;

import xyz.thelegacyvoyage.hyessentialsx.models.BanModel;
import xyz.thelegacyvoyage.hyessentialsx.models.MuteModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarningEscalationRuleModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarningModel;
import xyz.thelegacyvoyage.hyessentialsx.util.StaffActionUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class WarningEscalationManager {

    private final StorageManager storage;
    private final MuteManager muteManager;
    private final BanManager banManager;

    public WarningEscalationManager(@Nonnull StorageManager storage,
                                    @Nonnull MuteManager muteManager,
                                    @Nonnull BanManager banManager) {
        this.storage = storage;
        this.muteManager = muteManager;
        this.banManager = banManager;
        ensureDefaultRules();
    }

    @Nonnull
    public List<WarningEscalationRuleModel> listRules() {
        ensureDefaultRules();
        return storage.getWarningEscalationRules();
    }

    public void saveRule(@Nonnull WarningEscalationRuleModel rule) {
        storage.setWarningEscalationRule(rule);
    }

    @Nullable
    public WarningEscalationRuleModel getRule(@Nonnull String id) {
        for (WarningEscalationRuleModel rule : listRules()) {
            if (rule != null && rule.getId().equalsIgnoreCase(id)) {
                return rule;
            }
        }
        return null;
    }

    public boolean deleteRule(@Nonnull String id) {
        List<WarningEscalationRuleModel> rules = new ArrayList<>(listRules());
        boolean removed = rules.removeIf(rule -> rule != null && rule.getId().equalsIgnoreCase(id));
        if (removed) {
            storage.saveWarningEscalationRules(rules);
        }
        return removed;
    }

    public void setRuleEnabled(@Nonnull String id, boolean enabled) {
        List<WarningEscalationRuleModel> rules = new ArrayList<>(listRules());
        for (WarningEscalationRuleModel rule : rules) {
            if (rule != null && rule.getId().equalsIgnoreCase(id)) {
                rule.setEnabled(enabled);
                storage.saveWarningEscalationRules(rules);
                return;
            }
        }
    }

    public void resetDefaultRules() {
        storage.saveWarningEscalationRules(defaultRules());
    }

    @Nullable
    public WarningEscalationRuleModel evaluate(@Nonnull UUID playerId,
                                               @Nonnull String playerName,
                                               @Nonnull String actorName) {
        long now = System.currentTimeMillis();
        List<WarningEscalationRuleModel> rules = new ArrayList<>(listRules());
        rules.removeIf(rule -> rule == null || !rule.isEnabled());
        rules.sort(Comparator.comparingInt(WarningEscalationRuleModel::getThreshold).reversed());
        for (WarningEscalationRuleModel rule : rules) {
            long active = countWarnings(playerId, rule.getWindowSeconds(), now);
            if (active < rule.getThreshold()) continue;
            apply(rule, playerId, playerName, actorName);
            return rule;
        }
        return null;
    }

    private long countWarnings(@Nonnull UUID playerId, long windowSeconds, long now) {
        long earliest = windowSeconds <= 0L ? 0L : now - windowSeconds * 1000L;
        long count = 0L;
        for (WarningModel warning : storage.getWarnings(playerId)) {
            if (warning == null || !warning.isActive()) continue;
            if (earliest > 0L && warning.getCreatedAt() < earliest) continue;
            count++;
        }
        return count;
    }

    private void apply(@Nonnull WarningEscalationRuleModel rule,
                       @Nonnull UUID playerId,
                       @Nonnull String playerName,
                       @Nonnull String actorName) {
        long now = System.currentTimeMillis();
        String reason = rule.getReason().isBlank()
                ? "Automatic warning escalation: " + rule.getName()
                : rule.getReason();
        switch (rule.getAction()) {
            case "MUTE" -> muteManager.mute(playerId, new MuteModel(
                    playerName,
                    actorName,
                    reason,
                    expiry(now, rule.getDurationSeconds()),
                    now
            ));
            case "TEMPBAN" -> banManager.ban(playerId, new BanModel(
                    playerName,
                    actorName,
                    reason,
                    expiry(now, Math.max(1L, rule.getDurationSeconds())),
                    now
            ));
            case "BAN" -> banManager.ban(playerId, new BanModel(
                    playerName,
                    actorName,
                    reason,
                    0L,
                    now
            ));
            case "COMMAND" -> storage.addStaffCase(playerId, "escalation-command", actorName,
                    rule.getCommand().replace("{player}", playerName).replace("{uuid}", playerId.toString()));
            default -> {
                return;
            }
        }
        StaffActionUtil.log(storage, actorName, "warning-escalation", playerId, playerName,
                rule.getName() + " -> " + rule.getAction());
    }

    private long expiry(long now, long durationSeconds) {
        return durationSeconds <= 0L ? 0L : now + durationSeconds * 1000L;
    }

    private void ensureDefaultRules() {
        List<WarningEscalationRuleModel> existing = storage.getWarningEscalationRules();
        if (!existing.isEmpty()) {
            return;
        }
        storage.saveWarningEscalationRules(defaultRules());
    }

    @Nonnull
    private List<WarningEscalationRuleModel> defaultRules() {
        return List.of(
                new WarningEscalationRuleModel("warn-3-mute", "3 warnings: temporary mute", 3, "MUTE", 3600L, 604800L,
                        "Automatic escalation after 3 active warnings.", ""),
                new WarningEscalationRuleModel("warn-5-tempban", "5 warnings: temporary ban", 5, "TEMPBAN", 86400L, 604800L,
                        "Automatic escalation after 5 active warnings.", ""),
                new WarningEscalationRuleModel("warn-7-ban", "7 warnings: permanent ban", 7, "BAN", 0L, 2592000L,
                        "Automatic escalation after repeated warnings.", "")
        );
    }
}
