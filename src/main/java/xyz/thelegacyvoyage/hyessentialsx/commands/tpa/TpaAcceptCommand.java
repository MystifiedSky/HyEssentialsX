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
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;

public final class TpaAcceptCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.tpaaccept";

    private final TPManager tpManager;
    private final BackManager backManager;
    private final ConfigManager config;

    public TpaAcceptCommand(@Nonnull TPManager tpManager,
                            @Nonnull BackManager backManager,
                            @Nonnull ConfigManager config) {
        super("tpaaccept", "Accept the latest teleport request");
        this.tpManager = tpManager;
        this.backManager = backManager;
        this.config = config;
        this.setPermissionGroup(null);
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
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
            Messages.noPerm(context, "/tpaaccept");
            return;
        }
        if (!config.isTpaEnabled()) {
            Messages.err(context, "TPA is disabled.");
            return;
        }

        TPManager.LatestRequest latest = tpManager.getLatestTpaRequester(playerRef.getUuid());
        if (latest == null) {
            Messages.err(context, "No pending teleport requests.");
            return;
        }
        UUID requesterId = latest.requester();

        PlayerRef requester = Universe.get().getPlayer(requesterId);
        if (requester == null) {
            tpManager.removeTpaRequest(requesterId, playerRef.getUuid());
            Messages.err(context, "Requester is no longer online.");
            return;
        }

        boolean isHere = latest.type() == TPManager.TpaType.HERE;

        Ref<EntityStore> requesterRef = requester.getReference();
        Store<EntityStore> requesterStore = requesterRef.getStore();
        Ref<EntityStore> targetRef = ref;
        Store<EntityStore> targetStore = store;

        Ref<EntityStore> teleportedRef = isHere ? targetRef : requesterRef;
        Store<EntityStore> teleportedStore = isHere ? targetStore : requesterStore;

        TransformComponent transform = teleportedStore.getComponent(teleportedRef, TransformComponent.getComponentType());
        if (transform != null) {
            Vector3d pos = transform.getPosition();
            Vector3f rot = transform.getRotation();
            float yaw = (rot != null) ? rot.getY() : 0f;
            float pitch = (rot != null) ? rot.getX() : 0f;
            World teleportWorld = teleportedStore.getExternalData().getWorld();
            if (teleportWorld != null) {
                backManager.recordLocation(
                        teleportedRef == requesterRef ? requester.getUuid() : playerRef.getUuid(),
                        teleportWorld.getName(),
                        pos.getX(), pos.getY(), pos.getZ(),
                        yaw, pitch
                );
            }
        }

        int warmupSeconds = config.getTpaWarmupSeconds();
        UUID teleportedId = isHere ? playerRef.getUuid() : requester.getUuid();
        PlayerRef teleportedPlayer = isHere ? playerRef : requester;

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
                    t.getPosition().clone(),
                    warmupSeconds,
                    buffer -> {
                        String err = isHere
                                ? TeleportationUtil.teleportToPlayer(buffer, targetRef, requester)
                                : TeleportationUtil.teleportToPlayer(buffer, requesterRef, playerRef);
                        if (err != null) {
                            Messages.sendPrefixed(teleportedPlayer, err);
                        }
                    }
            );

            if (!teleportedId.equals(requester.getUuid())) {
                tpManager.cancel(requester.getUuid(), null);
            }
            tpManager.removeTpaRequest(requester.getUuid(), playerRef.getUuid());

            Messages.ok(context, "Accepted request from " + requester.getUsername() + ".");
            if (isHere) {
                Messages.send(requester, "&#55FF55Teleporting " + playerRef.getUsername() + " to you in " + warmupSeconds + "s...");
            } else {
                Messages.send(playerRef, "&#55FF55Teleporting " + requester.getUsername() + " to you in " + warmupSeconds + "s...");
            }
            Messages.sendPrefixedKey(teleportedPlayer, "teleport.warmup", Map.of("seconds", String.valueOf(warmupSeconds)));
            return;
        }

        tpManager.cancel(requester.getUuid(), null);
        String err = isHere
                ? TeleportationUtil.teleportToPlayer(targetStore, targetRef, requester)
                : TeleportationUtil.teleportToPlayer(requesterStore, requesterRef, playerRef);
        if (err != null) {
            Messages.err(context, err);
            return;
        }

        tpManager.removeTpaRequest(requester.getUuid(), playerRef.getUuid());

        Messages.ok(context, "Accepted request from " + requester.getUsername() + ".");
        Messages.send(requester, isHere
                ? "&#55FF55Teleporting " + playerRef.getUsername() + " to you..."
                : "&#55FF55Teleporting to " + playerRef.getUsername() + "...");
    }
}



