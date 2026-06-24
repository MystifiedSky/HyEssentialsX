package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class StaffActivityLogDataModel {

    private List<StaffActivityEntryModel> entries = new ArrayList<>();
    private List<WarningEscalationRuleModel> warningEscalationRules = new ArrayList<>();

    @Nonnull
    public List<StaffActivityEntryModel> getEntries() {
        if (entries == null) {
            entries = new ArrayList<>();
        }
        return entries;
    }

    public void setEntries(@Nonnull List<StaffActivityEntryModel> entries) {
        this.entries = entries;
    }

    @Nonnull
    public List<WarningEscalationRuleModel> getWarningEscalationRules() {
        if (warningEscalationRules == null) {
            warningEscalationRules = new ArrayList<>();
        }
        return warningEscalationRules;
    }

    public void setWarningEscalationRules(@Nonnull List<WarningEscalationRuleModel> warningEscalationRules) {
        this.warningEscalationRules = warningEscalationRules;
    }

    public void sanitize(int maxEntries) {
        List<StaffActivityEntryModel> sanitized = new ArrayList<>();
        for (StaffActivityEntryModel entry : getEntries()) {
            if (entry != null && entry.getCreatedAt() > 0L) {
                sanitized.add(entry);
            }
        }
        sanitized.sort(Comparator.comparingLong(StaffActivityEntryModel::getCreatedAt).reversed());
        if (sanitized.size() > maxEntries) {
            sanitized = new ArrayList<>(sanitized.subList(0, maxEntries));
        }
        entries = sanitized;

        List<WarningEscalationRuleModel> rules = new ArrayList<>();
        for (WarningEscalationRuleModel rule : getWarningEscalationRules()) {
            if (rule == null || rule.getId().isBlank()) continue;
            rule.sanitize();
            rules.add(rule);
        }
        rules.sort(Comparator.comparingInt(WarningEscalationRuleModel::getThreshold));
        warningEscalationRules = rules;
    }
}
