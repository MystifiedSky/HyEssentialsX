package xyz.thelegacyvoyage.hyessentialsx.commands.economy;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyHudManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.Map;

public final class EcoGuiCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.ecogui";

    private final EconomyManager economy;
    private final EconomyHudManager hudManager;
    private final ConfigManager config;
    private final RequiredArg<String> modeArg;

    public EcoGuiCommand(@Nonnull EconomyManager economy,
                         @Nonnull EconomyHudManager hudManager,
                         @Nonnull ConfigManager config) {
        super("ecogui", "Toggle economy HUD");
        this.economy = economy;
        this.hudManager = hudManager;
        this.config = config;
        this.setPermissionGroups();
        this.modeArg = withRequiredArg("mode", "on, off, or toggle", ArgTypes.STRING);
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
            Messages.noPerm(context, "/ecogui");
            return;
        }
        if (!economy.isEnabled() || !config.isEconomyHudEnabled()) {
            Messages.errKey(context, "economy.hud.disabled_global", Map.of());
            return;
        }

        String mode = context.get(modeArg).trim().toLowerCase(Locale.ROOT);
        boolean hidden;
        switch (mode) {
            case "on", "show" -> hidden = false;
            case "off", "hide" -> hidden = true;
            case "toggle" -> hidden = !hudManager.isPlayerHidden(playerRef.getUuid());
            default -> {
                Messages.warnKey(context, "economy.hud.usage", Map.of());
                return;
            }
        }

        hudManager.setPlayerHidden(playerRef.getUuid(), hidden);
        hudManager.refreshPlayer(playerRef);
        if (hidden) {
            Messages.okKey(context, "economy.hud.hidden", Map.of());
        } else {
            Messages.okKey(context, "economy.hud.shown", Map.of());
        }
    }
}
