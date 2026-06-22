package xyz.thelegacyvoyage.hyessentialsx.commands.warp;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.WarpManager;
import xyz.thelegacyvoyage.hyessentialsx.ui.WarpsUI;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public final class WarpsCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.warps";

    private final WarpManager warpManager;
    private final ConfigManager config;
    private final TPManager tpManager;
    private final CommandCooldownManager cooldowns;

    public WarpsCommand(@Nonnull WarpManager warpManager,
                        @Nonnull TPManager tpManager,
                        @Nonnull ConfigManager config,
                        @Nonnull CommandCooldownManager cooldowns) {
        super("warps", "Lists all warps");
        this.warpManager = warpManager;
        this.config = config;
        this.tpManager = tpManager;
        this.cooldowns = cooldowns;
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
            Messages.noPerm(context, "/warps");
            return;
        }
        if (!config.isWarpsEnabled()) {
            Messages.err(context, "Warps are disabled.");
            return;
        }

        if (config.isWarpsGuiEnabled()) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                Messages.errKey(context, "warp.ui_failed", Map.of());
                return;
            }
            WarpsUI page = new WarpsUI(playerRef, warpManager, tpManager, config, cooldowns);
            page.open(player, ref, store);
            return;
        }

        List<String> warps = warpManager.listWarps();
        if (warps.isEmpty()) {
            Messages.warn(context, "No warps set.");
            return;
        }

        Messages.sendKey(context, "warp.list", Map.of("warps", String.join(", ", warps)));
    }
}



