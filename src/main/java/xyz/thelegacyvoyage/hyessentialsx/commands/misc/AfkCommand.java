package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.AfkManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class AfkCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.afk";
    private static final String BYPASS_PERMISSION = "hyessentialsx.afk.bypass";

    private final AfkManager afk;
    private final ConfigManager config;
    private final CommandCooldownManager cooldowns;

    public AfkCommand(@Nonnull AfkManager afk, @Nonnull ConfigManager config, @Nonnull CommandCooldownManager cooldowns) {
        super("afk", "Toggle AFK status");
        this.afk = afk;
        this.config = config;
        this.cooldowns = cooldowns;
        this.setPermissionGroups();
        CommandPermissionUtil.apply(this, PERMISSION_NODE);
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
            Messages.noPerm(context, "/afk");
            return;
        }
        if (!config.isAfkEnabled()) {
            Messages.errKey(context, "afk.disabled", java.util.Map.of());
            return;
        }
        if (!cooldowns.canUse(context, playerRef, CooldownKeys.AFK, "/afk", BYPASS_PERMISSION)) {
            return;
        }

        afk.toggleAfk(playerRef);
        cooldowns.apply(playerRef, CooldownKeys.AFK);
    }
}

