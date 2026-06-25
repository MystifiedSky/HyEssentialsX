package xyz.thelegacyvoyage.hyessentialsx.commands.inventory;

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
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.container.DelegateItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.Map;

@SuppressWarnings("removal")
public final class InvSeeCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_VIEW = "hyessentialsx.invsee.view";
    private static final String PERMISSION_EDIT = "hyessentialsx.invsee.edit";

    private final RequiredArg<PlayerRef> targetArg;

    public InvSeeCommand() {
        super("invsee", "View a player's inventory");
        this.setPermissionGroups();
        this.targetArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
        this.addUsageVariant(new InvSeeSectionCommand());
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
        boolean canEdit = xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_EDIT);
        boolean canView = canEdit || xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_VIEW);
        if (!canView) {
            Messages.noPerm(context, "/invsee");
            return;
        }

        Player viewer = store.getComponent(ref, Player.getComponentType());
        if (viewer == null) {
            Messages.errKey(context, "error.inventory_access", Map.of());
            return;
        }

        PlayerRef target = context.get(targetArg);
        Ref<EntityStore> targetRef = target != null ? target.getReference() : null;
        if (targetRef == null || !targetRef.isValid()) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }

        openTargetInventory(context, store, ref, viewer, target, canEdit, false);
    }

    private static void openTargetInventory(@Nonnull CommandContext context,
                                            @Nonnull Store<EntityStore> store,
                                            @Nonnull Ref<EntityStore> ref,
                                            @Nonnull Player viewer,
                                            @Nonnull PlayerRef target,
                                            boolean canEdit,
                                            boolean viewBackpack) {
        Ref<EntityStore> targetRef = target.getReference();
        if (!targetRef.isValid()) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }
        Store<EntityStore> targetStore = targetRef.getStore();
        EntityStore targetEntityStore = (EntityStore) targetStore.getExternalData();
        World targetWorld = targetEntityStore.getWorld();
        targetWorld.execute(() -> openInventory(context, viewer, ref, store, targetStore, targetRef, canEdit, viewBackpack));
    }

    private static void openInventory(
            @Nonnull CommandContext context,
            @Nonnull Player viewer,
            @Nonnull Ref<EntityStore> viewerRef,
            @Nonnull Store<EntityStore> viewerStore,
            @Nonnull Store<EntityStore> targetStore,
            @Nonnull Ref<EntityStore> targetRef,
            boolean canEdit,
            boolean viewBackpack
    ) {
        Player targetPlayer = targetStore.getComponent(targetRef, Player.getComponentType());
        if (targetPlayer == null) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }

        Inventory inventory = targetPlayer.getInventory();
        if (inventory == null) {
            Messages.errKey(context, "error.inventory_access", Map.of());
            return;
        }

        ItemContainer container = viewBackpack ? inventory.getBackpack() : inventory.getCombinedHotbarFirst();
        if (container == null) {
            Messages.errKey(context, "error.inventory_access", Map.of());
            return;
        }

        ItemContainer viewContainer = container;
        if (!canEdit) {
            DelegateItemContainer delegate = new DelegateItemContainer(container);
            delegate.setGlobalFilter(FilterType.DENY_ALL);
            viewContainer = delegate;
        }

        boolean opened = viewer.getPageManager().setPageWithWindows(
                viewerRef,
                viewerStore,
                Page.Bench,
                true,
                new Window[]{new ContainerWindow(viewContainer)}
        );
        if (!opened) {
            Messages.errKey(context, "error.inventory_access", Map.of());
        }
    }

    private static boolean isBackpackSection(String section) {
        if (section == null) return false;
        String normalized = section.trim().toLowerCase();
        if (normalized.startsWith("--")) {
            normalized = normalized.substring(2);
        }
        if (normalized.startsWith("section=")) {
            normalized = normalized.substring("section=".length());
        }
        return normalized.equals("backpack") || normalized.equals("bp") || normalized.equals("bag");
    }

    private final class InvSeeSectionCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> targetArg;
        private final RequiredArg<String> sectionArg;

        private InvSeeSectionCommand() {
            super("View a player's inventory section");
            this.targetArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
            this.sectionArg = withRequiredArg("section", "Inventory section", ArgTypes.STRING);
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
            boolean canEdit = xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_EDIT);
            boolean canView = canEdit || xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_VIEW);
            if (!canView) {
                Messages.noPerm(context, "/invsee");
                return;
            }

            Player viewer = store.getComponent(ref, Player.getComponentType());
            if (viewer == null) {
                Messages.errKey(context, "error.inventory_access", Map.of());
                return;
            }

            PlayerRef target = context.get(targetArg);
            if (target == null) {
                Messages.errKey(context, "player.not_found", Map.of());
                return;
            }

            String section = context.get(sectionArg);
            if (!isBackpackSection(section)) {
                Messages.errKey(context, "invsee.invalid_section", Map.of());
                return;
            }
            openTargetInventory(context, store, ref, viewer, target, canEdit, true);
        }
    }
}
