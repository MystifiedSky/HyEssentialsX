package xyz.thelegacyvoyage.hyessentialsx.commands.tpa;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.ui.TpaUI;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class TpaCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.tpa";
    private static final String BYPASS_PERMISSION = "hyessentialsx.tpa.bypass";

    private final TPManager tpManager;
    private final ConfigManager config;
    private final CommandCooldownManager cooldowns;
    public TpaCommand(@Nonnull TPManager tpManager,
                      @Nonnull ConfigManager config,
                      @Nonnull CommandCooldownManager cooldowns) {
        super("tpa", "Request to teleport to another player");
        this.tpManager = tpManager;
        this.config = config;
        this.cooldowns = cooldowns;
        this.setPermissionGroup(null);
        this.setAllowsExtraArguments(true);
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
            Messages.noPerm(context, "/tpa");
            return;
        }
        if (!config.isTpaEnabled()) {
            Messages.err(context, "TPA is disabled.");
            return;
        }
        var args = CommandInputUtil.getArgs(context);
        if (args.isEmpty()) {
            if (config.isTpaGuiEnabled()) {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) {
                    Messages.errKey(context, "tpa.ui_failed", java.util.Map.of());
                    return;
                }
                TpaUI page = new TpaUI(playerRef, tpManager, cooldowns, config, null);
                page.open(player, ref, store);
                return;
            }
            Messages.err(context, "Usage: /tpa <player>");
            return;
        }

        if (!cooldowns.canUse(context, playerRef, CooldownKeys.TPA, "/tpa", BYPASS_PERMISSION)) {
            return;
        }

        String targetName = args.get(0);
        PlayerRef target = findOnlinePlayer(targetName);
        if (target == null) {
            Messages.err(context, "Player not found.");
            return;
        }

        if (target.getUuid().equals(playerRef.getUuid())) {
            Messages.err(context, "You can't send a request to yourself.");
            return;
        }

        if (tpManager.isTpaIgnored(target.getUuid())) {
            Messages.warn(context, target.getUsername() + " is not accepting teleport requests.");
            return;
        }

        boolean created = tpManager.addTpaRequest(playerRef.getUuid(), target.getUuid());
        if (!created) {
            Messages.warn(context, "You already have a pending request to " + target.getUsername() + ".");
            return;
        }

        Messages.ok(context, "Teleport request sent to " + target.getUsername() + ".");
        Messages.send(target,
                "&#FFFF55" + playerRef.getUsername()
                        + "&#FFFFFF wants to teleport to you. Type &#FFFF55/tpaaccept&#FFFFFF to accept.");
        cooldowns.apply(playerRef, CooldownKeys.TPA);
    }

    private static PlayerRef findOnlinePlayer(@Nonnull String name) {
        for (PlayerRef ref : Universe.get().getPlayers()) {
            if (ref == null) continue;
            String username = ref.getUsername();
            if (username != null && username.equalsIgnoreCase(name)) {
                return ref;
            }
        }
        return null;
    }
}




