package xyz.thelegacyvoyage.hyessentialsx.commands.cheat;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.FlyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class FlyCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.fly";
    private static final String OTHERS_PERMISSION = "hyessentialsx.fly.others";

    private final FlyManager flyManager;
    private final StorageManager storage;
    private final OptionalArg<PlayerRef> targetArg;

    public FlyCommand(@Nonnull FlyManager flyManager, @Nonnull StorageManager storage) {
        super("fly", "Toggle flight");
        this.flyManager = flyManager;
        this.storage = storage;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.targetArg = withOptionalArg("player", "Target player", ArgTypes.PLAYER_REF);
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
            Messages.noPerm(context, "/fly");
            return;
        }

        PlayerRef target = context.provided(targetArg) ? context.get(targetArg) : playerRef;
        if (!playerRef.getUuid().equals(target.getUuid())
                && !xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), OTHERS_PERMISSION)) {
            Messages.noPerm(context, "/fly");
            return;
        }

        if (!playerRef.getUuid().equals(target.getUuid())
                && playerRef.getWorldUuid() != null
                && target.getWorldUuid() != null
                && !playerRef.getWorldUuid().equals(target.getWorldUuid())) {
            Messages.errKey(context, "error.target_world", java.util.Map.of());
            return;
        }

        boolean enabled = flyManager.toggle(target.getUuid());
        if (!flyManager.applyState(target, enabled)) {
            flyManager.queueApply(target.getUuid(), enabled);
        }
        var data = storage.getPlayerData(target.getUuid());
        data.setFlyEnabled(enabled);
        storage.savePlayerDataAsync(target.getUuid(), data);

        boolean isSelf = playerRef.getUuid().equals(target.getUuid());
        if (isSelf) {
            Messages.okKey(context, enabled ? "fly.enabled" : "fly.disabled", java.util.Map.of());
        } else {
            Messages.okKey(context,
                    enabled ? "fly.enabled_for" : "fly.disabled_for",
                    java.util.Map.of("player", target.getUsername()));
            Messages.sendPrefixedKey(target,
                    enabled ? "fly.enabled_by" : "fly.disabled_by",
                    java.util.Map.of("player", playerRef.getUsername()));
        }
    }
}




