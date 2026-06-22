package xyz.thelegacyvoyage.hyessentialsx.commands.home;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.HomeManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class DelHomeCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.delhome";

    private final HomeManager homeManager;
    private final ConfigManager config;
    private final RequiredArg<String> nameArg;
    public DelHomeCommand(@Nonnull HomeManager homeManager, @Nonnull ConfigManager config) {
        super("delhome", "Deletes a home");
        this.homeManager = homeManager;
        this.config = config;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"remhome", "rmhome"});
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
            Messages.noPerm(context, "/delhome");
            return;
        }
        if (!config.isHomesEnabled()) {
            Messages.errKey(context, "home.disabled", java.util.Map.of());
            return;
        }

        String name = context.get(nameArg);
        if (name == null || name.isBlank()) {
            Messages.errKey(context, "home.name_required", java.util.Map.of());
            return;
        }

        boolean removed = homeManager.removeHome(playerRef.getUuid(), name);
        if (!removed) {
            Messages.errKey(context, "home.not_found", java.util.Map.of());
            return;
        }

        Messages.okKey(context, "home.deleted", java.util.Map.of("home", name));
    }
}



