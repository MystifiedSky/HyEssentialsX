package xyz.thelegacyvoyage.hyessentialsx.commands.tpa;

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
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
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
        this.setPermissionGroups();
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
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/tpahere");
            return;
        }
        if (!config.isTpaEnabled()) {
            Messages.errKey(context, "tpa.disabled", java.util.Map.of());
            return;
        }
        if (!cooldowns.canUse(context, playerRef, CooldownKeys.TPAHERE, "/tpahere", BYPASS_PERMISSION)) {
            return;
        }

        PlayerRef target = context.get(targetArg);
        if (target == null) {
            Messages.errKey(context, "player.not_found", java.util.Map.of());
            return;
        }

        if (target.getUuid().equals(playerRef.getUuid())) {
            Messages.errKey(context, "tpa.self", java.util.Map.of());
            return;
        }

        boolean created = tpManager.addTpaHereRequest(playerRef.getUuid(), target.getUuid());
        if (!created) {
            Messages.warnKey(context, "tpa.request.pending", java.util.Map.of("player", target.getUsername()));
            return;
        }

        Messages.okKey(context, "tpa.request.sent", java.util.Map.of("player", target.getUsername()));
        Messages.sendKey(target, "tpahere.request.received", java.util.Map.of("player", playerRef.getUsername()));
        cooldowns.apply(playerRef, CooldownKeys.TPAHERE);
    }
}




