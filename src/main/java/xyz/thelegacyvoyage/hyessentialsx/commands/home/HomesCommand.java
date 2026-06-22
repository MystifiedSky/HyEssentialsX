package xyz.thelegacyvoyage.hyessentialsx.commands.home;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.HomeManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;

public final class HomesCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.homes";

    private final HomeManager homeManager;
    private final ConfigManager config;

    public HomesCommand(@Nonnull HomeManager homeManager, @Nonnull ConfigManager config) {
        super("homes", "Lists all your homes");
        this.homeManager = homeManager;
        this.config = config;
        this.setPermissionGroup(null);
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
            Messages.noPerm(context, "/homes");
            return;
        }
        if (!config.isHomesEnabled()) {
            Messages.err(context, "Homes are disabled.");
            return;
        }

        List<String> homes = homeManager.listHomes(playerRef.getUuid());
        if (homes.isEmpty()) {
            Messages.warn(context, "You have no homes set.");
            return;
        }

        Messages.send(context, "&aHomes: &f" + String.join(", ", homes));
    }
}



