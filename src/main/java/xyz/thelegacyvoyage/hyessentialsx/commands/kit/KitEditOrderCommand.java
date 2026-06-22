package xyz.thelegacyvoyage.hyessentialsx.commands.kit;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.KitManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.Map;

public final class KitEditOrderCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.kiteditorder";

    private final KitManager kitManager;
    private final ConfigManager config;
    private final RequiredArg<String> nameArg;
    private final RequiredArg<Integer> positionArg;

    public KitEditOrderCommand(@Nonnull KitManager kitManager, @Nonnull ConfigManager config) {
        super("kiteditorder", "Changes the display order of a kit");
        this.kitManager = kitManager;
        this.config = config;
        this.setPermissionGroup(null);
        this.addAliases("kitorder");
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.nameArg = withRequiredArg("name", "Kit name", ArgTypes.STRING);
        this.positionArg = withRequiredArg("position", "1-based list position", ArgTypes.INTEGER);
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
            Messages.noPerm(context, "/kiteditorder");
            return;
        }
        if (!config.isKitsEnabled()) {
            Messages.errKey(context, "kit.disabled", Map.of());
            return;
        }

        String kitName = context.get(nameArg);
        Integer position = context.get(positionArg);
        if (kitName == null || kitName.isBlank()) {
            Messages.errKey(context, "kit.not_found", Map.of());
            return;
        }
        if (position == null || position < 1) {
            Messages.errKey(context, "kit.order_invalid_position", Map.of());
            return;
        }
        if (kitManager.getKit(kitName) == null) {
            Messages.errKey(context, "kit.not_found", Map.of());
            return;
        }

        if (!kitManager.reorderKit(kitName, position)) {
            Messages.errKey(context, "kit.not_found", Map.of());
            return;
        }

        Messages.okKey(context, "kit.order_updated", Map.of(
                "kit", kitName,
                "position", String.valueOf(position)
        ));
    }
}
