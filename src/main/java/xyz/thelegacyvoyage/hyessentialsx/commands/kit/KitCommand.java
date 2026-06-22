package xyz.thelegacyvoyage.hyessentialsx.commands.kit;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.KitManager;
import xyz.thelegacyvoyage.hyessentialsx.models.KitModel;
import xyz.thelegacyvoyage.hyessentialsx.ui.KitsUI;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.InventoryUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.List;

public final class KitCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.kit";
    private static final String BYPASS_PERMISSION = "hyessentialsx.kit.bypass";

    private final KitManager kitManager;
    private final ConfigManager config;
    public KitCommand(@Nonnull KitManager kitManager,
                      @Nonnull ConfigManager config) {
        super("kit", "Claims a kit");
        this.kitManager = kitManager;
        this.config = config;
        this.setPermissionGroup(null);
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
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
            Messages.noPerm(context, "/kit");
            return;
        }
        if (!config.isKitsEnabled()) {
            Messages.err(context, "Kits are disabled.");
            return;
        }

        List<String> args = CommandInputUtil.getArgs(context);
        if (args.isEmpty()) {
            List<String> kits = kitManager.listKits();
            if (kits.isEmpty()) {
                Messages.warn(context, "No kits available.");
                return;
            }
            if (config.isKitsGuiEnabled()) {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) {
                    Messages.errKey(context, "kit.ui_failed", java.util.Map.of());
                    return;
                }
                KitsUI page = new KitsUI(playerRef, kitManager, config);
                page.open(player, ref, store);
                return;
            }
            Messages.send(context, "&aKits: &f" + String.join(", ", kits));
            return;
        }

        String name = args.get(0);
        if (name == null || name.trim().isEmpty()) {
            Messages.err(context, "Kit not found.");
            return;
        }
        name = name.trim();
        KitModel kit = kitManager.getKit(name);
        if (kit == null) {
            Messages.err(context, "Kit not found.");
            return;
        }

        if (config.isKitsRequirePermission()) {
            String kitPermission = "hyessentialsx.kit." + kit.getName().toLowerCase();
            if (!context.sender().hasPermission(kitPermission)) {
                Messages.noPerm(context, "/kit " + kit.getName());
                return;
            }
        }

        String kitBypass = "hyessentialsx.kit." + kit.getName().toLowerCase() + ".bypass";
        boolean bypassKitCooldown = context.sender().hasPermission(BYPASS_PERMISSION)
                || context.sender().hasPermission(kitBypass);
        if (!bypassKitCooldown) {
            long remaining = kitManager.getRemainingCooldownSeconds(playerRef.getUuid(), kit);
            if (remaining > 0) {
                Messages.warn(context, "You must wait " + TimeUtil.formatDurationSeconds(remaining) + " to use this kit again.");
                return;
            }
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

        List<ItemStack> overflow = InventoryUtil.applyKit(inventory, kit.getItems());
        if (!overflow.isEmpty()) {
            dropOverflow(playerRef, player, overflow);
        }
        kitManager.markUsed(playerRef.getUuid(), kit);

        Messages.ok(context, "Kit '&f" + kit.getName() + "&a' claimed.");
    }

    private void dropOverflow(@Nonnull PlayerRef playerRef, @Nonnull Player player, @Nonnull List<ItemStack> overflow) {
        for (ItemStack stack : overflow) {
            if (stack == null || stack.isEmpty()) continue;
            if (!tryDropItem(player, stack)) {
                // If we can't drop via API, the item is lost, so log a warning.
                Messages.send(playerRef, "&cInventory full. Some kit items could not be delivered.");
                return;
            }
        }
    }

    private boolean tryDropItem(@Nonnull Player player, @Nonnull ItemStack stack) {
        String[] methods = {"dropItemStack", "dropItem", "dropItemAt", "spawnItemStack"};
        for (String name : methods) {
            for (Method method : player.getClass().getMethods()) {
                if (!method.getName().equals(name)) continue;
                if (method.getParameterCount() != 1) continue;
                if (!method.getParameterTypes()[0].isAssignableFrom(ItemStack.class)) continue;
                try {
                    method.invoke(player, stack);
                    return true;
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }
}



