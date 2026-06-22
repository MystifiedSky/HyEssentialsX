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
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/tpa");
            return;
        }
        if (!config.isTpaEnabled()) {
            Messages.errKey(context, "tpa.disabled", java.util.Map.of());
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
            Messages.errKey(context, "tpa.usage", java.util.Map.of());
            return;
        }

        if (!cooldowns.canUse(context, playerRef, CooldownKeys.TPA, "/tpa", BYPASS_PERMISSION)) {
            return;
        }

        String targetName = args.get(0);
        PlayerRef target = findOnlinePlayer(targetName);
        if (target == null) {
            Messages.errKey(context, "player.not_found", java.util.Map.of());
            return;
        }

        if (target.getUuid().equals(playerRef.getUuid())) {
            Messages.errKey(context, "tpa.self", java.util.Map.of());
            return;
        }

        if (tpManager.isTpaIgnored(target.getUuid())) {
            Messages.warnKey(context, "tpa.target_ignored", java.util.Map.of("player", target.getUsername()));
            return;
        }

        boolean created = tpManager.addTpaRequest(playerRef.getUuid(), target.getUuid());
        if (!created) {
            Messages.warnKey(context, "tpa.request.pending", java.util.Map.of("player", target.getUsername()));
            return;
        }

        Messages.okKey(context, "tpa.request.sent", java.util.Map.of("player", target.getUsername()));
        Messages.sendKey(target, "tpa.request.received", java.util.Map.of("player", playerRef.getUsername()));
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




