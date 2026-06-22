package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EconomyRewardListener {

    private static final long DAMAGE_TRACK_TIMEOUT_MS = 10_000L;

    private final EconomyManager economy;
    private final ConfigManager config;
    private final ConcurrentHashMap<Ref<EntityStore>, DamageRecord> lastDamage = new ConcurrentHashMap<>();

    public EconomyRewardListener(@Nonnull EconomyManager economy, @Nonnull ConfigManager config) {
        this.economy = economy;
        this.config = config;
    }

    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new BlockRewardSystem(economy, config));
        registry.registerSystem(new MobDamageTrackSystem(economy, config, lastDamage));
        registry.registerSystem(new MobDeathRewardSystem(economy, config, lastDamage));
    }

    private static final class BlockRewardSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        private final EconomyManager economy;
        private final ConfigManager config;

        private BlockRewardSystem(@Nonnull EconomyManager economy, @Nonnull ConfigManager config) {
            super(BreakBlockEvent.class);
            this.economy = economy;
            this.config = config;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public java.util.Set<com.hypixel.hytale.component.dependency.Dependency<EntityStore>> getDependencies() {
            return Collections.singleton(RootDependency.first());
        }

        @Override
        public void handle(int index,
                           @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer,
                           @Nonnull BreakBlockEvent event) {
            if (!economy.isEnabled()) return;
            if (!config.isEconomyRewardsEnabled() || !config.isEconomyBlockRewardsEnabled()) return;
            if (event.isCancelled()) return;
            BlockType blockType = event.getBlockType();
            if (blockType == null) return;
            Vector3i pos = event.getTargetBlock();
            if (pos == null) return;

            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null) return;

            String blockId = blockType.getId();
            String group = blockType.getGroup();
            long reward = resolveBlockReward(blockId, group, config.getEconomyBlockRewards(), config.getEconomyBlockGroupRewards());
            if (reward <= 0L) {
                if (config.isEconomyRewardsDebug()) {
                    Log.info("[HyEssentialsX] No block reward match: id=" + blockId + " group=" + group);
                }
                return;
            }
            economy.deposit(player.getUuid(), reward);
            sendPopup(player, reward, config, economy);
            if (config.isEconomyRewardsDebug()) {
                Log.info("[HyEssentialsX] Block reward: id=" + blockId + " group=" + group + " reward=" + reward);
            }
        }
    }

    private static final class MobDamageTrackSystem extends DamageEventSystem {
        private final EconomyManager economy;
        private final ConfigManager config;
        private final ConcurrentHashMap<Ref<EntityStore>, DamageRecord> lastDamage;

        private MobDamageTrackSystem(@Nonnull EconomyManager economy,
                                     @Nonnull ConfigManager config,
                                     @Nonnull ConcurrentHashMap<Ref<EntityStore>, DamageRecord> lastDamage) {
            this.economy = economy;
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
            if (!economy.isEnabled()) return;
            if (!config.isEconomyRewardsEnabled() || !config.isEconomyMobRewardsEnabled()) return;
            if (event.isCancelled() || event.getAmount() <= 0f) return;

            Ref<EntityStore> victimRef = chunk.getReferenceTo(index);
            if (victimRef == null) return;

            Ref<EntityStore> attackerRef = resolveAttackerRef(event);
            if (attackerRef == null) return;

            PlayerRef attacker = store.getComponent(attackerRef, PlayerRef.getComponentType());
            if (attacker == null) return;

            lastDamage.put(victimRef, new DamageRecord(attacker.getUuid(), System.currentTimeMillis()));
        }
    }

    private static final class MobDeathRewardSystem extends RefChangeSystem<EntityStore, DeathComponent> {
        private final EconomyManager economy;
        private final ConfigManager config;
        private final ConcurrentHashMap<Ref<EntityStore>, DamageRecord> lastDamage;

        private MobDeathRewardSystem(@Nonnull EconomyManager economy,
                                     @Nonnull ConfigManager config,
                                     @Nonnull ConcurrentHashMap<Ref<EntityStore>, DamageRecord> lastDamage) {
            this.economy = economy;
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
            if (!economy.isEnabled()) return;
            if (!config.isEconomyRewardsEnabled() || !config.isEconomyMobRewardsEnabled()) return;
            if (store.getComponent(ref, PlayerRef.getComponentType()) != null) return;

            DamageRecord record = lastDamage.remove(ref);
            if (record == null || record.isExpired()) return;

            UUID attackerId = record.attackerId();
            if (attackerId == null) return;

            long reward = resolveMobReward(ref, store, config.getEconomyMobRewards(), config.getEconomyMobDefaultReward());
            if (reward <= 0L) return;

            economy.deposit(attackerId, reward);
            PlayerRef attacker = Universe.get().getPlayer(attackerId);
            if (attacker != null) {
                sendPopup(attacker, reward, config, economy);
            }
        }

        @Override
        public com.hypixel.hytale.component.ComponentType<EntityStore, DeathComponent> componentType() {
            return DeathComponent.getComponentType();
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
    }

    private static Ref<EntityStore> resolveAttackerRef(@Nonnull Damage event) {
        Damage.Source source = event.getSource();
        if (source instanceof Damage.EntitySource entitySource) {
            return entitySource.getRef();
        }
        return null;
    }

    private static long resolveBlockReward(String blockId,
                                           String group,
                                           @Nonnull Map<String, Long> blockRewards,
                                           @Nonnull Map<String, Long> groupRewards) {
        if (blockId == null) blockId = "";
        if (group == null) group = "";
        String normalizedId = normalizeKey(blockId);
        Long reward = blockRewards.get(normalizedId);
        if (reward == null && normalizedId.contains(":")) {
            reward = blockRewards.get(normalizedId.substring(normalizedId.indexOf(':') + 1));
        }
        if (reward != null) return reward;
        String normalizedGroup = normalizeKey(group);
        Long groupReward = groupRewards.get(normalizedGroup);
        if (groupReward == null && normalizedGroup.contains(":")) {
            groupReward = groupRewards.get(normalizedGroup.substring(normalizedGroup.indexOf(':') + 1));
        }
        if (groupReward != null) return groupReward;

        if (looksLikeOre(normalizedId) || looksLikeOre(normalizedGroup)) {
            Long oreReward = groupRewards.get("ore");
            if (oreReward != null) return oreReward;
        }

        if (looksLikeWood(normalizedId) || looksLikeWood(normalizedGroup)) {
            Long woodReward = groupRewards.get("wood");
            if (woodReward != null) return woodReward;
        }

        return 0L;
    }

    private static boolean looksLikeOre(@Nonnull String value) {
        return value.contains("ore") || value.contains("mineral");
    }

    private static boolean looksLikeWood(@Nonnull String value) {
        if (value.contains("ore")) return false;
        return value.contains("log")
                || value.contains("stem")
                || value.contains("wood")
                || value.contains("tree")
                || value.contains("trunk");
    }

    private static long resolveMobReward(@Nonnull Ref<EntityStore> ref,
                                         @Nonnull Store<EntityStore> store,
                                         @Nonnull Map<String, Long> mobRewards,
                                         long defaultReward) {
        ModelComponent modelComponent = store.getComponent(ref, ModelComponent.getComponentType());
        if (modelComponent == null || modelComponent.getModel() == null) {
            return defaultReward;
        }
        String modelId = modelComponent.getModel().getModelAssetId();
        if (modelId == null || modelId.isBlank()) {
            modelId = modelComponent.getModel().getModel();
        }
        if (modelId == null || modelId.isBlank()) {
            return defaultReward;
        }

        String normalized = normalizeKey(modelId);
        Long reward = mobRewards.get(normalized);
        if (reward == null && normalized.contains(":")) {
            reward = mobRewards.get(normalized.substring(normalized.indexOf(':') + 1));
        }
        if (reward == null && normalized.contains("/")) {
            reward = mobRewards.get(normalized.substring(normalized.lastIndexOf('/') + 1));
        }
        return reward != null ? reward : defaultReward;
    }

    @Nonnull
    private static String normalizeKey(@Nonnull String input) {
        return input.trim().toLowerCase();
    }

    private record DamageRecord(UUID attackerId, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > DAMAGE_TRACK_TIMEOUT_MS;
        }
    }

    private static void sendPopup(@Nonnull PlayerRef player,
                                  long amount,
                                  @Nonnull ConfigManager config,
                                  @Nonnull EconomyManager economy) {
        if (!config.isEconomyRewardsPopupEnabled()) return;
        String text = Messages.tr(player, "economy.reward.popup",
                Map.of("amount", economy.formatAmount(amount)));
        NotificationStyle style = resolveStyle(config.getEconomyRewardsPopupStyle());
        NotificationUtil.sendNotification(player.getPacketHandler(), Messages.m(text), style);
    }

    @Nonnull
    private static NotificationStyle resolveStyle(@Nonnull String raw) {
        if (raw == null) return NotificationStyle.Success;
        String key = raw.trim().toLowerCase();
        return switch (key) {
            case "default" -> NotificationStyle.Default;
            case "warning" -> NotificationStyle.Warning;
            case "danger", "error" -> NotificationStyle.Danger;
            case "success" -> NotificationStyle.Success;
            default -> NotificationStyle.Success;
        };
    }
}

