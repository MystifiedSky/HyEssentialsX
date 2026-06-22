package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandSenderUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.WorldTimeUtil;

import javax.annotation.Nonnull;
import java.util.Map;

public final class DayCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.time.day";
    private static final double DAY_TIME = 0.25d;

    public DayCommand() {
        super("day", "Sets time to day");
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/day");
            return;
        }

        PlayerRef player = CommandSenderUtil.resolvePlayer(context);
        if (player == null) {
            Messages.errKey(context, "time.player_only", Map.of());
            return;
        }

        World world = player.getWorldUuid() != null ? com.hypixel.hytale.server.core.universe.Universe.get().getWorld(player.getWorldUuid()) : null;
        if (world == null) {
            Messages.errKey(context, "time.not_supported", Map.of());
            return;
        }

        if (!WorldTimeUtil.setDayTimeFraction(world, DAY_TIME)) {
            boolean ran = tryRunTimeCommand(player, "time set day");
            if (ran || WorldTimeUtil.isDay(world)) {
                Messages.okKey(context, "time.day", Map.of());
                return;
            }
            Messages.errKey(context, "time.not_supported", Map.of());
            return;
        }

        Messages.okKey(context, "time.day", Map.of());
    }

    private boolean tryRunTimeCommand(@Nonnull PlayerRef playerRef, @Nonnull String command) {
        try {
            Object manager = resolveCommandManager();
            if (manager == null) return false;
            java.lang.reflect.Method handle = manager.getClass().getMethod("handleCommand", PlayerRef.class, String.class);
            handle.invoke(manager, playerRef, command);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Object resolveCommandManager() {
        try {
            Class<?> cls = Class.forName("com.hypixel.hytale.server.core.command.system.CommandManager");
            java.lang.reflect.Method get = cls.getMethod("get");
            return get.invoke(null);
        } catch (Throwable ignored) {
        }
        try {
            Class<?> cls = Class.forName("com.hypixel.hytale.server.core.command.CommandManager");
            java.lang.reflect.Method get = cls.getMethod("get");
            return get.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}

