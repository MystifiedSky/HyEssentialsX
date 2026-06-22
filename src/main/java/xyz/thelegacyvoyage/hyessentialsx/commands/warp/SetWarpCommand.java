package xyz.thelegacyvoyage.hyessentialsx.commands.warp;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.WarpManager;
import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class SetWarpCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.setwarp";

    private final WarpManager warpManager;
    private final ConfigManager config;
    private final RequiredArg<String> nameArg;

    public SetWarpCommand(@Nonnull WarpManager warpManager, @Nonnull ConfigManager config) {
        super("setwarp", "Creates a warp point");
        this.warpManager = warpManager;
        this.config = config;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.nameArg = withRequiredArg("name", "Warp name", ArgTypes.STRING);
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
            Messages.noPerm(context, "/setwarp");
            return;
        }
        if (!config.isWarpsEnabled()) {
            Messages.err(context, "Warps are disabled.");
            return;
        }

        String name = context.get(nameArg);
        if (name == null || name.isBlank()) {
            Messages.err(context, "Warp name required.");
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            Messages.err(context, "Could not read your position.");
            return;
        }

        Vector3d pos = transform.getPosition();
        Vector3f rot = transform.getRotation();
        float yaw = (rot != null) ? rot.getY() : 0f;
        float pitch = (rot != null) ? rot.getX() : 0f;

        warpManager.setWarp(new WarpModel(
                name,
                world.getName(),
                pos.getX(), pos.getY(), pos.getZ(),
                yaw, pitch
        ));

        Messages.ok(context, "Warp '&f" + name + "&a' set.");
    }
}




