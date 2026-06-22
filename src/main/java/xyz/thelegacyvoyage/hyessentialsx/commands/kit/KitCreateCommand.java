package xyz.thelegacyvoyage.hyessentialsx.commands.kit;

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
import xyz.thelegacyvoyage.hyessentialsx.managers.KitManager;
import xyz.thelegacyvoyage.hyessentialsx.models.KitItemModel;
import xyz.thelegacyvoyage.hyessentialsx.models.KitModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.InventoryUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import java.util.List;

public final class KitCreateCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.kitcreate";

    private final KitManager kitManager;
    private final ConfigManager config;
    private final RequiredArg<String> nameArg;
    private final OptionalArg<String> cooldownArg;
    private final OptionalArg<Integer> maxUsesArg;

    public KitCreateCommand(@Nonnull KitManager kitManager, @Nonnull ConfigManager config) {
        super("kitcreate", "Creates a kit");
        this.kitManager = kitManager;
        this.config = config;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.nameArg = withRequiredArg("name", "Kit name", ArgTypes.STRING);
        this.cooldownArg = withOptionalArg("cooldown", "Cooldown (e.g. 30d)", ArgTypes.STRING);
        this.maxUsesArg = withOptionalArg("maxUses", "Max amount of claims (0 = unlimited)", ArgTypes.INTEGER);
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
            Messages.noPerm(context, "/kitcreate");
            return;
        }
        if (!config.isKitsEnabled()) {
            Messages.errKey(context, "kit.disabled", java.util.Map.of());
            return;
        }

        String name = context.get(nameArg);
        if (name == null || name.isBlank()) {
            Messages.errKey(context, "kit.name_required", java.util.Map.of());
            return;
        }

        int cooldownSeconds = 0;
        String raw = context.provided(cooldownArg) ? context.get(cooldownArg) : CommandInputUtil.getArg(context, 1);
        if (raw != null && !raw.isBlank()) {
            long secs = TimeUtil.parseDurationSeconds(raw);
            if (secs < 0) {
                Messages.errKey(context, "kit.cooldown_invalid", java.util.Map.of());
                return;
            }
            cooldownSeconds = (int) Math.min(Integer.MAX_VALUE, secs);
        }
        int maxUses = 0;
        Integer maxUsesInput = context.provided(maxUsesArg) ? context.get(maxUsesArg) : null;
        if (maxUsesInput == null) {
            String rawMaxUses = CommandInputUtil.getArg(context, 2);
            if (rawMaxUses != null && !rawMaxUses.isBlank()) {
                try {
                    maxUsesInput = Integer.parseInt(rawMaxUses.trim());
                } catch (NumberFormatException ignored) {
                    Messages.errKey(context, "kit.max_uses_invalid", java.util.Map.of());
                    return;
                }
            }
        }
        if (maxUsesInput != null) {
            if (maxUsesInput < 0) {
                Messages.errKey(context, "kit.max_uses_invalid", java.util.Map.of());
                return;
            }
            maxUses = maxUsesInput;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.errKey(context, "error.inventory_read", java.util.Map.of());
            return;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            Messages.errKey(context, "error.inventory_read", java.util.Map.of());
            return;
        }

        List<KitItemModel> items = InventoryUtil.snapshot(inventory);
        kitManager.setKit(new KitModel(name, cooldownSeconds, maxUses, items));

        Messages.okKey(context, "kit.created", java.util.Map.of("kit", name));
    }
}




