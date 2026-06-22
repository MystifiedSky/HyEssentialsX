package xyz.thelegacyvoyage.hyessentialsx.commands.kit;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.KitManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class KitDeleteCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.kitdelete";

    private final KitManager kitManager;
    private final ConfigManager config;
    private final RequiredArg<String> nameArg;

    public KitDeleteCommand(@Nonnull KitManager kitManager, @Nonnull ConfigManager config) {
        super("kitdelete", "Deletes a kit");
        this.kitManager = kitManager;
        this.config = config;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.nameArg = withRequiredArg("name", "Kit name", ArgTypes.STRING);
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
            Messages.noPerm(context, "/kitdelete");
            return;
        }
        if (!config.isKitsEnabled()) {
            Messages.err(context, "Kits are disabled.");
            return;
        }

        String name = context.get(nameArg);
        boolean removed = kitManager.deleteKit(name);
        if (!removed) {
            Messages.err(context, "Kit not found.");
            return;
        }

        Messages.ok(context, "Kit '&f" + name + "&a' deleted.");
    }
}




