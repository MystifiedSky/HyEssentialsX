package xyz.thelegacyvoyage.hyessentialsx.commands.cheat;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;

public final class HealCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.heal";
    private static final String BYPASS_PERMISSION = "hyessentialsx.heal.bypass";
    private static final String OTHERS_PERMISSION = "hyessentialsx.heal.other";
    private final CommandCooldownManager cooldowns;

    public HealCommand(@Nonnull CommandCooldownManager cooldowns) {
        super("heal", "Restore health and stamina");
        this.cooldowns = cooldowns;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.setAllowsExtraArguments(true);
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
            Messages.noPerm(context, "/heal");
            return;
        }
        if (!cooldowns.canUse(context, playerRef, CooldownKeys.HEAL, "/heal", BYPASS_PERMISSION)) {
            return;
        }

        List<String> args = CommandInputUtil.getArgs(context);
        PlayerRef target = playerRef;
        if (!args.isEmpty()) {
            String targetName = args.get(0);
            PlayerRef found = Universe.get().getPlayerByUsername(targetName, NameMatching.EXACT_IGNORE_CASE);
            if (found == null) {
                Messages.errKey(context, "player.not_found", java.util.Map.of());
                return;
            }
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), OTHERS_PERMISSION)) {
                Messages.noPerm(context, "/heal " + targetName);
                return;
            }
            target = found;
        }

        Ref<EntityStore> targetRef = target.getReference();
        Store<EntityStore> targetStore = targetRef.getStore();
        if (targetStore == null) {
            Messages.errKey(context, "heal.stats_unavailable", java.util.Map.of());
            return;
        }
        EntityStatMap stats = targetStore.getComponent(targetRef, EntityStatMap.getComponentType());
        if (stats == null) {
            Messages.errKey(context, "heal.stats_unavailable", java.util.Map.of());
            return;
        }

        stats.maximizeStatValue(DefaultEntityStatTypes.getHealth());
        stats.maximizeStatValue(DefaultEntityStatTypes.getStamina());
        stats.update();

        cooldowns.apply(playerRef, CooldownKeys.HEAL);
        if (target.getUuid().equals(playerRef.getUuid())) {
            Messages.okKey(context, "heal.self", java.util.Map.of());
        } else {
            Messages.okKey(context, "heal.other", java.util.Map.of("player", target.getUsername()));
            Messages.sendKey(target, "heal.target", java.util.Map.of());
        }
    }
}




