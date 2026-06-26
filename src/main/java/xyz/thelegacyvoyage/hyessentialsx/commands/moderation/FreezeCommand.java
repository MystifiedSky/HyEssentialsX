package xyz.thelegacyvoyage.hyessentialsx.commands.moderation;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.FreezeManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.Map;

public final class FreezeCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.freeze";
    private static final String ALL_PERMISSION = "hyessentialsx.freeze.all";

    private final FreezeManager freezeManager;

    public FreezeCommand(@Nonnull FreezeManager freezeManager) {
        super("freeze", "Freeze a player");
        this.freezeManager = freezeManager;
        this.setPermissionGroups();
        this.addUsageVariant(new FreezePlayerCommand());
        this.addSubCommand(new FreezeAllCommand());
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
        Messages.errKey(context, "freeze.usage", Map.of());
    }

    private void freezeTarget(@Nonnull CommandContext context, PlayerRef target) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/freeze");
            return;
        }
        if (target == null) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }

        if (freezeManager.isFrozen(target.getUuid())) {
            Messages.errKey(context, "freeze.already", Map.of());
            return;
        }

        freezeManager.freeze(target);
        Messages.okKey(context, "freeze.single", Map.of("player", target.getUsername()));
        Messages.sendPrefixedKey(target, "freeze.target", Map.of());
    }

    private final class FreezePlayerCommand extends AbstractPlayerCommand {

        private final RequiredArg<PlayerRef> targetArg;

        private FreezePlayerCommand() {
            super("Freeze a player");
            this.setPermissionGroups();
            xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
            this.targetArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
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
            freezeTarget(context, context.get(targetArg));
        }
    }

    private final class FreezeAllCommand extends AbstractPlayerCommand {

        private FreezeAllCommand() {
            super("all", "Freeze all online players");
            this.setPermissionGroups();
            xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, ALL_PERMISSION);
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
                Messages.noPerm(context, "/freeze");
                return;
            }
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), ALL_PERMISSION)) {
                Messages.noPerm(context, "/freeze all");
                return;
            }
            int count = 0;
            for (PlayerRef target : Universe.get().getPlayers()) {
                if (target == null) continue;
                if (target.getUuid().equals(playerRef.getUuid())) {
                    continue;
                }
                if (freezeManager.isFrozen(target.getUuid())) {
                    continue;
                }
                freezeManager.freeze(target);
                count++;
                Messages.sendPrefixedKey(target, "freeze.target", Map.of());
            }
            Messages.okKey(context, "freeze.count", Map.of("count", String.valueOf(count)));
        }
    }
}

