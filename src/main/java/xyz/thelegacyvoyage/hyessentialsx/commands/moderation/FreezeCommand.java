package xyz.thelegacyvoyage.hyessentialsx.commands.moderation;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.FreezeManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public final class FreezeCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.freeze";
    private static final String ALL_PERMISSION = "hyessentialsx.freeze.all";

    private final FreezeManager freezeManager;

    public FreezeCommand(@Nonnull FreezeManager freezeManager) {
        super("freeze", "Freeze a player");
        this.freezeManager = freezeManager;
        this.setPermissionGroups();
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
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/freeze");
            return;
        }

        List<String> args = CommandInputUtil.getArgs(context);
        if (args.isEmpty()) {
            Messages.errKey(context, "freeze.usage", Map.of());
            return;
        }

        String first = args.get(0);
        if ("all".equalsIgnoreCase(first)) {
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), ALL_PERMISSION)) {
                Messages.noPerm(context, "/freeze all");
                return;
            }
            int count = 0;
            for (PlayerRef target : Universe.get().getPlayers()) {
                if (target == null) continue;
                if (target.getUuid().equals(playerRef.getUuid())) {
                    continue;
                }
                if (freezeManager.isFrozen(target.getUuid())) {
                    continue;
                }
                freezeManager.freeze(target);
                count++;
                Messages.sendPrefixedKey(target, "freeze.target", Map.of());
            }
            Messages.okKey(context, "freeze.count", Map.of("count", String.valueOf(count)));
            return;
        }

        PlayerRef target = Universe.get().getPlayerByUsername(first, NameMatching.EXACT_IGNORE_CASE);
        if (target == null) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }

        if (freezeManager.isFrozen(target.getUuid())) {
            Messages.errKey(context, "freeze.already", Map.of());
            return;
        }

        freezeManager.freeze(target);
        Messages.okKey(context, "freeze.single", Map.of("player", target.getUsername()));
        Messages.sendPrefixedKey(target, "freeze.target", Map.of());
    }
}

