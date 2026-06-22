package xyz.thelegacyvoyage.hyessentialsx.commands.inventory;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.InventoryUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

@SuppressWarnings("removal")
public final class RepairCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.repair";
    private static final String BYPASS_PERMISSION = "hyessentialsx.repair.bypass";
    private static final String OTHER_PERMISSION = "hyessentialsx.repair.other";
    private static final String ALL_PERMISSION = "hyessentialsx.repair.all";

    private final CommandCooldownManager cooldowns;

    public RepairCommand(@Nonnull CommandCooldownManager cooldowns) {
        super("repair", "Repairs items");
        this.cooldowns = cooldowns;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"fix"});
        this.addUsageVariant(new RepairOtherCommand());
        this.addSubCommand(new RepairAllCommand());
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
            Messages.noPerm(context, "/repair");
            return;
        }
        if (!cooldowns.canUse(context, playerRef, CooldownKeys.REPAIR, "/repair", BYPASS_PERMISSION)) {
            return;
        }

        repair(context, store, playerRef, playerRef, false);
    }

    private void repair(@Nonnull CommandContext context,
                        @Nonnull Store<EntityStore> store,
                        @Nonnull PlayerRef playerRef,
                        PlayerRef target,
                        boolean repairAll) {
        if (target == null) {
            Messages.errKey(context, "player.not_found", java.util.Map.of());
            return;
        }
        boolean isSelf = playerRef.getUuid().equals(target.getUuid());
        if (!isSelf && !xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), OTHER_PERMISSION)) {
            Messages.noPerm(context, "/repair " + target.getUsername());
            return;
        }
        if (repairAll && !xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), ALL_PERMISSION)) {
            Messages.noPerm(context, "/repair all");
            return;
        }
        if (!isSelf && playerRef.getWorldUuid() != null && target.getWorldUuid() != null
                && !playerRef.getWorldUuid().equals(target.getWorldUuid())) {
            Messages.errKey(context, "error.target_world", java.util.Map.of());
            return;
        }

        Ref<EntityStore> targetRef = target.getReference();
        Player player = store.getComponent(targetRef, Player.getComponentType());
        if (player == null) {
            Messages.errKey(context, "error.inventory_access", java.util.Map.of());
            return;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            Messages.errKey(context, "error.inventory_access", java.util.Map.of());
            return;
        }

        int repaired = repairAll ? InventoryUtil.repairAll(inventory) : InventoryUtil.repairInHand(inventory);
        if (repaired <= 0) {
            Messages.warnKey(context, "repair.none", java.util.Map.of());
            return;
        }

        cooldowns.apply(playerRef, CooldownKeys.REPAIR);
        if (isSelf) {
            Messages.okKey(context, "repair.self", java.util.Map.of("count", String.valueOf(repaired)));
        } else {
            Messages.okKey(context, "repair.other", java.util.Map.of(
                    "count", String.valueOf(repaired),
                    "player", target.getUsername()
            ));
            Messages.sendPrefixedKey(target, "repair.by", java.util.Map.of("player", playerRef.getUsername()));
        }
    }

    private final class RepairOtherCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> targetArg;

        private RepairOtherCommand() {
            super("Repairs a player's held item");
            this.setPermissionGroups();
            this.targetArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
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
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
                Messages.noPerm(context, "/repair");
                return;
            }
            if (!cooldowns.canUse(context, playerRef, CooldownKeys.REPAIR, "/repair", BYPASS_PERMISSION)) {
                return;
            }
            repair(context, store, playerRef, context.get(targetArg), false);
        }
    }

    private final class RepairAllCommand extends AbstractPlayerCommand {
        private final OptionalArg<PlayerRef> targetArg;

        private RepairAllCommand() {
            super("all", "Repairs all items");
            this.targetArg = withOptionalArg("player", "Target player", ArgTypes.PLAYER_REF);
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
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
                Messages.noPerm(context, "/repair");
                return;
            }
            if (!cooldowns.canUse(context, playerRef, CooldownKeys.REPAIR, "/repair", BYPASS_PERMISSION)) {
                return;
            }
            PlayerRef target = context.provided(targetArg) ? context.get(targetArg) : playerRef;
            repair(context, store, playerRef, target, true);
        }
    }
}




