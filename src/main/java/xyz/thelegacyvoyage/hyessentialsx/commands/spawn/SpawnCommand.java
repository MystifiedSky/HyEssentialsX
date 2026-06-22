package xyz.thelegacyvoyage.hyessentialsx.commands.spawn;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.SpawnManager;
import xyz.thelegacyvoyage.hyessentialsx.models.SpawnModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;

public final class SpawnCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.spawn";
    private final SpawnManager spawnManager;
    private final BackManager backManager;
    private final TPManager tpManager;
    private final ConfigManager configManager;

    public SpawnCommand(@Nonnull SpawnManager spawnManager,
                        @Nonnull BackManager backManager,
                        @Nonnull TPManager tpManager,
                        @Nonnull ConfigManager configManager) {
        super("spawn", "Teleport to the server spawn");
        this.spawnManager = spawnManager;
        this.backManager = backManager;
        this.tpManager = tpManager;
        this.configManager = configManager;
        this.setPermissionGroup(null);
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
            Messages.noPerm(context, "/spawn");
            return;
        }
        if (!configManager.isSpawnEnabled()) {
            Messages.err(context, "Spawn is disabled.");
            return;
        }

        SpawnModel spawn = spawnManager.getSpawn();
        if (spawn == null && configManager.isUseWorldDefaultSpawnIfUnset()) {
            spawn = spawnManager.getSpawnOrWorldDefault(world, playerRef.getUuid());
        }
        if (spawn == null) {
            Messages.err(context, "Spawn is not set yet. Use /setspawn first.");
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform != null) {
            Vector3d pos = transform.getPosition();
            Vector3f rot = transform.getRotation();
            float yaw = (rot != null) ? rot.getY() : 0f;
            float pitch = (rot != null) ? rot.getX() : 0f;
            backManager.recordLocation(
                    playerRef.getUuid(),
                    world.getName(),
                    pos.getX(), pos.getY(), pos.getZ(),
                    yaw, pitch
            );
        }

        tpManager.cancel(playerRef.getUuid(), null);
        String err = TeleportationUtil.teleportToSpawn(store, ref, spawn);
        if (err != null) {
            Messages.err(context, err);
            return;
        }

        Messages.ok(context, "Teleported to spawn.");
    }
}



