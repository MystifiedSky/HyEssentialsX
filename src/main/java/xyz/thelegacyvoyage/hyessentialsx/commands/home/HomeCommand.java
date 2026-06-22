package xyz.thelegacyvoyage.hyessentialsx.commands.home;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.HomeManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.models.HomeModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

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
    private final RequiredArg<String> nameArg;
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
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.nameArg = withRequiredArg("name", "Home name", ArgTypes.STRING);
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
        if (!cooldowns.canUse(context, playerRef, CooldownKeys.HOME, "/home", BYPASS_PERMISSION)) {
            return;
        }

        String name = context.get(nameArg);
        String rawArgs = CommandUtil.stripCommandName(context.getInputString()).trim();
        if (rawArgs.isEmpty()) {
            List<String> homes = homeManager.listHomes(playerRef.getUuid());
            if (homes.isEmpty()) {
                Messages.errKey(context, "home.none", Map.of());
                return;
            }
            if (homes.size() == 1) {
                name = homes.get(0);
            } else if (homes.contains("home")) {
                name = "home";
            } else {
                Messages.errKey(context, "home.multiple", Map.of());
                return;
            }
        }

        HomeModel home = homeManager.getHome(playerRef.getUuid(), name);
        if (home == null) {
            Messages.errKey(context, "home.not_found", Map.of());
            return;
        }

        final String homeName = name;
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
        Messages.okKey(context, "teleport.success.home", Map.of("home", name));
    }
}



