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

public final class AdminCommandCenterCommand extends AbstractPlayerCommand {

    public static final String PERMISSION_NODE = "hyessentialsx.ui";

    private final AdminCommandCenterContext dashboardContext;

    public AdminCommandCenterCommand(@Nonnull AdminCommandCenterContext dashboardContext) {
        super("hexadmin", "Open the HyEssentialsX staff command center");
        this.dashboardContext = dashboardContext;
        this.addAliases(new String[]{"hyadmin", "essadmin", "admincenter", "staffcenter"});
        this.setPermissionGroups();
        CommandPermissionUtil.apply(this, PERMISSION_NODE);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        if (!CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/hexadmin");
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.errKey(context, "error.player_only", Map.of());
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new HyEssentialsXDashboardUI(playerRef, dashboardContext));
        context.sendMessage(Message.raw("HyEssentialsX Command Center opened. Press ESC to close."));
    }
}
