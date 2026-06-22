package xyz.thelegacyvoyage.hyessentialsx.commands.inventory;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.util.InventoryUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.Map;

@SuppressWarnings("removal")
public final class ClearInventoryCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.clearinventory";
    private static final String OTHER_PERMISSION = "hyessentialsx.clearinventory.other";

    private final OptionalArg<PlayerRef> targetArg;

    public ClearInventoryCommand() {
        super("clearinventory", "Clears inventory");
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"ci"});
        this.targetArg = withOptionalArg("player", "Target player", ArgTypes.PLAYER_REF);
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
            Messages.noPerm(context, "/clearinventory");
            return;
        }

        PlayerRef target = playerRef;
        if (context.provided(targetArg)) {
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), OTHER_PERMISSION)) {
                Messages.noPerm(context, "/clearinventory <player>");
                return;
            }
            target = context.get(targetArg);
            if (target == null) {
                Messages.errKey(context, "player.not_found", Map.of());
                return;
            }
        }

        boolean isSelf = playerRef.getUuid().equals(target.getUuid());
        if (!isSelf && playerRef.getWorldUuid() != null && target.getWorldUuid() != null
                && !playerRef.getWorldUuid().equals(target.getWorldUuid())) {
            Messages.errKey(context, "error.target_world", Map.of());
            return;
        }

        Ref<EntityStore> targetRef = target.getReference();
        Player player = store.getComponent(targetRef, Player.getComponentType());
        if (player == null) {
            Messages.errKey(context, "error.inventory_access", Map.of());
            return;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            Messages.errKey(context, "error.inventory_access", Map.of());
            return;
        }

        InventoryUtil.clear(inventory);
        if (isSelf) {
            Messages.okKey(context, "inventory.cleared", Map.of());
        } else {
            Messages.okKey(context, "inventory.cleared_other", Map.of("player", target.getUsername()));
            Messages.sendPrefixedKey(target, "inventory.cleared_by", Map.of("player", playerRef.getUsername()));
        }
    }
}




