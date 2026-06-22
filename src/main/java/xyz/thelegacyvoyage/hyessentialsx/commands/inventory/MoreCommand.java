package xyz.thelegacyvoyage.hyessentialsx.commands.inventory;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;

@SuppressWarnings("removal")
public final class MoreCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.more";
    private static final String LEGACY_PERMISSION = "hyessentailsx.more";
    private static final String OTHERS_PERMISSION = "hyessentialsx.more.other";

    public MoreCommand() {
        super("more", "Set held item to max stack size");
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.setAllowsExtraArguments(true);
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
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE) && !xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), LEGACY_PERMISSION)) {
            Messages.noPerm(context, "/more");
            return;
        }

        List<String> args = CommandInputUtil.getArgs(context);
        PlayerRef target = playerRef;
        if (!args.isEmpty()) {
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), OTHERS_PERMISSION)) {
                Messages.noPerm(context, "/more");
                return;
            }
            String targetName = args.get(0);
            PlayerRef resolved = Universe.get().getPlayerByUsername(targetName, NameMatching.EXACT_IGNORE_CASE);
            if (resolved == null) {
                Messages.errKey(context, "player.not_found", java.util.Map.of());
                return;
            }
            target = resolved;
        }
        boolean isSelf = playerRef.getUuid().equals(target.getUuid());

        if (!isSelf && playerRef.getWorldUuid() != null && target.getWorldUuid() != null
                && !playerRef.getWorldUuid().equals(target.getWorldUuid())) {
            Messages.errKey(context, "error.target_world", java.util.Map.of());
            return;
        }

        Ref<EntityStore> targetRef = target.getReference();
        Player targetPlayer = store.getComponent(targetRef, Player.getComponentType());
        if (targetPlayer == null) {
            Messages.errKey(context, "error.inventory_access", java.util.Map.of());
            return;
        }

        Inventory inventory = targetPlayer.getInventory();
        if (inventory == null) {
            Messages.errKey(context, "error.inventory_access", java.util.Map.of());
            return;
        }

        ItemStack held = inventory.getItemInHand();
        if (held == null || held.isEmpty()) {
            Messages.errKey(context, "more.no_item", java.util.Map.of());
            return;
        }

        Item item = held.getItem();
        int maxStack = item != null ? item.getMaxStack() : held.getQuantity();
        if (maxStack <= 1) {
            Messages.errKey(context, "more.not_stackable", java.util.Map.of());
            return;
        }

        if (held.getQuantity() >= maxStack) {
            Messages.warnKey(context, "more.already_max", java.util.Map.of());
            return;
        }

        ItemStack updated = withQuantity(held, maxStack);
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar == null) {
            Messages.errKey(context, "error.inventory_access", java.util.Map.of());
            return;
        }
        byte slot = inventory.getActiveHotbarSlot();
        hotbar.setItemStackForSlot(slot, updated);

        if (isSelf) {
            Messages.okKey(context, "more.success", java.util.Map.of("amount", String.valueOf(maxStack)));
        } else {
            Messages.okKey(context, "more.success_other", java.util.Map.of(
                    "player", target.getUsername(),
                    "amount", String.valueOf(maxStack)
            ));
            Messages.sendPrefixedKey(target, "more.success", java.util.Map.of("amount", String.valueOf(maxStack)));
        }
    }

    @Nonnull
    private static ItemStack withQuantity(@Nonnull ItemStack stack, int quantity) {
        try {
            java.lang.reflect.Method method = stack.getClass().getMethod("withQuantity", int.class);
            Object result = method.invoke(stack, quantity);
            if (result instanceof ItemStack itemStack) {
                return itemStack;
            }
        } catch (Exception ignored) {
        }
        return new ItemStack(
                stack.getItemId(),
                quantity,
                stack.getDurability(),
                stack.getMaxDurability(),
                stack.getMetadata()
        );
    }
}


