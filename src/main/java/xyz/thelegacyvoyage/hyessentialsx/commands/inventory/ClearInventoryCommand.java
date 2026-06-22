package xyz.thelegacyvoyage.hyessentialsx.commands.inventory;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.InventoryUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public final class ClearInventoryCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.clearinventory";
    private static final String OTHER_PERMISSION = "hyessentialsx.clearinventory.other";

    public ClearInventoryCommand() {
        super("clearinventory", "Clears inventory");
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"ci"});
        this.setAllowsExtraArguments(true);
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
            Messages.noPerm(context, "/clearinventory");
            return;
        }

        List<String> args = CommandInputUtil.getArgs(context);
        PlayerRef target = playerRef;
        if (!args.isEmpty()) {
            if (!context.sender().hasPermission(OTHER_PERMISSION)) {
                Messages.noPerm(context, "/clearinventory <player>");
                return;
            }
            String targetName = args.get(0);
            PlayerRef resolved = Universe.get().getPlayerByUsername(targetName, NameMatching.EXACT_IGNORE_CASE);
            if (resolved == null) {
                Messages.errKey(context, "player.not_found", Map.of());
                return;
            }
            target = resolved;
        }

        boolean isSelf = playerRef.getUuid().equals(target.getUuid());
        if (!isSelf && playerRef.getWorldUuid() != null && target.getWorldUuid() != null
                && !playerRef.getWorldUuid().equals(target.getWorldUuid())) {
            Messages.err(context, "Target must be in your world.");
            return;
        }

        Ref<EntityStore> targetRef = target.getReference();
        Player player = store.getComponent(targetRef, Player.getComponentType());
        if (player == null) {
            Messages.err(context, "Could not access inventory.");
            return;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            Messages.err(context, "Could not access inventory.");
            return;
        }

        InventoryUtil.clear(inventory);
        if (isSelf) {
            Messages.ok(context, "Inventory cleared.");
        } else {
            Messages.ok(context, "Cleared inventory for " + target.getUsername() + ".");
            Messages.sendPrefixed(target, "Your inventory was cleared by " + playerRef.getUsername() + ".");
        }
    }
}




