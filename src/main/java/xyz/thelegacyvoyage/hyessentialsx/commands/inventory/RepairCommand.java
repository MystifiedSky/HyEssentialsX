package xyz.thelegacyvoyage.hyessentialsx.commands.inventory;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
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

public final class RepairCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.repair";

    private final FlagArg allArg;
    private final OptionalArg<String> modeArg;

    public RepairCommand() {
        super("repair", "Repairs items");
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"fix"});
        this.allArg = withFlagArg("--all", "Repair all items");
        this.modeArg = withOptionalArg("mode", "Use 'all' to repair all items", ArgTypes.STRING);
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
            Messages.noPerm(context, "/repair");
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.err(context, "Could not access inventory.");
            return;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            Messages.err(context, "Could not access inventory.");
            return;
        }

        boolean repairAll = context.provided(allArg);
        if (!repairAll && context.provided(modeArg)) {
            String mode = context.get(modeArg);
            repairAll = mode != null && mode.equalsIgnoreCase("all");
        }

        int repaired = repairAll ? InventoryUtil.repairAll(inventory) : InventoryUtil.repairInHand(inventory);
        if (repaired <= 0) {
            Messages.warn(context, "Nothing to repair.");
            return;
        }

        Messages.ok(context, "Repaired " + repaired + " item(s).");
    }
}



