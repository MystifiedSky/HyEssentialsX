package xyz.thelegacyvoyage.hyessentialsx.commands.spawn;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
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
        super("delspawn", "Delete the custom spawn (or a named spawn) and revert to world default");
        this.spawnManager = spawnManager;
        this.config = config;
        this.setPermissionGroups();
        this.addUsageVariant(new DelNamedSpawnCommand());
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
            Messages.noPerm(context, "/delspawn");
            return;
        }
        if (!config.isSpawnEnabled()) {
            Messages.errKey(context, "spawn.disabled", java.util.Map.of());
            return;
        }

        if (!spawnManager.hasSpawn()) {
            Messages.warnKey(context, "spawn.not_set", java.util.Map.of());
            return;
        }

        spawnManager.clearSpawn();
        spawnManager.syncWorldSpawnProvider();
        Messages.okKey(context, "spawn.deleted", java.util.Map.of());
    }

    private void deleteNamedSpawn(@Nonnull CommandContext context, @Nonnull String rawName) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/delspawn <name>");
            return;
        }
        if (!config.isSpawnEnabled()) {
            Messages.errKey(context, "spawn.disabled", java.util.Map.of());
            return;
        }

        String spawnName = SpawnManager.normalizeSpawnName(rawName);
        if (spawnName == null) {
            Messages.errKey(context, "spawn.named.invalid_name", java.util.Map.of());
            return;
        }
        if (!spawnManager.clearNamedSpawn(spawnName)) {
            Messages.warnKey(context, "spawn.named.not_set", java.util.Map.of("name", spawnName));
            return;
        }
        Messages.okKey(context, "spawn.named.deleted", java.util.Map.of("name", spawnName));
    }

    private final class DelNamedSpawnCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;

        private DelNamedSpawnCommand() {
            super("Delete a named spawn");
            this.nameArg = withRequiredArg("name", "Spawn name", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            deleteNamedSpawn(context, context.get(nameArg));
        }
    }
}




