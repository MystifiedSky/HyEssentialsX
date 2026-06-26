package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import xyz.thelegacyvoyage.hyessentialsx.HyEssentialsXPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class CommandPermissionUtil {
    private static final String HYTALE_ADMIN_GROUP = "hytale:Admin";

    private static final List<String> PLAYER_PERMISSION_EXACT = List.of(
            "hyessentialsx.spawn",
            "hyessentialsx.home",
            "hyessentialsx.homes",
            "hyessentialsx.sethome",
            "hyessentialsx.delhome",
            "hyessentialsx.warp",
            "hyessentialsx.warps",
            "hyessentialsx.kit",
            "hyessentialsx.kits",
            "hyessentialsx.msg",
            "hyessentialsx.ignore",
            "hyessentialsx.mail",
            "hyessentialsx.nick",
            "hyessentialsx.pay",
            "hyessentialsx.balance",
            "hyessentialsx.baltop",
            "hyessentialsx.ecogui",
            "hyessentialsx.tpa",
            "hyessentialsx.tpahere",
            "hyessentialsx.tpaaccept",
            "hyessentialsx.tpadeny",
            "hyessentialsx.tpacancel",
            "hyessentialsx.tpaignore",
            "hyessentialsx.back",
            "hyessentialsx.list",
            "hyessentialsx.rules",
            "hyessentialsx.motd",
            "hyessentialsx.discord",
            "hyessentialsx.near",
            "hyessentialsx.afk",
            "hyessentialsx.seen",
            "hyessentialsx.playtime",
            "hyessentialsx.playtime.other",
            "hyessentialsx.rankup",
            "hyessentialsx.rtp",
            "hyessentialsx.trash",
            "hyessentialsx.help",
            "hyessentialsx.info",
            "hyessentialsx.language",
            "hyessentialsx.scoreboard.move",
            "hyessentialsx.scoreboard.reset",
            "hyessentialsx.scoreboard.show",
            "hyessentialsx.scoreboard.hide",
            "hyessentialsx.adminshop.use",
            "hyessentialsx.shop",
            "hyessentialsx.playershop",
            "hyessentialsx.playershop.use",
            "hyessentialsx.playershop.create",
            "hyessentialsx.playershop.delete",
            "hyessentialsx.auctionhouse.use",
            "hyessentialsx.auctionhouse.sell"
    );

    private static final List<String> PLAYER_PERMISSION_PREFIXES = List.of(
            "hyessentialsx.kit.",
            "hyessentialsx.warp."
    );

    private static final List<String> ADMIN_PERMISSION_PREFIXES = List.of(
            "hyessentialsx.fly",
            "hyessentialsx.flyspeed",
            "hyessentialsx.god",
            "hyessentialsx.heal",
            "hyessentialsx.infinitestamina",
            "hyessentialsx.top",
            "hyessentialsx.jumpto",
            "hyessentialsx.thru",
            "hyessentialsx.freecam",
            "hyessentialsx.vanish",
            "hyessentialsx.more",
            "hyessentialsx.repair",
            "hyessentialsx.time.day",
            "hyessentialsx.time.night",
            "hyessentialsx.setspawn",
            "hyessentialsx.delspawn",
            "hyessentialsx.spawn.other",
            "hyessentialsx.spawn.all",
            "hyessentialsx.tphere",
            "hyessentialsx.tpahereall",
            "hyessentialsx.ecoadmin",
            "hyessentialsx.money.admin",
            "hyessentialsx.money.set",
            "hyessentialsx.money.give",
            "hyessentialsx.money.take",
            "hyessentialsx.money.reset",
            "hyessentialsx.balance.others",
            "hyessentialsx.adminchat",
            "hyessentialsx.broadcast",
            "hyessentialsx.announcement.admin",
            "hyessentialsx.clearchat",
            "hyessentialsx.socialspy",
            "hyessentialsx.kitcreate",
            "hyessentialsx.kitedit",
            "hyessentialsx.kiteditorder",
            "hyessentialsx.kitdelete",
            "hyessentialsx.setwarp",
            "hyessentialsx.delwarp",
            "hyessentialsx.freeze",
            "hyessentialsx.unfreeze",
            "hyessentialsx.mute",
            "hyessentialsx.unmute",
            "hyessentialsx.ban",
            "hyessentialsx.tempban",
            "hyessentialsx.unban",
            "hyessentialsx.warn",
            "hyessentialsx.warnings",
            "hyessentialsx.clearwarnings",
            "hyessentialsx.warnrules",
            "hyessentialsx.ipban",
            "hyessentialsx.unipban",
            "hyessentialsx.banlist",
            "hyessentialsx.whois",
            "hyessentialsx.iphistory",
            "hyessentialsx.sleeppercent",
            "hyessentialsx.scoreboard.edit",
            "hyessentialsx.scoreboard.adminmove",
            "hyessentialsx.scoreboard.reload",
            "hyessentialsx.adminshop.admin",
            "hyessentialsx.adminshop.npc",
            "hyessentialsx.shop.admin",
            "hyessentialsx.shop.npc",
            "hyessentialsx.playershop.admin",
            "hyessentialsx.playershop.npc",
            "hyessentialsx.auctionhouse.admin",
            "hyessentialsx.playtime.admin",
            "hyessentialsx.reload",
            "hyessentialsx.ui",
            "hyessentialsx.import",
            "hyessentialsx.migrate",
            "hyessentialsx.clearinventory",
            "hyessentialsx.invsee",
            "hyessentialsx.combatlog",
            "hyessentialsx.hologram",
            "hyessentialsx.auctionhouse.listings",
            "hyessentialsx.auctionhouse.duration",
            "hologramservice.",
            "hyessentialsx.back.other",
            "hyessentialsx.warp.other",
            "hyessentialsx.home.other",
            "hyessentialsx.mail.sendall",
            "hyessentialsx.rtp.other"
    );

    private CommandPermissionUtil() {}

    public static void apply(@Nonnull Object command, @Nonnull String permission) {
        if (permission.isBlank()) return;
        if (!shouldHideNoPermissionCommands()) return;

        applyOperatorPermissionGroup(command);

        String[] methods = {"requirePermission", "setPermission", "setRequiredPermission", "setPermissionNode"};
        Class<?> type = command.getClass();
        while (type != null) {
            for (String name : methods) {
                try {
                    Method method = type.getDeclaredMethod(name, String.class);
                    method.setAccessible(true);
                    method.invoke(command, permission);
                    return;
                } catch (Exception ignored) {
                }
            }
            type = type.getSuperclass();
        }
    }

    public static boolean hasPermission(@Nullable Object sender, @Nonnull String permission) {
        String trimmed = permission.trim();
        if (trimmed.isBlank()) return true;

        if (isPermissionsSystemEnabled()) {
            return hasRawPermission(sender, trimmed) || isOperator(sender);
        }
        if (isAdminPermission(trimmed)) {
            return isOperator(sender);
        }
        if (isPlayerPermission(trimmed)) {
            return true;
        }
        return isOperator(sender);
    }

    public static boolean hasPermission(@Nullable PlayerRef playerRef, @Nonnull String permission) {
        String trimmed = permission.trim();
        if (trimmed.isBlank()) return true;

        if (isPermissionsSystemEnabled()) {
            return hasModulePermission(playerRef, trimmed) || isOperator(playerRef);
        }
        if (isAdminPermission(trimmed)) {
            return isOperator(playerRef);
        }
        if (isPlayerPermission(trimmed)) {
            return true;
        }
        return isOperator(playerRef);
    }

    public static boolean isPermissionsSystemEnabled() {
        try {
            HyEssentialsXPlugin plugin = HyEssentialsXPlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                return true;
            }
            return plugin.getConfigManager().isUsePermissionsSystem();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static void applyOperatorPermissionGroup(@Nonnull Object command) {
        Class<?> type = command.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod("setPermissionGroups", String[].class);
                method.setAccessible(true);
                method.invoke(command, (Object) new String[]{HYTALE_ADMIN_GROUP});
                return;
            } catch (Exception ignored) {
            }
            type = type.getSuperclass();
        }
    }

    public static boolean shouldHideNoPermissionCommands() {
        try {
            HyEssentialsXPlugin plugin = HyEssentialsXPlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                return false;
            }
            ConfigManager config = plugin.getConfigManager();
            return config.isUsePermissionsSystem() && config.isHideNoPermissionCommands();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isAdminPermission(@Nonnull String permission) {
        String normalized = normalize(permission);
        for (String prefix : ADMIN_PERMISSION_PREFIXES) {
            if (normalized.equals(prefix) || normalized.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPlayerPermission(@Nonnull String permission) {
        String normalized = normalize(permission);
        if (PLAYER_PERMISSION_EXACT.contains(normalized)) {
            return true;
        }
        for (String prefix : PLAYER_PERMISSION_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOperator(@Nullable Object sender) {
        if (sender == null) {
            return false;
        }

        if (sender instanceof PlayerRef ref) {
            return hasModulePermission(ref, "hytale.op")
                    || hasModulePermission(ref, "hytale.operator")
                    || hasModulePermission(ref, "*")
                    || hasHytaleAdminGroup(ref.getUuid());
        }

        if (hasRawPermission(sender, "hytale.op")
                || hasRawPermission(sender, "hytale.operator")
                || hasRawPermission(sender, "*")) {
            return true;
        }

        if (sender instanceof CommandSender commandSender && hasHytaleAdminGroup(commandSender.getUuid())) {
            return true;
        }

        for (String methodName : List.of("isOp", "isOperator", "isServerOperator")) {
            try {
                Method method = sender.getClass().getMethod(methodName);
                Object result = method.invoke(sender);
                if (result instanceof Boolean bool && bool) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static boolean hasRawPermission(@Nullable Object sender, @Nonnull String permission) {
        if (sender == null) {
            return false;
        }
        if (sender instanceof PlayerRef ref) {
            return hasModulePermission(ref, permission);
        }
        try {
            Method method = sender.getClass().getMethod("hasPermission", String.class);
            Object result = method.invoke(sender, permission);
            return result instanceof Boolean bool && bool;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean hasHytaleAdminGroup(@Nullable UUID uuid) {
        if (uuid == null) {
            return false;
        }
        try {
            for (String group : PermissionsModule.get().getGroupsForUser(uuid)) {
                if (group != null && HYTALE_ADMIN_GROUP.equalsIgnoreCase(group.trim())) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean hasModulePermission(@Nullable PlayerRef playerRef, @Nonnull String permission) {
        if (playerRef == null) {
            return false;
        }
        try {
            return PermissionsModule.get().hasPermission(playerRef.getUuid(), permission, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Nonnull
    private static String normalize(@Nonnull String permission) {
        return permission.trim().toLowerCase(Locale.ROOT);
    }
}

