package xyz.thelegacyvoyage.hyessentialsx.commands.spawn;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.SpawnManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class DelSpawnCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.delspawn";
    private final SpawnManager spawnManager;
    private final ConfigManager config;

    public DelSpawnCommand(@Nonnull SpawnManager spawnManager, @Nonnull ConfigManager config) {
        super("delspawn", "Delete the custom spawn and revert to world default");
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
            Messages.noPerm(context, "/delspawn");
            return;
        }
        if (!config.isSpawnEnabled()) {
            Messages.err(context, "Spawn is disabled.");
            return;
        }

        if (!spawnManager.hasSpawn()) {
            Messages.warn(context, "No custom spawn is set.");
            return;
        }

        spawnManager.clearSpawn();
        spawnManager.syncWorldSpawnProvider();
        Messages.ok(context, "Custom spawn deleted. Using world default spawn.");
    }
}




