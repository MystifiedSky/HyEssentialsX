package xyz.thelegacyvoyage.hyessentialsx.commands.tpa;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3f;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

public final class TpaAcceptCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.tpaaccept";
    private static final String BYPASS_PERMISSION = "hyessentialsx.tpa.bypass";

    private final TPManager tpManager;
    private final BackManager backManager;
    private final ConfigManager config;
    private final CommandCooldownManager cooldowns;

    public TpaAcceptCommand(@Nonnull TPManager tpManager,
                            @Nonnull BackManager backManager,
                            @Nonnull ConfigManager config,
                            @Nonnull CommandCooldownManager cooldowns) {
        super("tpaaccept", "Accept the latest teleport request");
        this.tpManager = tpManager;
        this.backManager = backManager;
        this.config = config;
        this.cooldowns = cooldowns;
        this.setPermissionGroups();
        this.addAliases(new String[]{"tpaccept"});
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/tpaaccept");
            return;
        }
        if (!config.isTpaEnabled()) {
            Messages.errKey(context, "tpa.disabled", Map.of());
            return;
        }

        TPManager.LatestRequest latest = tpManager.getLatestTpaRequester(playerRef.getUuid());
        if (latest == null) {
            Messages.errKey(context, "tpa.accept.none", Map.of());
            return;
        }
        UUID requesterId = latest.requester();

        PlayerRef requester = Universe.get().getPlayer(requesterId);
        if (requester == null) {
            tpManager.removeTpaRequest(requesterId, playerRef.getUuid());
            Messages.errKey(context, "tpa.accept.requester_offline", Map.of());
            return;
        }

        boolean isHere = latest.type() == TPManager.TpaType.HERE;

        Ref<EntityStore> requesterRef = requester.getReference();
        Store<EntityStore> requesterStore = requesterRef.getStore();
        Ref<EntityStore> targetRef = ref;
        Store<EntityStore> targetStore = store;

        Ref<EntityStore> teleportedRef = isHere ? targetRef : requesterRef;
        Store<EntityStore> teleportedStore = isHere ? targetStore : requesterStore;

        UUID teleportedId = isHere ? playerRef.getUuid() : requester.getUuid();
        PlayerRef teleportedPlayer = isHere ? playerRef : requester;
        int warmupSeconds = cooldowns.getEffectiveWarmupSeconds(context.sender(), teleportedPlayer, CooldownKeys.TPA, BYPASS_PERMISSION);
        BackSnapshot backSnapshot = captureBackSnapshot(teleportedPlayer, teleportedRef, teleportedStore);

        if (warmupSeconds > 0) {
            if (tpManager.hasPending(teleportedId)) {
                Messages.errKey(context, "teleport.pending", Map.of());
                return;
            }

            com.hypixel.hytale.math.vector.Transform t = teleportedPlayer.getTransform();
            if (t == null || t.getPosition() == null) {
                Messages.errKey(context, "teleport.position_unavailable", Map.of());
                return;
            }

            tpManager.queue(
                    teleportedId,
                    new org.joml.Vector3d(t.getPosition()),
                    warmupSeconds,
                    cooldowns.shouldCancelWarmupOnMove(CooldownKeys.TPA),
                    buffer -> {
                        String err = isHere
                                ? TeleportationUtil.teleportToPlayer(buffer, targetRef, requester)
                                : TeleportationUtil.teleportToPlayer(buffer, requesterRef, playerRef);
                        if (err != null) {
                            Messages.sendPrefixed(teleportedPlayer, err);
                            return;
                        }
                        if (backSnapshot != null) {
                            backManager.recordLocation(
                                    teleportedId,
                                    backSnapshot.worldName,
                                    backSnapshot.pos.x(), backSnapshot.pos.y(), backSnapshot.pos.z(),
                                    backSnapshot.yaw, backSnapshot.pitch
                            );
                        }
                    }
            );

            if (!teleportedId.equals(requester.getUuid())) {
                tpManager.cancel(requester.getUuid(), null);
            }
            tpManager.removeTpaRequest(requester.getUuid(), playerRef.getUuid());

            Messages.okKey(context, "tpa.accepted", Map.of("player", requester.getUsername()));
            if (isHere) {
                Messages.sendKey(requester, "tpa.accept.teleporting_to_you_warmup", Map.of(
                        "player", playerRef.getUsername(),
                        "seconds", String.valueOf(warmupSeconds)
                ));
            } else {
                Messages.sendKey(playerRef, "tpa.accept.teleporting_to_you_warmup", Map.of(
                        "player", requester.getUsername(),
                        "seconds", String.valueOf(warmupSeconds)
                ));
            }
            Messages.sendPrefixedKey(teleportedPlayer, "teleport.warmup", Map.of("seconds", String.valueOf(warmupSeconds)));
            return;
        }

        tpManager.cancel(requester.getUuid(), null);
        if (backSnapshot != null) {
            backManager.recordLocation(
                    teleportedId,
                    backSnapshot.worldName,
                    backSnapshot.pos.x(), backSnapshot.pos.y(), backSnapshot.pos.z(),
                    backSnapshot.yaw, backSnapshot.pitch
            );
        }
        String err = isHere
                ? TeleportationUtil.teleportToPlayer(targetStore, targetRef, requester)
                : TeleportationUtil.teleportToPlayer(requesterStore, requesterRef, playerRef);
        if (err != null) {
            Messages.err(context, err);
            return;
        }

        tpManager.removeTpaRequest(requester.getUuid(), playerRef.getUuid());

        Messages.okKey(context, "tpa.accepted", Map.of("player", requester.getUsername()));
        Messages.sendKey(requester, isHere
                ? "tpa.accept.teleporting_to_you"
                : "tpa.accept.teleporting_to_target",
                Map.of("player", playerRef.getUsername()));
    }

    @Nullable
    private BackSnapshot captureBackSnapshot(@Nonnull PlayerRef playerRef,
                                             @Nonnull Ref<EntityStore> ref,
                                             @Nonnull Store<EntityStore> store) {
        com.hypixel.hytale.math.vector.Transform transform = playerRef.getTransform();
        Vector3d pos = (transform != null) ? transform.getPosition() : null;
        com.hypixel.hytale.math.vector.Rotation3f rot = (transform != null) ? transform.getRotation() : null;
        if (pos == null) {
            TransformComponent component = store.getComponent(ref, TransformComponent.getComponentType());
            if (component != null) {
                pos = component.getPosition();
                rot = component.getRotation();
            }
        }
        if (pos == null) {
            return null;
        }
        float yaw = (rot != null) ? rot.y() : 0f;
        float pitch = (rot != null) ? rot.x() : 0f;
        World world = store.getExternalData().getWorld();
        if (world == null) {
            UUID worldId = playerRef.getWorldUuid();
            if (worldId != null) {
                world = Universe.get().getWorld(worldId);
            }
        }
        if (world == null) {
            return null;
        }
        return new BackSnapshot(world.getName(), pos, yaw, pitch);
    }

    private record BackSnapshot(@Nonnull String worldName,
                                @Nonnull Vector3d pos,
                                float yaw,
                                float pitch) {
    }
}




