package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import xyz.thelegacyvoyage.hyessentialsx.models.EconomyAuditEntryModel;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EconomyAuditManager {

    private static final int DEFAULT_MAX_ENTRIES = 1000;
    private static final Type ENTRY_LIST_TYPE = new TypeToken<List<EconomyAuditEntryModel>>() {}.getType();

    private final Path filePath;
    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private final ExecutorService ioPool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HyEssentialsX-EconomyAudit");
        t.setDaemon(true);
        return t;
    });
    private final Object lock = new Object();
    private final List<EconomyAuditEntryModel> entries = new ArrayList<>();

    private volatile int maxEntries = DEFAULT_MAX_ENTRIES;

    public EconomyAuditManager(@Nonnull Path dataDirectory) {
        this.filePath = dataDirectory.resolve("economy-audit-log.json");
        loadFromDisk();
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = Math.max(100, maxEntries);
        synchronized (lock) {
            trimLocked();
        }
        saveAsync();
    }

    public void log(@Nonnull String action,
                    @Nonnull String actor,
                    @Nonnull String target,
                    long amount,
                    long balanceAfter,
                    @Nullable String detail) {
        EconomyAuditEntryModel entry = new EconomyAuditEntryModel(
                System.currentTimeMillis(),
                action,
                actor,
                target,
                amount,
                balanceAfter,
                detail
        );
        synchronized (lock) {
            entries.add(0, entry);
            trimLocked();
        }
        saveAsync();
    }

    @Nonnull
    public List<EconomyAuditEntryModel> recent(int limit) {
        int max = Math.max(1, limit);
        synchronized (lock) {
            int count = Math.min(max, entries.size());
            List<EconomyAuditEntryModel> out = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                out.add(entries.get(i));
            }
            return out;
        }
    }

    public int count(@Nullable String filter) {
        synchronized (lock) {
            if (filter == null || filter.isBlank()) {
                return entries.size();
            }
            String normalized = filter.trim().toLowerCase(Locale.ROOT);
            int count = 0;
            for (EconomyAuditEntryModel entry : entries) {
                if (matches(entry, normalized)) {
                    count++;
                }
            }
            return count;
        }
    }

    @Nonnull
    public List<EconomyAuditEntryModel> query(@Nullable String filter, int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, limit);
        synchronized (lock) {
            List<EconomyAuditEntryModel> filtered;
            if (filter == null || filter.isBlank()) {
                filtered = entries;
            } else {
                String normalized = filter.trim().toLowerCase(Locale.ROOT);
                filtered = new ArrayList<>();
                for (EconomyAuditEntryModel entry : entries) {
                    if (matches(entry, normalized)) {
                        filtered.add(entry);
                    }
                }
            }
            if (safeOffset >= filtered.size()) {
                return List.of();
            }
            int end = Math.min(filtered.size(), safeOffset + safeLimit);
            List<EconomyAuditEntryModel> out = new ArrayList<>(end - safeOffset);
            for (int i = safeOffset; i < end; i++) {
                out.add(filtered.get(i));
            }
            return out;
        }
    }

    public void forceSave() {
        writeSnapshot(snapshot());
    }

    public void shutdown() {
        try {
            forceSave();
        } finally {
            ioPool.shutdownNow();
        }
    }

    private void loadFromDisk() {
        if (!Files.exists(filePath)) {
            return;
        }
        try {
            String json = Files.readString(filePath, StandardCharsets.UTF_8);
            List<EconomyAuditEntryModel> loaded = gson.fromJson(json, ENTRY_LIST_TYPE);
            if (loaded == null || loaded.isEmpty()) {
                return;
            }
            loaded.removeIf(entry -> entry == null);
            loaded.sort(Comparator.comparingLong(EconomyAuditEntryModel::getTimestamp).reversed());
            synchronized (lock) {
                entries.clear();
                entries.addAll(loaded);
                trimLocked();
            }
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to load economy audit log: " + e.getMessage());
        }
    }

    private void saveAsync() {
        List<EconomyAuditEntryModel> snapshot = snapshot();
        CompletableFuture.runAsync(() -> writeSnapshot(snapshot), ioPool);
    }

    @Nonnull
    private List<EconomyAuditEntryModel> snapshot() {
        synchronized (lock) {
            return new ArrayList<>(entries);
        }
    }

    private void writeSnapshot(@Nonnull List<EconomyAuditEntryModel> snapshot) {
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(filePath, gson.toJson(snapshot), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to save economy audit log: " + e.getMessage());
        }
    }

    private void trimLocked() {
        int max = Math.max(100, maxEntries);
        while (entries.size() > max) {
            entries.remove(entries.size() - 1);
        }
    }

    private boolean matches(@Nonnull EconomyAuditEntryModel entry, @Nonnull String filter) {
        return contains(entry.getAction(), filter)
                || contains(entry.getActor(), filter)
                || contains(entry.getTarget(), filter)
                || contains(entry.getDetail(), filter);
    }

    private boolean contains(@Nullable String value, @Nonnull String filter) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(filter);
    }
}
