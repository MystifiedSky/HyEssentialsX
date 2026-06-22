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
import xyz.thelegacyvoyage.hyessentialsx.models.HomeModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import java.util.List;

public final class HomeCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.home";

    private final HomeManager homeManager;
    private final ConfigManager config;
    private final RequiredArg<String> nameArg;
    public HomeCommand(@Nonnull HomeManager homeManager, @Nonnull ConfigManager config) {
        super("home", "Teleports to a home");
        this.homeManager = homeManager;
        this.config = config;
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
            Messages.err(context, "Homes are disabled.");
            return;
        }

        String name = context.get(nameArg);
        String rawArgs = CommandUtil.stripCommandName(context.getInputString()).trim();
        if (rawArgs.isEmpty()) {
            List<String> homes = homeManager.listHomes(playerRef.getUuid());
            if (homes.isEmpty()) {
                Messages.err(context, "You have no homes set.");
                return;
            }
            if (homes.size() == 1) {
                name = homes.get(0);
            } else if (homes.contains("home")) {
                name = "home";
            } else {
                Messages.err(context, "Multiple homes set. Use /home <name>.");
                return;
            }
        }

        HomeModel home = homeManager.getHome(playerRef.getUuid(), name);
        if (home == null) {
            Messages.err(context, "Home not found.");
            return;
        }

        String err = TeleportationUtil.teleportToLocation(
                store,
                ref,
                home.getWorldName(),
                home.getX(), home.getY(), home.getZ(),
                home.getYaw(), home.getPitch()
        );
        if (err != null) {
            Messages.err(context, err);
            return;
        }

        Messages.ok(context, "Teleported to home '&f" + name + "&a'.");
    }
}



