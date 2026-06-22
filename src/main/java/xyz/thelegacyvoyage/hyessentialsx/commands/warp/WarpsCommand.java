package xyz.thelegacyvoyage.hyessentialsx.commands.warp;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.WarpManager;
import xyz.thelegacyvoyage.hyessentialsx.ui.WarpsUI;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WarpsCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.warps";
    private static final String WARP_GLOBAL_PERMISSION = "hyessentialsx.warp";
    private static final String WARP_PERMISSION_PREFIX = WARP_GLOBAL_PERMISSION + ".";

    private final WarpManager warpManager;
    private final ConfigManager config;
    private final TPManager tpManager;
    private final CommandCooldownManager cooldowns;
    private final BackManager backManager;

    public WarpsCommand(@Nonnull WarpManager warpManager,
                        @Nonnull TPManager tpManager,
                        @Nonnull ConfigManager config,
                        @Nonnull CommandCooldownManager cooldowns,
                        @Nonnull BackManager backManager) {
        super("warps", "Lists all warps");
        this.warpManager = warpManager;
        this.config = config;
        this.tpManager = tpManager;
        this.cooldowns = cooldowns;
        this.backManager = backManager;
        this.setPermissionGroups();
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
            Messages.noPerm(context, "/warps");
            return;
        }
        if (!config.isWarpsEnabled()) {
            Messages.errKey(context, "warp.disabled", Map.of());
            return;
        }

        if (config.isWarpsGuiEnabled()) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                Messages.errKey(context, "warp.ui_failed", Map.of());
                return;
            }
            WarpsUI page = new WarpsUI(playerRef, warpManager, tpManager, config, cooldowns, backManager);
            page.open(player, ref, store);
            return;
        }

        List<String> warps = warpManager.listWarps();
        if (warps.isEmpty()) {
            Messages.warnKey(context, "warp.none", Map.of());
            return;
        }

        List<String> visibleWarps = filterAccessibleWarps(playerRef, warps);
        if (visibleWarps.isEmpty()) {
            Messages.warnKey(context, "warp.none", Map.of());
            return;
        }

        Messages.sendKey(context, "warp.list", Map.of("warps", String.join(", ", visibleWarps)));
    }

    @Nonnull
    private List<String> filterAccessibleWarps(@Nonnull PlayerRef playerRef, @Nonnull List<String> warps) {
        if (CommandPermissionUtil.hasPermission(playerRef, WARP_GLOBAL_PERMISSION)) {
            return warps;
        }
        List<String> visible = new ArrayList<>();
        for (String warpName : warps) {
            if (hasWarpPermission(playerRef, warpName)) {
                visible.add(warpName);
            }
        }
        return visible;
    }

    private boolean hasWarpPermission(@Nonnull PlayerRef playerRef, @Nonnull String warpName) {
        if (CommandPermissionUtil.hasPermission(playerRef, WARP_GLOBAL_PERMISSION)) {
            return true;
        }
        String normalized = normalizePermissionSegment(warpName);
        if (normalized.isEmpty()) {
            return false;
        }
        return CommandPermissionUtil.hasPermission(playerRef, WARP_PERMISSION_PREFIX + normalized);
    }

    @Nonnull
    private String normalizePermissionSegment(@Nonnull String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}




