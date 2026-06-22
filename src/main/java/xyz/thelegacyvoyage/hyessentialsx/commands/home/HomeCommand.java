package xyz.thelegacyvoyage.hyessentialsx.commands.home;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.HomeManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.models.HomeModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;
import xyz.thelegacyvoyage.hyessentialsx.ui.HomesUI;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public final class HomeCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.home";
    private static final String BYPASS_PERMISSION = "hyessentialsx.home.bypass";

    private final HomeManager homeManager;
    private final TPManager tpManager;
    private final ConfigManager config;
    private final CommandCooldownManager cooldowns;
    public HomeCommand(@Nonnull HomeManager homeManager,
                       @Nonnull TPManager tpManager,
                       @Nonnull ConfigManager config,
                       @Nonnull CommandCooldownManager cooldowns) {
        super("home", "Teleports to a home");
        this.homeManager = homeManager;
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
            Messages.noPerm(context, "/home");
            return;
        }
        if (!config.isHomesEnabled()) {
            Messages.errKey(context, "home.disabled", Map.of());
            return;
        }
        String name = CommandInputUtil.getArg(context, 0);
        if (name == null || name.trim().isEmpty()) {
            List<String> homes = homeManager.listHomes(playerRef.getUuid());
            if (homes.isEmpty()) {
                Messages.errKey(context, "home.none", Map.of());
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                Messages.errKey(context, "home.ui_failed", Map.of());
                return;
            }
            HomesUI page = new HomesUI(playerRef, homeManager, tpManager, config, cooldowns);
            page.open(player, ref, store);
            return;
        }

        if (!cooldowns.canUse(context, playerRef, CooldownKeys.HOME, "/home", BYPASS_PERMISSION)) {
            return;
        }

        HomeModel home = homeManager.getHome(playerRef.getUuid(), name.trim());
        if (home == null) {
            Messages.errKey(context, "home.not_found", Map.of());
            return;
        }

        final String homeName = name.trim();
        int warmupSeconds = config.getHomeWarmupSeconds();
        if (warmupSeconds > 0) {
            if (tpManager.hasPending(playerRef.getUuid())) {
                Messages.errKey(context, "teleport.pending", Map.of());
                return;
            }
            com.hypixel.hytale.math.vector.Transform transform = playerRef.getTransform();
            if (transform == null || transform.getPosition() == null) {
                Messages.errKey(context, "teleport.position_unavailable", Map.of());
                return;
            }
            tpManager.queue(
                    playerRef.getUuid(),
                    transform.getPosition().clone(),
                    warmupSeconds,
                    buffer -> {
                        String err = TeleportationUtil.teleportToLocation(
                                buffer,
                                ref,
                                home.getWorldId(),
                                home.getWorldName(),
                                home.getX(), home.getY(), home.getZ(),
                                home.getYaw(), home.getPitch()
                        );
                        if (err != null) {
                            Messages.sendPrefixed(playerRef, err);
                            return;
                        }
                        cooldowns.apply(playerRef, CooldownKeys.HOME);
                        Messages.sendPrefixedKey(playerRef, "teleport.success.home", Map.of("home", homeName));
                    }
            );
            Messages.sendPrefixedKey(playerRef, "teleport.warmup", Map.of("seconds", String.valueOf(warmupSeconds)));
            return;
        }

        String err = TeleportationUtil.teleportToLocation(
                store,
                ref,
                home.getWorldId(),
                home.getWorldName(),
                home.getX(), home.getY(), home.getZ(),
                home.getYaw(), home.getPitch()
        );
        if (err != null) {
            Messages.err(context, err);
            return;
        }

        cooldowns.apply(playerRef, CooldownKeys.HOME);
        Messages.okKey(context, "teleport.success.home", Map.of("home", homeName));
    }
}



