package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.PlaceholderApiUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DeathMessageListener {

    private static final long DAMAGE_TRACK_TIMEOUT_MS = 10_000L;

    private final ConfigManager config;
    private final ConcurrentHashMap<Ref<EntityStore>, DamageInfo> lastDamage = new ConcurrentHashMap<>();

    public DeathMessageListener(@Nonnull ConfigManager config) {
        this.config = config;
    }

    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new PlayerDamageTrackSystem(config, lastDamage));
        registry.registerSystem(new PlayerDeathMessageSystem(config, lastDamage));
    }

    private static final class PlayerDamageTrackSystem extends DamageEventSystem {

        private final ConfigManager config;
        private final ConcurrentHashMap<Ref<EntityStore>, DamageInfo> lastDamage;

        private PlayerDamageTrackSystem(@Nonnull ConfigManager config,
                                        @Nonnull ConcurrentHashMap<Ref<EntityStore>, DamageInfo> lastDamage) {
            this.config = config;
            this.lastDamage = lastDamage;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }

        @Override
        public SystemGroup<EntityStore> getGroup() {
            return DamageModule.get().getInspectDamageGroup();
        }

        @Override
        public void handle(int index,
                           @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer,
                           @Nonnull Damage event) {
            if (!config.isDeathMessagesEnabled()) return;
            if (event.isCancelled() || event.getAmount() <= 0f) return;

            Ref<EntityStore> victimRef = chunk.getReferenceTo(index);
            PlayerRef victim = store.getComponent(victimRef, PlayerRef.getComponentType());
            if (victim == null) return;

            Damage.Source source = event.getSource();
            UUID killerId = null;
            String cause = null;

            if (source instanceof Damage.EntitySource entitySource) {
                Ref<EntityStore> attackerRef = entitySource.getRef();
                if (attackerRef != null) {
                    PlayerRef attacker = store.getComponent(attackerRef, PlayerRef.getComponentType());
                    if (attacker != null) {
                        killerId = attacker.getUuid();
                    } else {
                        cause = resolveEntityName(attackerRef, store);
                    }
                }
            }

            if (cause == null) {
                cause = resolveDamageCause(event);
            }

            lastDamage.put(victimRef, new DamageInfo(killerId, cause, System.currentTimeMillis()));
        }

        @Nullable
        private static String resolveEntityName(@Nonnull Ref<EntityStore> attackerRef,
                                                @Nonnull Store<EntityStore> store) {
            ModelComponent model = store.getComponent(attackerRef, ModelComponent.getComponentType());
            if (model == null || model.getModel() == null) return null;
            String id = model.getModel().getModelAssetId();
            if (id == null || id.isBlank()) {
                id = model.getModel().getModel();
            }
            if (id == null || id.isBlank()) return null;
            return prettifyId(id);
        }
    }

    private static final class PlayerDeathMessageSystem extends RefChangeSystem<EntityStore, DeathComponent> {

        private final ConfigManager config;
        private final ConcurrentHashMap<Ref<EntityStore>, DamageInfo> lastDamage;

        private PlayerDeathMessageSystem(@Nonnull ConfigManager config,
                                         @Nonnull ConcurrentHashMap<Ref<EntityStore>, DamageInfo> lastDamage) {
            this.config = config;
            this.lastDamage = lastDamage;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }

        @Override
        public void onComponentAdded(@Nonnull Ref<EntityStore> ref,
                                     @Nonnull DeathComponent component,
                                     @Nonnull Store<EntityStore> store,
                                     @Nonnull CommandBuffer<EntityStore> buffer) {
            if (!config.isDeathMessagesEnabled()) return;

            PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null) return;

            DamageInfo info = lastDamage.remove(ref);
            if (info != null && info.isExpired()) {
                info = null;
            }

            String playerName = player.getUsername();
            String killerName = resolveKillerName(info);
            String cause = resolveCause(info, killerName);

            Map<String, String> vars = Map.of(
                    "player", playerName != null ? playerName : "Unknown",
                    "killer", killerName,
                    "cause", cause
            );

            List<String> lines = config.getDeathMessages();
            if (lines.isEmpty()) return;

            for (PlayerRef target : Universe.get().getPlayers()) {
                for (String line : lines) {
                    String msg = line.replace("{player}", vars.get("player"))
                            .replace("{killer}", vars.get("killer"))
                            .replace("{cause}", vars.get("cause"));
                    msg = normalizeSpacing(msg);
                    target.sendMessage(PlaceholderApiUtil.apply(target, msg, config));
                }
            }
        }

        @Override
        public void onComponentSet(@Nonnull Ref<EntityStore> ref,
                                   DeathComponent oldC,
                                   @Nonnull DeathComponent newC,
                                   @Nonnull Store<EntityStore> store,
                                   @Nonnull CommandBuffer<EntityStore> buffer) { }

        @Override
        public void onComponentRemoved(@Nonnull Ref<EntityStore> ref,
                                       @Nonnull DeathComponent component,
                                       @Nonnull Store<EntityStore> store,
                                       @Nonnull CommandBuffer<EntityStore> buffer) { }

        @Override
        public com.hypixel.hytale.component.ComponentType<EntityStore, DeathComponent> componentType() {
            return DeathComponent.getComponentType();
        }

        @Nonnull
        private String resolveKillerName(@Nullable DamageInfo info) {
            if (info == null || info.killerId() == null) return "";
            PlayerRef killer = Universe.get().getPlayer(info.killerId());
            return killer != null && killer.getUsername() != null ? killer.getUsername() : "Unknown";
        }

        @Nonnull
        private String resolveCause(@Nullable DamageInfo info, @Nonnull String killerName) {
            if (!killerName.isBlank()) {
                return "by " + killerName;
            }
            if (info == null) return "";
            String cause = info.cause();
            if (cause == null || cause.isBlank()) return "";
            return "from " + cause;
        }
    }

    @Nullable
    private static String resolveDamageCause(@Nonnull Damage event) {
        Object value = tryInvoke(event, "getDamageCause");
        if (value == null) value = tryInvoke(event, "getCause");
        if (value == null) value = tryInvoke(event, "getDamageType");
        if (value == null) value = tryInvoke(event, "getType");
        if (value == null) value = tryInvoke(event, "getSource");
        if (value == null) value = tryInvoke(event, "getReason");
        if (value == null) return null;

        String name = extractCauseName(value);
        if (name == null || name.isBlank()) return null;
        return prettifyId(name);
    }

    @Nullable
    private static String extractCauseName(@Nonnull Object value) {
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        String raw = value.toString();
        if (raw == null) return null;
        // If toString looks like a Java object ref, use simple class name instead.
        if (raw.contains("@")) {
            String simple = value.getClass().getSimpleName();
            return simple.isBlank() ? raw : simple;
        }
        // Strip package noise if present.
        int lastDot = raw.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < raw.length() - 1) {
            return raw.substring(lastDot + 1);
        }
        return raw;
    }

    private static Object tryInvoke(@Nonnull Object target, @Nonnull String method) {
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String prettifyId(@Nonnull String raw) {
        String cleaned = raw.replace("DamageCause.", "")
                .replace("DamageType.", "")
                .replace("DamageSource.", "")
                .replace("Damagecause", "DamageCause")
                .replace('_', ' ')
                .replace('-', ' ')
                .trim();
        if (cleaned.isBlank()) return raw;
        if ("DamageCause".equalsIgnoreCase(cleaned)) {
            return "Unknown";
        }
        String lowerCleaned = cleaned.toLowerCase();
        if (lowerCleaned.contains("fall")) {
            return "Fall damage";
        }
        String lower = cleaned.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String normalizeSpacing(@Nonnull String msg) {
        return msg.replace("  ", " ").replace(" .", ".").replace(" ,", ",").trim();
    }

    private record DamageInfo(UUID killerId, String cause, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > DAMAGE_TRACK_TIMEOUT_MS;
        }
    }
}

