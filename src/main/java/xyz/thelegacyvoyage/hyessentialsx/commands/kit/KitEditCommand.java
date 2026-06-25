package xyz.thelegacyvoyage.hyessentialsx.commands.kit;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.KitManager;
import xyz.thelegacyvoyage.hyessentialsx.models.KitItemModel;
import xyz.thelegacyvoyage.hyessentialsx.models.KitModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;
import org.bson.BsonDocument;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class KitEditCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.kitedit";

    private final KitManager kitManager;
    private final ConfigManager config;
    private final RequiredArg<String> nameArg;

    public KitEditCommand(@Nonnull KitManager kitManager, @Nonnull ConfigManager config) {
        super("kitedit", "Edits an existing kit");
        this.kitManager = kitManager;
        this.config = config;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.nameArg = withRequiredArg("name", "Kit name", ArgTypes.STRING);
        this.addUsageVariant(new KitEditCooldownCommand());
        this.addUsageVariant(new KitEditCooldownUsesCommand());
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
            Messages.noPerm(context, "/kitedit");
            return;
        }
        if (!config.isKitsEnabled()) {
            Messages.errKey(context, "kit.disabled", Map.of());
            return;
        }

        String name = context.get(nameArg);
        if (name == null || name.isBlank()) {
            Messages.errKey(context, "kit.not_found", Map.of());
            return;
        }
        KitModel existing = kitManager.getKit(name.trim());
        if (existing == null) {
            Messages.errKey(context, "kit.not_found", Map.of());
            return;
        }

        openKitEditor(context, store, ref, playerRef, existing);
    }

    private void updateKitSettings(@Nonnull CommandContext context,
                                   @Nonnull KitModel existing,
                                   String rawCooldown,
                                   Integer maxUsesInput) {
        int cooldownSeconds = existing.getCooldownSeconds();
        if (rawCooldown != null && !rawCooldown.isBlank()) {
            long secs = TimeUtil.parseDurationSeconds(rawCooldown);
            if (secs < 0) {
                Messages.errKey(context, "kit.cooldown_invalid", Map.of());
                return;
            }
            cooldownSeconds = (int) Math.min(Integer.MAX_VALUE, secs);
        }

        int maxUses = existing.getMaxUses();
        if (maxUsesInput != null) {
            if (maxUsesInput < 0) {
                Messages.errKey(context, "kit.max_uses_invalid", Map.of());
                return;
            }
            maxUses = maxUsesInput;
        }

        KitModel updated = new KitModel(existing.getName(), cooldownSeconds, maxUses, existing.getSortOrder(), existing.getItems());
        kitManager.setKit(updated);

        Messages.okKey(context, "kit.edited", Map.of("kit", existing.getName()));
    }

    private KitModel resolveKit(@Nonnull CommandContext context, String name) {
        if (name == null || name.isBlank()) {
            Messages.errKey(context, "kit.not_found", Map.of());
            return null;
        }
        KitModel existing = kitManager.getKit(name.trim());
        if (existing == null) {
            Messages.errKey(context, "kit.not_found", Map.of());
            return null;
        }
        return existing;
    }

    private final class KitEditCooldownCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;
        private final RequiredArg<String> cooldownArg;

        private KitEditCooldownCommand() {
            super("Edits a kit cooldown");
            this.nameArg = withRequiredArg("name", "Kit name", ArgTypes.STRING);
            this.cooldownArg = withRequiredArg("cooldown", "Cooldown (e.g. 30d)", ArgTypes.STRING);
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
                Messages.noPerm(context, "/kitedit");
                return;
            }
            if (!config.isKitsEnabled()) {
                Messages.errKey(context, "kit.disabled", Map.of());
                return;
            }
            KitModel existing = resolveKit(context, context.get(nameArg));
            if (existing != null) {
                updateKitSettings(context, existing, context.get(cooldownArg), null);
            }
        }
    }

    private final class KitEditCooldownUsesCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;
        private final RequiredArg<String> cooldownArg;
        private final RequiredArg<Integer> maxUsesArg;

        private KitEditCooldownUsesCommand() {
            super("Edits a kit cooldown and max uses");
            this.nameArg = withRequiredArg("name", "Kit name", ArgTypes.STRING);
            this.cooldownArg = withRequiredArg("cooldown", "Cooldown (e.g. 30d)", ArgTypes.STRING);
            this.maxUsesArg = withRequiredArg("maxUses", "Max amount of claims (0 = unlimited)", ArgTypes.INTEGER);
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
                Messages.noPerm(context, "/kitedit");
                return;
            }
            if (!config.isKitsEnabled()) {
                Messages.errKey(context, "kit.disabled", Map.of());
                return;
            }
            KitModel existing = resolveKit(context, context.get(nameArg));
            if (existing != null) {
                updateKitSettings(context, existing, context.get(cooldownArg), context.get(maxUsesArg));
            }
        }
    }

    private void openKitEditor(@Nonnull CommandContext context,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull KitModel existing) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.errKey(context, "error.inventory_access", Map.of());
            return;
        }

        short capacity = 54;
        SimpleItemContainer container = new SimpleItemContainer(capacity);
        loadKitItemsIntoContainer(existing.getItems(), container);

        ContainerWindow window = new ContainerWindow(container);
        window.registerCloseEvent(closeEvent -> {
            List<KitItemModel> editedItems = snapshotContainer(container);
            KitModel current = kitManager.getKit(existing.getName());
            int cooldownSeconds = current != null ? current.getCooldownSeconds() : existing.getCooldownSeconds();
            int maxUses = current != null ? current.getMaxUses() : existing.getMaxUses();
            int sortOrder = current != null ? current.getSortOrder() : existing.getSortOrder();
            kitManager.setKit(new KitModel(existing.getName(), cooldownSeconds, maxUses, sortOrder, editedItems));
            Messages.sendPrefixedKey(playerRef, "kit.edited", Map.of("kit", existing.getName()));
            container.clear();
        });

        boolean opened = player.getPageManager().setPageWithWindows(
                ref,
                store,
                Page.Bench,
                true,
                new Window[]{window}
        );
        if (!opened) {
            Messages.errKey(context, "error.inventory_access", Map.of());
        }
    }

    private void loadKitItemsIntoContainer(@Nonnull List<KitItemModel> items, @Nonnull SimpleItemContainer container) {
        for (KitItemModel model : items) {
            if (model == null) continue;
            ItemStack stack = toItemStack(model);
            if (stack == null) continue;
            short slot = model.getSlot();
            if (slot >= 0 && slot < container.getCapacity()) {
                if (isEmpty(container.getItemStack(slot))) {
                    container.setItemStackForSlot(slot, stack);
                    continue;
                }
            }
            short empty = firstEmptySlot(container);
            if (empty >= 0) {
                container.setItemStackForSlot(empty, stack);
            }
        }
    }

    @Nonnull
    private List<KitItemModel> snapshotContainer(@Nonnull SimpleItemContainer container) {
        List<KitItemModel> out = new ArrayList<>();
        short cap = container.getCapacity();
        for (short i = 0; i < cap; i++) {
            ItemStack stack = container.getItemStack(i);
            if (isEmpty(stack)) continue;
            String meta = stack.getMetadata() != null ? stack.getMetadata().toJson() : null;
            out.add(new KitItemModel(
                    i,
                    stack.getItemId(),
                    stack.getQuantity(),
                    stack.getDurability(),
                    stack.getMaxDurability(),
                    meta
            ));
        }
        return out;
    }

    private short firstEmptySlot(@Nonnull SimpleItemContainer container) {
        short cap = container.getCapacity();
        for (short i = 0; i < cap; i++) {
            if (isEmpty(container.getItemStack(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    private ItemStack toItemStack(@Nonnull KitItemModel model) {
        if (model.getItemId() == null || model.getItemId().isBlank()) return null;
        BsonDocument meta = null;
        if (model.getMetadataJson() != null && !model.getMetadataJson().isBlank()) {
            try {
                meta = BsonDocument.parse(model.getMetadataJson());
            } catch (Exception ignored) {
            }
        }
        return new ItemStack(
                model.getItemId(),
                model.getQuantity(),
                model.getDurability(),
                model.getMaxDurability(),
                meta
        );
    }
}
