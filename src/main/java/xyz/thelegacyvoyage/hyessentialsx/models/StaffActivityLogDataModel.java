package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class StaffActivityLogDataModel {

    private List<StaffActivityEntryModel> entries = new ArrayList<>();

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
    }
}
