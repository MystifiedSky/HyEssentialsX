package xyz.thelegacyvoyage.hyessentialsx.commands.teleport;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class TpahereCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.tpahere";
    private static final String BYPASS_PERMISSION = "hyessentialsx.tpahere.bypass";

    private final TPManager tpManager;
    private final ConfigManager config;
    private final CommandCooldownManager cooldowns;
    private final RequiredArg<PlayerRef> targetArg;

    public TpahereCommand(@Nonnull TPManager tpManager,
                          @Nonnull ConfigManager config,
                          @Nonnull CommandCooldownManager cooldowns) {
        super("tpahere", "Requests player to teleport to you");
        this.tpManager = tpManager;
        this.config = config;
        this.cooldowns = cooldowns;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.targetArg = withRequiredArg("player", "Player to request", ArgTypes.PLAYER_REF);
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
            Messages.noPerm(context, "/tpahere");
            return;
        }
        if (!config.isTpaEnabled()) {
            Messages.err(context, "TPA is disabled.");
            return;
        }
        if (!cooldowns.canUse(context, playerRef, CooldownKeys.TPAHERE, "/tpahere", BYPASS_PERMISSION)) {
            return;
        }

        PlayerRef target = context.get(targetArg);
        if (target == null) {
            Messages.err(context, "Player not found.");
            return;
        }

        if (target.getUuid().equals(playerRef.getUuid())) {
            Messages.err(context, "You can't request yourself.");
            return;
        }

        boolean created = tpManager.addTpaHereRequest(playerRef.getUuid(), target.getUuid());
        if (!created) {
            Messages.warn(context, "You already have a pending request to " + target.getUsername() + ".");
            return;
        }

        Messages.ok(context, "Teleport request sent to " + target.getUsername() + ".");
        Messages.send(target,
                "&#FFFF55" + playerRef.getUsername()
                        + "&#FFFFFF wants you to teleport to them. Type &#FFFF55/tpaaccept&#FFFFFF to accept.");
        cooldowns.apply(playerRef, CooldownKeys.TPAHERE);
    }
}



