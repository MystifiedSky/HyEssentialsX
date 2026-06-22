package xyz.thelegacyvoyage.hyessentialsx.commands.kit;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.KitManager;
import xyz.thelegacyvoyage.hyessentialsx.ui.KitsUI;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;

public final class KitsCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.kits";

    private final KitManager kitManager;
    private final ConfigManager config;

    public KitsCommand(@Nonnull KitManager kitManager, @Nonnull ConfigManager config) {
        super("kits", "Lists all kits");
        this.kitManager = kitManager;
        this.config = config;
        this.setPermissionGroup(null);
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
            Messages.noPerm(context, "/kits");
            return;
        }
        if (!config.isKitsEnabled()) {
            Messages.err(context, "Kits are disabled.");
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

        List<String> kits = kitManager.listKits();
        if (kits.isEmpty()) {
            Messages.warn(context, "No kits available.");
            return;
        }

        Messages.send(context, "&aKits: &f" + String.join(", ", kits));
    }
}




