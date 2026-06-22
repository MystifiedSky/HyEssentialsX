package xyz.thelegacyvoyage.hyessentialsx.commands.cheat;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.FlyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public final class FlySpeedCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.flyspeed";
    private static final String OTHERS_PERMISSION = "hyessentialsx.flyspeed.others";

    private final FlyManager flyManager;
    private final StorageManager storage;
    private final ConfigManager config;

    public FlySpeedCommand(@Nonnull FlyManager flyManager,
                           @Nonnull StorageManager storage,
                           @Nonnull ConfigManager config) {
        super("flyspeed", "Set fly speed multiplier");
        this.flyManager = flyManager;
        this.storage = storage;
        this.config = config;
        this.setPermissionGroup(null);
        this.setAllowsExtraArguments(true);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
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
            Messages.noPerm(context, "/flyspeed");
            return;
        }

        List<String> args = CommandInputUtil.getArgs(context);
        if (args.isEmpty()) {
            float current = flyManager.getFlySpeedMultiplier(playerRef.getUuid());
            Messages.sendKey(context, "flyspeed.current", Map.of("speed", formatSpeed(current)));
            return;
        }

        float requested;
        try {
            requested = Float.parseFloat(args.get(0));
        } catch (NumberFormatException ex) {
            Messages.errKey(context, "flyspeed.invalid", Map.of());
            return;
        }
        if (!Float.isFinite(requested)) {
            Messages.errKey(context, "flyspeed.invalid", Map.of());
            return;
        }

        float min = (float) config.getFlySpeedMin();
        float max = (float) config.getFlySpeedMax();
        if (requested < min || requested > max) {
            Messages.errKey(context, "flyspeed.range", Map.of(
                    "min", formatSpeed(min),
                    "max", formatSpeed(max)
            ));
            return;
        }

        PlayerRef target = playerRef;
        if (args.size() >= 2) {
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), OTHERS_PERMISSION)) {
                Messages.noPerm(context, "/flyspeed <speed> <player>");
                return;
            }
            PlayerRef found = Universe.get().getPlayerByUsername(args.get(1), NameMatching.EXACT_IGNORE_CASE);
            if (found == null) {
                Messages.errKey(context, "player.not_found", Map.of());
                return;
            }
            target = found;
        }

        flyManager.setFlySpeedMultiplier(target.getUuid(), requested);
        var data = storage.getPlayerData(target.getUuid());
        data.setFlySpeedMultiplier(requested);
        storage.savePlayerDataAsync(target.getUuid(), data);

        if (flyManager.isEnabled(target.getUuid())) {
            flyManager.applyState(target, true);
        } else {
            flyManager.applySpeedOnly(target);
        }

        if (target.getUuid().equals(playerRef.getUuid())) {
            Messages.okKey(context, "flyspeed.set_self", Map.of("speed", formatSpeed(requested)));
        } else {
            Messages.okKey(context, "flyspeed.set_other", Map.of(
                    "player", target.getUsername(),
                    "speed", formatSpeed(requested)
            ));
            Messages.sendPrefixedKey(target, "flyspeed.changed_by", Map.of(
                    "player", playerRef.getUsername(),
                    "speed", formatSpeed(requested)
            ));
        }
    }

    @Nonnull
    private static String formatSpeed(float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
