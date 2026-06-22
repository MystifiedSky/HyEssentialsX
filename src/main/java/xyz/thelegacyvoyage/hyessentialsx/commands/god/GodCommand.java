package xyz.thelegacyvoyage.hyessentialsx.commands.god;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.GodManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class GodCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.god";

    private final GodManager godManager;

    public GodCommand(@Nonnull GodManager godManager) {
        super("god", "Toggle invulnerability");
        this.godManager = godManager;
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
            Messages.noPerm(context, "/god");
            return;
        }

        boolean nowEnabled = godManager.toggle(playerRef.getUuid());

        if (nowEnabled) {
            Messages.ok(context, "God mode enabled.");
        } else {
            Messages.ok(context, "God mode disabled.");
        }
    }
}



