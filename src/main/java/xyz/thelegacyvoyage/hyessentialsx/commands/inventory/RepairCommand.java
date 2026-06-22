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
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.InventoryUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;

public final class RepairCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.repair";
    private static final String BYPASS_PERMISSION = "hyessentialsx.repair.bypass";
    private static final String OTHER_PERMISSION = "hyessentialsx.repair.other";
    private static final String ALL_PERMISSION = "hyessentialsx.repair.all";

    private final CommandCooldownManager cooldowns;

    public RepairCommand(@Nonnull CommandCooldownManager cooldowns) {
        super("repair", "Repairs items");
        this.cooldowns = cooldowns;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"fix"});
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
            Messages.noPerm(context, "/repair");
            return;
        }
        if (!cooldowns.canUse(context, playerRef, CooldownKeys.REPAIR, "/repair", BYPASS_PERMISSION)) {
            return;
        }

        List<String> args = CommandInputUtil.getArgs(context);
        boolean repairAll = false;
        String targetName = null;

        if (!args.isEmpty()) {
            String first = args.get(0);
            if (isAllToken(first)) {
                repairAll = true;
                if (args.size() >= 2) {
                    targetName = args.get(1);
                }
            } else {
                targetName = first;
                if (args.size() >= 2 && isAllToken(args.get(1))) {
                    repairAll = true;
                }
            }
        }

        PlayerRef target = playerRef;
        if (targetName != null && !targetName.isBlank()) {
            PlayerRef resolved = Universe.get().getPlayerByUsername(targetName, NameMatching.EXACT_IGNORE_CASE);
            if (resolved == null) {
                Messages.errKey(context, "player.not_found", java.util.Map.of());
                return;
            }
            target = resolved;
        }

        boolean isSelf = playerRef.getUuid().equals(target.getUuid());
        if (!isSelf && !context.sender().hasPermission(OTHER_PERMISSION)) {
            Messages.noPerm(context, "/repair " + target.getUsername());
            return;
        }
        if (repairAll && !context.sender().hasPermission(ALL_PERMISSION)) {
            Messages.noPerm(context, "/repair all");
            return;
        }

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

        int repaired = repairAll ? InventoryUtil.repairAll(inventory) : InventoryUtil.repairInHand(inventory);
        if (repaired <= 0) {
            Messages.warn(context, "Nothing to repair.");
            return;
        }

        cooldowns.apply(playerRef, CooldownKeys.REPAIR);
        if (isSelf) {
            Messages.ok(context, "Repaired " + repaired + " item(s).");
        } else {
            Messages.ok(context, "Repaired " + repaired + " item(s) for " + target.getUsername() + ".");
            Messages.sendPrefixed(target, "Your items were repaired by " + playerRef.getUsername() + ".");
        }
    }

    private boolean isAllToken(@Nonnull String token) {
        String lowered = token.toLowerCase();
        return lowered.equals("all") || lowered.equals("--all");
    }
}




