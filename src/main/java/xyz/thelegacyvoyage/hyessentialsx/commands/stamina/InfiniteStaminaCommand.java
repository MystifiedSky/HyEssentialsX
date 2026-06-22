package xyz.thelegacyvoyage.hyessentialsx.commands.stamina;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.InfiniteStaminaManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class InfiniteStaminaCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.infinitestamina";
    private final InfiniteStaminaManager staminaManager;

    public InfiniteStaminaCommand(@Nonnull InfiniteStaminaManager staminaManager) {
        super("infinitestamina", "Toggle infinite stamina");
        this.staminaManager = staminaManager;
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
            Messages.noPerm(context, "/infinitestamina");
            return;
        }

        boolean enabled = staminaManager.toggle(playerRef.getUuid());
        if (enabled) {
            Messages.ok(context, "Infinite stamina enabled.");
        } else {
            Messages.ok(context, "Infinite stamina disabled.");
        }
    }
}



