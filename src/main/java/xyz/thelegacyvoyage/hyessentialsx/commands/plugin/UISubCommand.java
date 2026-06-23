package xyz.thelegacyvoyage.hyessentialsx.commands.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import xyz.thelegacyvoyage.hyessentialsx.ui.AdminCommandCenterContext;
import xyz.thelegacyvoyage.hyessentialsx.ui.HyEssentialsXDashboardUI;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * /hyessentialsx ui - Open the plugin dashboard UI
 *
 * Extends AbstractPlayerCommand to ensure proper thread handling
 * when opening custom UI pages.
 */
public class UISubCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.ui";

    private final AdminCommandCenterContext dashboardContext;

    public UISubCommand(@Nonnull AdminCommandCenterContext dashboardContext) {
        super("ui", "Open the HyEssentialsX staff command center");
        this.dashboardContext = dashboardContext;
        this.addAliases(new String[]{"dashboard", "gui", "admin", "commandcenter", "staff"});
        this.setPermissionGroups();
        CommandPermissionUtil.apply(this, PERMISSION_NODE);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    /**
     * Called on the world thread with proper player context.
     */
    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        if (!CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/hyessentialsx ui");
            return;
        }
        context.sendMessage(Message.raw("Opening HyEssentialsX Command Center..."));

        try {
            // Get the player component (safe - we're on world thread)
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                Messages.errKey(context, "error.player_only", Map.of());
                return;
            }

            // Create and open the custom page
            HyEssentialsXDashboardUI dashboardPage = new HyEssentialsXDashboardUI(playerRef, dashboardContext);
            player.getPageManager().openCustomPage(ref, store, dashboardPage);
            context.sendMessage(Message.raw("Command center opened. Press ESC to close."));
        } catch (Exception e) {
            context.sendMessage(Message.raw("Error opening command center: " + e.getMessage()));
        }
    }
}

