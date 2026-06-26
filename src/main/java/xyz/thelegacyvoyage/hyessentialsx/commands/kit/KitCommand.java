package xyz.thelegacyvoyage.hyessentialsx.commands.kit;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.KitManager;
import xyz.thelegacyvoyage.hyessentialsx.models.KitModel;
import xyz.thelegacyvoyage.hyessentialsx.ui.KitsUI;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.InventoryUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.List;

@SuppressWarnings("removal")
public final class KitCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.kit";
    private static final String BYPASS_PERMISSION = "hyessentialsx.kit.bypass";
    private static final String ALL_PERMISSION = "hyessentialsx.kit.all";

    private final KitManager kitManager;
    private final ConfigManager config;

    public KitCommand(@Nonnull KitManager kitManager,
                      @Nonnull ConfigManager config) {
        super("kit", "Claims a kit");
        this.kitManager = kitManager;
        this.config = config;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addSubCommand(new KitAllCommand());
        this.addUsageVariant(new ClaimKitCommand());
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
            Messages.noPerm(context, "/kit");
            return;
        }
        if (!config.isKitsEnabled()) {
            Messages.errKey(context, "kit.disabled", java.util.Map.of());
            return;
        }

        List<String> kits = kitManager.listKits();
        if (kits.isEmpty()) {
            Messages.warnKey(context, "kit.none_available", java.util.Map.of());
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
        Messages.send(context, Messages.tr(null, "kit.list", java.util.Map.of(
                "kits", String.join(", ", kits)
        )));
    }

    private final class KitAllCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;

        private KitAllCommand() {
            super("all", "Give a kit to all online players");
            this.setPermissionGroups();
            xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, ALL_PERMISSION);
            this.nameArg = withRequiredArg("name", "Kit name", ArgTypes.STRING);
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
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), ALL_PERMISSION)) {
                Messages.noPerm(context, "/kit all");
                return;
            }
            if (!config.isKitsEnabled()) {
                Messages.errKey(context, "kit.disabled", java.util.Map.of());
                return;
            }
            KitModel kit = kitManager.getKit(context.get(nameArg));
            if (kit == null) {
                Messages.errKey(context, "kit.not_found", java.util.Map.of());
                return;
            }
            int count = 0;
            for (PlayerRef target : Universe.get().getPlayers()) {
                if (target == null) continue;
                Ref<EntityStore> targetRef = target.getReference();
                Store<EntityStore> targetStore = targetRef != null ? targetRef.getStore() : null;
                if (targetRef == null || targetStore == null) continue;
                Player targetPlayer = targetStore.getComponent(targetRef, Player.getComponentType());
                if (targetPlayer == null || targetPlayer.getInventory() == null) continue;
                List<ItemStack> overflow = InventoryUtil.applyKit(targetPlayer.getInventory(), kit.getItems());
                if (!overflow.isEmpty()) {
                    dropOverflow(target, targetPlayer, overflow);
                }
                Messages.sendPrefixedKey(target, "kit.claimed", java.util.Map.of("kit", kit.getName()));
                count++;
            }
            Messages.okKey(context, "kit.given_all", java.util.Map.of(
                    "kit", kit.getName(),
                    "count", String.valueOf(count)
            ));
        }
    }

    private void claimKit(@Nonnull CommandContext context,
                          @Nonnull Store<EntityStore> store,
                          @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef playerRef,
                          String name) {
        if (name == null || name.trim().isEmpty()) {
            Messages.errKey(context, "kit.not_found", java.util.Map.of());
            return;
        }
        name = name.trim();
        KitModel kit = kitManager.getKit(name);
        if (kit == null) {
            Messages.errKey(context, "kit.not_found", java.util.Map.of());
            return;
        }

        if (config.isKitsRequirePermission()) {
            String kitPermission = "hyessentialsx.kit." + kit.getName().toLowerCase();
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), kitPermission)) {
                Messages.noPerm(context, "/kit " + kit.getName());
                return;
            }
        }

        String kitBypass = "hyessentialsx.kit." + kit.getName().toLowerCase() + ".bypass";
        boolean bypassKitCooldown = xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), BYPASS_PERMISSION)
                || xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), kitBypass);
        if (!bypassKitCooldown) {
            if (!kitManager.hasRemainingUses(playerRef.getUuid(), kit)) {
                Messages.warnKey(context, "kit.max_uses_reached", java.util.Map.of());
                return;
            }
            long remaining = kitManager.getRemainingCooldownSeconds(playerRef.getUuid(), kit);
            if (remaining > 0) {
                Messages.warnKey(context, "kit.cooldown", java.util.Map.of(
                        "time", TimeUtil.formatDurationSeconds(remaining)
                ));
                return;
            }
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.errKey(context, "error.inventory_access", java.util.Map.of());
            return;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            Messages.errKey(context, "error.inventory_access", java.util.Map.of());
            return;
        }

        List<ItemStack> overflow = InventoryUtil.applyKit(inventory, kit.getItems());
        if (!overflow.isEmpty()) {
            dropOverflow(playerRef, player, overflow);
        }
        kitManager.markUsed(playerRef.getUuid(), kit);

        Messages.okKey(context, "kit.claimed", java.util.Map.of("kit", kit.getName()));
    }

    private final class ClaimKitCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;

        private ClaimKitCommand() {
            super("Claims a named kit");
            this.nameArg = withRequiredArg("name", "Kit name", ArgTypes.STRING);
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
                Messages.noPerm(context, "/kit");
                return;
            }
            if (!config.isKitsEnabled()) {
                Messages.errKey(context, "kit.disabled", java.util.Map.of());
                return;
            }
            claimKit(context, store, ref, playerRef, context.get(nameArg));
        }
    }

    private void dropOverflow(@Nonnull PlayerRef playerRef, @Nonnull Player player, @Nonnull List<ItemStack> overflow) {
        for (ItemStack stack : overflow) {
            if (stack == null || stack.isEmpty()) continue;
            if (!tryDropItem(player, stack)) {
                // If we can't drop via API, the item is lost, so log a warning.
                Messages.send(playerRef, "kit.inventory_full");
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




