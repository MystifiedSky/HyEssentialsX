package xyz.thelegacyvoyage.hyessentialsx.commands.tpa;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class TpaIgnoreCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.tpaignore";

    private final TPManager tpManager;
    private final ConfigManager config;

    public TpaIgnoreCommand(@Nonnull TPManager tpManager, @Nonnull ConfigManager config) {
        super("tpaignore", "Toggle ignoring teleport requests");
        this.tpManager = tpManager;
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
            Messages.noPerm(context, "/tpaignore");
            return;
        }
        if (!config.isTpaEnabled()) {
            Messages.err(context, "TPA is disabled.");
            return;
        }

        boolean enabled = tpManager.toggleTpaIgnore(playerRef.getUuid());
        if (enabled) {
            tpManager.removeAllTpaFor(playerRef.getUuid());
            Messages.ok(context, "Teleport requests ignored.");
        } else {
            Messages.ok(context, "Teleport requests accepted.");
        }
    }
}




