package xyz.thelegacyvoyage.hyessentialsx.commands.warp;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.WarpManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;

public final class WarpsCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.warps";

    private final WarpManager warpManager;
    private final ConfigManager config;

    public WarpsCommand(@Nonnull WarpManager warpManager, @Nonnull ConfigManager config) {
        super("warps", "Lists all warps");
        this.warpManager = warpManager;
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
            Messages.noPerm(context, "/warps");
            return;
        }
        if (!config.isWarpsEnabled()) {
            Messages.err(context, "Warps are disabled.");
            return;
        }

        List<String> warps = warpManager.listWarps();
        if (warps.isEmpty()) {
            Messages.warn(context, "No warps set.");
            return;
        }

        Messages.send(context, "&aWarps: &f" + String.join(", ", warps));
    }
}



