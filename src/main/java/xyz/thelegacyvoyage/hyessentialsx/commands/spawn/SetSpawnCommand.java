package xyz.thelegacyvoyage.hyessentialsx.commands.spawn;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;
import org.joml.Vector3f;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.SpawnManager;
import xyz.thelegacyvoyage.hyessentialsx.models.SpawnModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class SetSpawnCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.setspawn";
    private final SpawnManager spawnManager;
    private final ConfigManager config;

    public SetSpawnCommand(@Nonnull SpawnManager spawnManager, @Nonnull ConfigManager config) {
        super("setspawn", "Set the server spawn (or a named spawn) to your current location");
        this.spawnManager = spawnManager;
        this.config = config;
        this.setPermissionGroups();
        this.setAllowsExtraArguments(true);
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
            Messages.noPerm(context, "/setspawn");
            return;
        }
        if (!config.isSpawnEnabled()) {
            Messages.errKey(context, "spawn.disabled", java.util.Map.of());
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            Messages.errKey(context, "error.position_unavailable", java.util.Map.of());
            return;
        }

        Vector3d pos = transform.getPosition();
        com.hypixel.hytale.math.vector.Rotation3f rot = transform.getRotation(); // axis order depends on SDK
        float yaw = (rot != null) ? rot.y() : 0f;
        float pitch = (rot != null) ? rot.x() : 0f;
        SpawnModel spawn = new SpawnModel(
                world.getName(),
                pos.x(), pos.y(), pos.z(),
                yaw, pitch
        );

        java.util.List<String> args = CommandInputUtil.getArgs(context);
        if (!args.isEmpty()) {
            String rawName = args.get(0);
            String spawnName = SpawnManager.normalizeSpawnName(rawName);
            if (spawnName == null) {
                Messages.errKey(context, "spawn.named.invalid_name", java.util.Map.of());
                return;
            }
            spawnManager.setNamedSpawn(spawnName, spawn);
            Messages.okKey(context, "spawn.named.set", java.util.Map.of("name", spawnName));
            return;
        }

        spawnManager.setSpawn(spawn);
        spawnManager.syncWorldSpawnProvider();
        Messages.okKey(context, "spawn.set", java.util.Map.of());
    }

}




