package xyz.thelegacyvoyage.hyessentialsx.commands.inventory;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TrashCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.trash";

    public TrashCommand() {
        super("trash", "Open a disposable container");
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"disposal"});
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
            Messages.noPerm(context, "/trash");
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.errKey(context, "trash.open_failed", java.util.Map.of());
            return;
        }

        SimpleItemContainer container = new SimpleItemContainer((short) 9);
        AtomicBoolean clearing = new AtomicBoolean(false);
        container.registerChangeEvent(event -> {
            if (clearing.get()) return;
            clearing.set(true);
            try {
                for (short slot = 0; slot < container.getCapacity(); slot++) {
                    if (!com.hypixel.hytale.server.core.inventory.ItemStack.isEmpty(container.getItemStack(slot))) {
                        container.removeItemStackFromSlot(slot);
                    }
                }
            } finally {
                clearing.set(false);
            }
        });

        ContainerWindow window = new ContainerWindow(container);
        window.registerCloseEvent(closeEvent -> container.clear());

        boolean opened = player.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, new Window[]{window});
        if (!opened) {
            Messages.errKey(context, "trash.open_failed", java.util.Map.of());
        }
    }
}

