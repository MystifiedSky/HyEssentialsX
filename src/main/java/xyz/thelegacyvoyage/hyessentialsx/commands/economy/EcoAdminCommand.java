package xyz.thelegacyvoyage.hyessentialsx.commands.economy;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyAuditManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyHudManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.ui.economy.EcoAdminUI;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.Map;

public final class EcoAdminCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.ecoadmin";

    private final EconomyManager economy;
    private final StorageManager storage;
    private final ConfigManager config;
    private final EconomyHudManager hudManager;
    private final EconomyAuditManager audit;

    public EcoAdminCommand(@Nonnull EconomyManager economy,
                           @Nonnull StorageManager storage,
                           @Nonnull ConfigManager config,
                           @Nonnull EconomyHudManager hudManager,
                           @Nonnull EconomyAuditManager audit) {
        super("ecoadmin", "Open the economy admin panel");
        this.economy = economy;
        this.storage = storage;
        this.config = config;
        this.hudManager = hudManager;
        this.audit = audit;
        this.setPermissionGroups();
        CommandPermissionUtil.apply(this, PERMISSION_NODE);
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
            Messages.noPerm(context, "/ecoadmin");
            return;
        }
        if (!economy.isEnabled()) {
            Messages.errKey(context, "economy.disabled", Map.of());
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.errKey(context, "error.player_only", Map.of());
            return;
        }
        EcoAdminUI ui = new EcoAdminUI(playerRef, economy, storage, config, hudManager, audit);
        ui.open(player, ref, store);
    }
}
