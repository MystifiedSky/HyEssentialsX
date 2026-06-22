package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.component.CommandBuffer;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public final class TPManager {

    private static final double CANCEL_DISTANCE = 0.25;
    private static final long DEFAULT_TPA_TIMEOUT_MS = 60_000L;

    private final ConcurrentHashMap<UUID, PendingTeleport> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, TpaRequest>> tpaByTarget = new ConcurrentHashMap<>();
    private volatile long tpaRequestTimeoutMs;
    private final ConcurrentHashMap<UUID, Boolean> tpaIgnore = new ConcurrentHashMap<>();

    public TPManager() {
        this(DEFAULT_TPA_TIMEOUT_MS);
    }

    public TPManager(long tpaRequestTimeoutMs) {
        this.tpaRequestTimeoutMs = tpaRequestTimeoutMs;
    }

    public void setTpaRequestTimeoutMs(long tpaRequestTimeoutMs) {
        this.tpaRequestTimeoutMs = Math.max(0L, tpaRequestTimeoutMs);
    }

    public boolean hasPending(@Nonnull UUID playerId) {
        return pending.containsKey(playerId);
    }

    public void cancel(@Nonnull UUID playerId, @Nullable String reason) {
        PendingTeleport p = pending.remove(playerId);
        // reason is handled by caller (command/listener) using Msg util
    }

    public void onPlayerQuit(@Nonnull UUID playerId) {
        pending.remove(playerId);
        removeAllTpaFor(playerId);
        tpaIgnore.remove(playerId);
    }

    public void clearAll() {
        pending.clear();
        tpaByTarget.clear();
        tpaIgnore.clear();
    }


    public interface TeleportAction {
        void execute(@Nonnull CommandBuffer<EntityStore> buffer);
    }

    public void queue(@Nonnull UUID playerId,
                      @Nonnull Vector3d startPos,
                      float delaySeconds,
                      @Nonnull TeleportAction executeTeleport) {
        pending.put(playerId, new PendingTeleport(new org.joml.Vector3d(startPos), delaySeconds, executeTeleport));
    }

    public static final class TickResult {
        public enum Status {
            NONE,
            WAITING,
            CANCELLED,
            READY
        }

        private final Status status;
        private final TeleportAction action;

        private TickResult(@Nonnull Status status, @Nullable TeleportAction action) {
            this.status = status;
            this.action = action;
        }

        @Nonnull
        public Status status() {
            return status;
        }

        @Nullable
        public TeleportAction action() {
            return action;
        }

        @Nonnull
        public static TickResult none() {
            return new TickResult(Status.NONE, null);
        }

        @Nonnull
        public static TickResult waiting() {
            return new TickResult(Status.WAITING, null);
        }

        @Nonnull
        public static TickResult cancelled() {
            return new TickResult(Status.CANCELLED, null);
        }

        @Nonnull
        public static TickResult ready(@Nonnull TeleportAction action) {
            return new TickResult(Status.READY, action);
        }
    }

    @Nonnull
    public TickResult tickStatus(@Nonnull UUID playerId,
                                 @Nonnull Vector3d currentPos,
                                 float deltaSeconds) {
        PendingTeleport p = pending.get(playerId);
        if (p == null) return TickResult.none();

        double maxSq = CANCEL_DISTANCE * CANCEL_DISTANCE;
        if (p.startPos.distanceSquared(currentPos) > maxSq) {
            pending.remove(playerId);
            return TickResult.cancelled();
        }

        p.elapsed += deltaSeconds;
        if (p.elapsed < p.delay) return TickResult.waiting();

        pending.remove(playerId);
        return TickResult.ready(p.execute);
    }

    public boolean tick(@Nonnull UUID playerId,
                        @Nonnull Vector3d currentPos,
                        float deltaSeconds) {
        return tickStatus(playerId, currentPos, deltaSeconds).status() == TickResult.Status.READY;
    }

    // -------------------------
    // TPA requests
    // -------------------------

    public enum TpaType {
        TO_TARGET,
        HERE
    }

    /**
     * @return true if this request is new, false if it already existed and is still valid
     */
    public boolean addTpaRequest(@Nonnull UUID requester, @Nonnull UUID target) {
        return addTpaRequest(requester, target, TpaType.TO_TARGET);
    }

    public boolean addTpaHereRequest(@Nonnull UUID requester, @Nonnull UUID target) {
        return addTpaRequest(requester, target, TpaType.HERE);
    }

    private boolean addTpaRequest(@Nonnull UUID requester, @Nonnull UUID target, @Nonnull TpaType type) {
        if (isTpaIgnored(target)) return false;
        ConcurrentHashMap<UUID, TpaRequest> byRequester =
                tpaByTarget.computeIfAbsent(target, ignored -> new ConcurrentHashMap<>());
        long now = System.currentTimeMillis();
        TpaRequest existing = byRequester.get(requester);
        if (existing != null && !existing.isExpired(now, tpaRequestTimeoutMs)) {
            return false;
        }
        byRequester.put(requester, new TpaRequest(now, type));
        return true;
    }

    public boolean hasTpaRequest(@Nonnull UUID requester, @Nonnull UUID target) {
        ConcurrentHashMap<UUID, TpaRequest> byRequester = tpaByTarget.get(target);
        if (byRequester == null) return false;
        TpaRequest request = byRequester.get(requester);
        if (request == null) return false;
        long now = System.currentTimeMillis();
        if (request.isExpired(now, tpaRequestTimeoutMs)) {
            byRequester.remove(requester);
            if (byRequester.isEmpty()) {
                tpaByTarget.remove(target, byRequester);
            }
            return false;
        }
        return true;
    }

    public record LatestRequest(@Nonnull UUID requester, @Nonnull TpaType type) {}

    @Nullable
    public LatestRequest getLatestTpaRequester(@Nonnull UUID target) {
        ConcurrentHashMap<UUID, TpaRequest> byRequester = tpaByTarget.get(target);
        if (byRequester == null || byRequester.isEmpty()) return null;

        long now = System.currentTimeMillis();
        UUID latest = null;
        long latestAt = -1L;
        TpaType latestType = TpaType.TO_TARGET;

        for (Map.Entry<UUID, TpaRequest> entry : byRequester.entrySet()) {
            TpaRequest request = entry.getValue();
            if (request == null) continue;
            if (request.isExpired(now, tpaRequestTimeoutMs)) {
                byRequester.remove(entry.getKey());
                continue;
            }
            if (request.createdAt > latestAt) {
                latestAt = request.createdAt;
                latest = entry.getKey();
                latestType = request.type;
            }
        }

        if (byRequester.isEmpty()) {
            tpaByTarget.remove(target, byRequester);
        }

        return (latest != null) ? new LatestRequest(latest, latestType) : null;
    }

    public boolean removeTpaRequest(@Nonnull UUID requester, @Nonnull UUID target) {
        ConcurrentHashMap<UUID, TpaRequest> byRequester = tpaByTarget.get(target);
        if (byRequester == null) return false;
        boolean removed = byRequester.remove(requester) != null;
        if (byRequester.isEmpty()) {
            tpaByTarget.remove(target, byRequester);
        }
        return removed;
    }

    public void removeAllTpaFor(@Nonnull UUID playerId) {
        tpaByTarget.remove(playerId);
        for (ConcurrentHashMap<UUID, TpaRequest> byRequester : tpaByTarget.values()) {
            byRequester.remove(playerId);
        }
        tpaByTarget.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public boolean isTpaIgnored(@Nonnull UUID playerId) {
        return tpaIgnore.containsKey(playerId);
    }

    public boolean toggleTpaIgnore(@Nonnull UUID playerId) {
        if (tpaIgnore.remove(playerId) != null) return false;
        tpaIgnore.put(playerId, Boolean.TRUE);
        return true;
    }

    private static final class PendingTeleport {
        final Vector3d startPos;
        final float delay;
        float elapsed;
        final TeleportAction execute;

        PendingTeleport(Vector3d startPos, float delay, TeleportAction execute) {
            this.startPos = startPos;
            this.delay = Math.max(0f, delay);
            this.execute = execute;
        }
    }

    private static final class TpaRequest {
        final long createdAt;
        final TpaType type;

        private TpaRequest(long createdAt, TpaType type) {
            this.createdAt = createdAt;
            this.type = type;
        }

        private boolean isExpired(long now, long timeoutMs) {
            if (timeoutMs <= 0L) return false;
            return now - createdAt > timeoutMs;
        }
    }
}

