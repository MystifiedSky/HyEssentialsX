package xyz.thelegacyvoyage.hyessentialsx.commands.spawn;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.SpawnManager;
import xyz.thelegacyvoyage.hyessentialsx.models.SpawnModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class SetSpawnCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.setspawn";
    private final SpawnManager spawnManager;
    private final ConfigManager config;

    public SetSpawnCommand(@Nonnull SpawnManager spawnManager, @Nonnull ConfigManager config) {
        super("setspawn", "Set the server spawn to your current location");
        this.spawnManager = spawnManager;
        this.config = config;
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
            Messages.noPerm(context, "/setspawn");
            return;
        }
        if (!config.isSpawnEnabled()) {
            Messages.err(context, "Spawn is disabled.");
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            Messages.err(context, "Could not read your position.");
            return;
        }

        Vector3d pos = transform.getPosition();
        Vector3f rot = transform.getRotation(); // axis order depends on SDK
        float yaw = (rot != null) ? rot.getY() : 0f;
        float pitch = (rot != null) ? rot.getX() : 0f;

        spawnManager.setSpawn(new SpawnModel(
                world.getName(),
                pos.getX(), pos.getY(), pos.getZ(),
                yaw, pitch
        ));

        spawnManager.syncWorldSpawnProvider();
        Messages.ok(context, "Spawn set.");
    }

}



