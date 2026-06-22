package xyz.thelegacyvoyage.hyessentialsx.commands.warp;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.WarpManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class DelWarpCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.delwarp";

    private final WarpManager warpManager;
    private final ConfigManager config;
    private final RequiredArg<String> nameArg;

    public DelWarpCommand(@Nonnull WarpManager warpManager, @Nonnull ConfigManager config) {
        super("delwarp", "Deletes a warp");
        this.warpManager = warpManager;
        this.config = config;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"remwarp", "rmwarp"});
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
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/delwarp");
            return;
        }
        if (!config.isWarpsEnabled()) {
            Messages.errKey(context, "warp.disabled", java.util.Map.of());
            return;
        }

        String name = context.get(nameArg);
        boolean removed = warpManager.deleteWarp(name);
        if (!removed) {
            Messages.errKey(context, "warp.not_found", java.util.Map.of());
            return;
        }

        Messages.okKey(context, "warp.deleted", java.util.Map.of("warp", name));
    }
}




