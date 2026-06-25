package xyz.thelegacyvoyage.hyessentialsx.commands.cheat;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.GodManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class GodCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.god";
    private static final String OTHERS_PERMISSION = "hyessentialsx.god.others";

    private final GodManager godManager;

    public GodCommand(@Nonnull GodManager godManager) {
        super("god", "Toggle invulnerability");
        this.godManager = godManager;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addUsageVariant(new GodOtherCommand());
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
            Messages.noPerm(context, "/god");
            return;
        }

        toggleGod(context, playerRef, playerRef);
    }

    private void toggleGod(@Nonnull CommandContext context, @Nonnull PlayerRef playerRef, @Nonnull PlayerRef target) {
        if (!playerRef.getUuid().equals(target.getUuid())
                && !xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), OTHERS_PERMISSION)) {
            Messages.noPerm(context, "/god");
            return;
        }

        boolean nowEnabled = godManager.toggle(target.getUuid());

        boolean isSelf = playerRef.getUuid().equals(target.getUuid());
        if (isSelf) {
            Messages.okKey(context, nowEnabled ? "god.enabled" : "god.disabled", java.util.Map.of());
        } else {
            Messages.okKey(context,
                    nowEnabled ? "god.enabled_for" : "god.disabled_for",
                    java.util.Map.of("player", target.getUsername()));
            Messages.sendPrefixedKey(target,
                    nowEnabled ? "god.enabled_by" : "god.disabled_by",
                    java.util.Map.of("player", playerRef.getUsername()));
        }
    }

    private final class GodOtherCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> targetArg;

        private GodOtherCommand() {
            super("Toggle another player's invulnerability");
            this.targetArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
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
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
                Messages.noPerm(context, "/god");
                return;
            }
            PlayerRef target = context.get(targetArg);
            if (target == null) {
                Messages.errKey(context, "player.not_found", java.util.Map.of());
                return;
            }
            toggleGod(context, playerRef, target);
        }
    }
}




