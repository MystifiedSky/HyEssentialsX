package xyz.thelegacyvoyage.hyessentialsx.commands.home;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.HomeManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.ui.HomesUI;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public final class HomesCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.homes";

    private final HomeManager homeManager;
    private final ConfigManager config;
    private final TPManager tpManager;
    private final CommandCooldownManager cooldowns;
    private final BackManager backManager;

    public HomesCommand(@Nonnull HomeManager homeManager,
                        @Nonnull TPManager tpManager,
                        @Nonnull ConfigManager config,
                        @Nonnull CommandCooldownManager cooldowns,
                        @Nonnull BackManager backManager) {
        super("homes", "Lists all your homes");
        this.homeManager = homeManager;
        this.tpManager = tpManager;
        this.config = config;
        this.cooldowns = cooldowns;
        this.backManager = backManager;
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
            Messages.errKey(context, "home.none", Map.of());
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.errKey(context, "home.ui_failed", Map.of());
            return;
        }
        HomesUI page = new HomesUI(playerRef, homeManager, tpManager, config, cooldowns, backManager);
        page.open(player, ref, store);
    }
}



