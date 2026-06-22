package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import xyz.thelegacyvoyage.hyessentialsx.managers.WorldBorderManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.Map;

public final class WorldBorderCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.worldborder";

    private final WorldBorderManager worldBorderManager;

    public WorldBorderCommand(@Nonnull WorldBorderManager worldBorderManager) {
        super("worldborder", "Manage the world border");
        this.worldBorderManager = worldBorderManager;
        this.setPermissionGroups();
        CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addSubCommand(new StatusCommand());
        this.addSubCommand(new EnableCommand());
        this.addSubCommand(new DisableCommand());
        this.addSubCommand(new SetCommand());
        this.addSubCommand(new CenterCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!hasAccess(context, "/worldborder")) {
            return;
        }
        sendStatus(context);
    }

    private boolean hasAccess(@Nonnull CommandContext context, @Nonnull String command) {
        if (!CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, command);
            return false;
        }
        return true;
    }

    private void sendStatus(@Nonnull CommandContext context) {
        Messages.okKey(context, "worldborder.status", Map.of(
                "state", worldBorderManager.isEnabled() ? "enabled" : "disabled",
                "radius", String.valueOf(worldBorderManager.radius()),
                "x", String.valueOf(worldBorderManager.centerX()),
                "z", String.valueOf(worldBorderManager.centerZ())
        ));
    }

    private final class StatusCommand extends CommandBase {
        private StatusCommand() {
            super("status", "Show the world border status");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!hasAccess(context, "/worldborder status")) {
                return;
            }
            sendStatus(context);
        }
    }

    private final class EnableCommand extends CommandBase {
        private EnableCommand() {
            super("on", "Enable the world border");
            this.addAliases(new String[]{"enable"});
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!hasAccess(context, "/worldborder on")) {
                return;
            }
            worldBorderManager.setEnabled(true);
            Messages.okKey(context, "worldborder.enabled", Map.of());
            sendStatus(context);
        }
    }

    private final class DisableCommand extends CommandBase {
        private DisableCommand() {
            super("off", "Disable the world border");
            this.addAliases(new String[]{"disable"});
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!hasAccess(context, "/worldborder off")) {
                return;
            }
            worldBorderManager.setEnabled(false);
            worldBorderManager.clearWarningState();
            Messages.okKey(context, "worldborder.disabled", Map.of());
            sendStatus(context);
        }
    }

    private final class SetCommand extends CommandBase {
        private final RequiredArg<Integer> radiusArg;

        private SetCommand() {
            super("set", "Set the world border radius");
            this.radiusArg = withRequiredArg("radius", "Border radius in blocks", ArgTypes.INTEGER);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!hasAccess(context, "/worldborder set")) {
                return;
            }
            Integer radius = context.get(radiusArg);
            if (radius == null || radius < 1) {
                Messages.errKey(context, "worldborder.invalid_radius", Map.of());
                return;
            }
            worldBorderManager.setRadius(radius);
            Messages.okKey(context, "worldborder.set", Map.of("radius", String.valueOf(radius)));
        }
    }

    private final class CenterCommand extends CommandBase {
        private final RequiredArg<Integer> xArg;
        private final RequiredArg<Integer> zArg;

        private CenterCommand() {
            super("center", "Set the world border center");
            this.xArg = withRequiredArg("x", "Center X coordinate", ArgTypes.INTEGER);
            this.zArg = withRequiredArg("z", "Center Z coordinate", ArgTypes.INTEGER);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!hasAccess(context, "/worldborder center")) {
                return;
            }
            int x = context.get(xArg);
            int z = context.get(zArg);
            worldBorderManager.setCenter(x, z);
            Messages.okKey(context, "worldborder.center_set", Map.of(
                    "x", String.valueOf(x),
                    "z", String.valueOf(z)
            ));
        }
    }
}
