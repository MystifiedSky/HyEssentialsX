package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.ui.RulesUI;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.PlaceholderApiUtil;

import javax.annotation.Nonnull;
import java.util.List;

public final class RulesCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.rules";

    private final ConfigManager config;

    public RulesCommand(@Nonnull ConfigManager config) {
        super("rules", "Displays server rules");
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
            Messages.noPerm(context, "/rules");
            return;
        }
        if (!config.isRulesEnabled()) {
            Messages.err(context, "Rules are disabled.");
            return;
        }

        List<String> rules = config.getRules();
        if (rules.isEmpty()) {
            context.sendMessage(Messages.m("&7No rules set."));
            return;
        }

        if (config.isRulesGuiEnabled()) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                Messages.errKey(context, "rules.ui_failed", java.util.Map.of());
                return;
            }
            RulesUI page = new RulesUI(playerRef, config);
            page.open(player, ref, store);
            return;
        }

        context.sendMessage(Messages.m("&aRules:"));
        for (String rule : rules) {
            context.sendMessage(PlaceholderApiUtil.apply(playerRef, rule, config));
        }
    }
}
