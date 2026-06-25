package xyz.thelegacyvoyage.hyessentialsx.commands.spawn;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import xyz.thelegacyvoyage.hyessentialsx.managers.SpawnManager;
import xyz.thelegacyvoyage.hyessentialsx.models.SpawnRouteGroupModel;
import xyz.thelegacyvoyage.hyessentialsx.models.SpawnModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SpawnRouteCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.spawnroute";

    private final SpawnManager spawnManager;
    private final ConfigManager config;

    public SpawnRouteCommand(@Nonnull SpawnManager spawnManager, @Nonnull ConfigManager config) {
        super("spawnroute", "Manage flexible spawn routing");
        this.spawnManager = spawnManager;
        this.config = config;
        this.setPermissionGroups();
        this.addAliases(new String[]{"spawnrouting", "spawnroutes"});
        this.addSubCommand(new ListSubCommand());
        this.addSubCommand(new ModeSubCommand());
        this.addSubCommand(new FirstJoinSubCommand());
        this.addSubCommand(new DeathSubCommand());
        this.addSubCommand(new WorldSubCommand());
        this.addSubCommand(new GroupSubCommand());
        this.addSubCommand(new GroupDeleteSubCommand());
        this.addSubCommand(new OrderSubCommand());
        CommandPermissionUtil.apply(this, PERMISSION_NODE);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        listRoutes(context);
    }

    private boolean canManage(@Nonnull CommandContext context, @Nonnull String usage) {
        if (!CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, usage);
            return false;
        }
        if (!config.isSpawnEnabled()) {
            Messages.errKey(context, "spawn.disabled", Map.of());
            return false;
        }
        return true;
    }

    private final class ListSubCommand extends CommandBase {
        private ListSubCommand() {
            super("list", "List spawn routing configuration");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            listRoutes(context);
        }
    }

    private final class ModeSubCommand extends CommandBase {
        private final RequiredArg<String> modeArg;

        private ModeSubCommand() {
            super("mode", "Set multi-spawn selection mode");
            this.modeArg = withRequiredArg("mode", "first, random, or nearest", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(context, "/spawnroute mode")) return;
            String mode = context.get(modeArg).trim().toLowerCase(Locale.ROOT);
            if (!List.of("first", "random", "nearest").contains(mode)) {
                Messages.errKey(context, "spawnroute.invalid_mode", Map.of());
                return;
            }
            config.setSpawnRouteSelectionMode(mode);
            Messages.okKey(context, "spawnroute.mode_set", Map.of("mode", mode));
        }
    }

    private final class FirstJoinSubCommand extends CommandBase {
        private final RequiredArg<String> spawnArg;

        private FirstJoinSubCommand() {
            super("firstjoin", "Route first joins to a named spawn");
            this.spawnArg = withRequiredArg("spawn", "Named spawn, or none", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(context, "/spawnroute firstjoin")) return;
            setSingleNamedRoute(context, context.get(spawnArg), true);
        }
    }

    private final class DeathSubCommand extends CommandBase {
        private final RequiredArg<String> spawnArg;

        private DeathSubCommand() {
            super("death", "Route death/respawn to a named spawn source");
            this.spawnArg = withRequiredArg("spawn", "Named spawn, or none", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(context, "/spawnroute death")) return;
            setSingleNamedRoute(context, context.get(spawnArg), false);
        }
    }

    private final class WorldSubCommand extends CommandBase {
        private final RequiredArg<String> worldArg;
        private final RequiredArg<String> spawnsArg;

        private WorldSubCommand() {
            super("world", "Route a world to one or more named spawns");
            this.worldArg = withRequiredArg("world", "World route key", ArgTypes.STRING);
            this.spawnsArg = withRequiredArg("spawns", "Comma-separated named spawns, or none", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(context, "/spawnroute world")) return;
            String world = context.get(worldArg).trim();
            List<String> spawns = parseSpawnList(context.get(spawnsArg));
            if (isNone(context.get(spawnsArg))) {
                config.setWorldSpawnRoute(world, List.of());
                Messages.okKey(context, "spawnroute.world_cleared", Map.of("world", world));
                return;
            }
            if (!validateSpawnList(context, spawns)) return;
            config.setWorldSpawnRoute(world, spawns);
            Messages.okKey(context, "spawnroute.world_set", Map.of("world", world, "spawns", String.join(", ", spawns)));
        }
    }

    private final class GroupSubCommand extends CommandBase {
        private final RequiredArg<String> idArg;
        private final RequiredArg<String> permissionArg;
        private final RequiredArg<Integer> priorityArg;
        private final RequiredArg<String> spawnsArg;

        private GroupSubCommand() {
            super("group", "Route a permission group to one or more named spawns");
            this.idArg = withRequiredArg("id", "Route ID", ArgTypes.STRING);
            this.permissionArg = withRequiredArg("permission", "Permission node", ArgTypes.STRING);
            this.priorityArg = withRequiredArg("priority", "Higher wins", ArgTypes.INTEGER);
            this.spawnsArg = withRequiredArg("spawns", "Comma-separated named spawns", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(context, "/spawnroute group")) return;
            String id = normalizeRouteId(context.get(idArg));
            String permission = context.get(permissionArg).trim();
            List<String> spawns = parseSpawnList(context.get(spawnsArg));
            if (id == null || permission.isBlank() || spawns.isEmpty()) {
                Messages.errKey(context, "spawnroute.invalid_group", Map.of());
                return;
            }
            if (!validateSpawnList(context, spawns)) return;
            config.setGroupSpawnRoute(new SpawnRouteGroupModel(id, permission, context.get(priorityArg), spawns));
            Messages.okKey(context, "spawnroute.group_set", Map.of("id", id, "permission", permission, "spawns", String.join(", ", spawns)));
        }
    }

    private final class GroupDeleteSubCommand extends CommandBase {
        private final RequiredArg<String> idArg;

        private GroupDeleteSubCommand() {
            super("groupdel", "Delete a permission group spawn route");
            this.addAliases(new String[]{"groupdelete", "groupremove"});
            this.idArg = withRequiredArg("id", "Route ID", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(context, "/spawnroute groupdel")) return;
            String id = context.get(idArg);
            if (!config.clearGroupSpawnRoute(id)) {
                Messages.errKey(context, "spawnroute.group_not_found", Map.of("id", id));
                return;
            }
            Messages.okKey(context, "spawnroute.group_deleted", Map.of("id", id));
        }
    }

    private final class OrderSubCommand extends CommandBase {
        private final RequiredArg<String> routeArg;

        private OrderSubCommand() {
            super("order", "Set a route resolution order");
            this.routeArg = withRequiredArg("route", "command, firstjoin, join, respawn, or death", ArgTypes.STRING);
            this.addUsageVariant(new OrderSetVariant());
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(context, "/spawnroute order")) return;
            String route = context.get(routeArg);
            Messages.sendKey(context, "spawnroute.order_entry", Map.of(
                    "route", route,
                    "order", String.join(", ", config.getSpawnRouteOrder(route))
            ));
        }

        private final class OrderSetVariant extends CommandBase {
            private final RequiredArg<String> routeArg;
            private final RequiredArg<List<String>> orderArg;

            private OrderSetVariant() {
                super("Set a route resolution order");
                this.routeArg = withRequiredArg("route", "command, firstjoin, join, respawn, or death", ArgTypes.STRING);
                this.orderArg = withListRequiredArg("sources", "Sources: bed, firstjoin, death, group, world, permission, main, setspawn, worlddefault", ArgTypes.STRING);
            }

            @Override
            protected boolean canGeneratePermission() {
                return false;
            }

            @Override
            protected void executeSync(@Nonnull CommandContext context) {
                if (!canManage(context, "/spawnroute order")) return;
                String route = context.get(routeArg);
                List<String> order = new ArrayList<>();
                for (String raw : context.get(orderArg)) {
                    order.addAll(parseSpawnList(raw));
                }
                config.setSpawnRouteOrder(route, order);
                Messages.okKey(context, "spawnroute.order_set", Map.of(
                        "route", route,
                        "order", String.join(", ", config.getSpawnRouteOrder(route))
                ));
            }
        }
    }

    private void setSingleNamedRoute(@Nonnull CommandContext context, @Nonnull String rawName, boolean firstJoin) {
        String key = SpawnManager.normalizeSpawnName(rawName);
        if (isNone(rawName)) {
            if (firstJoin) {
                config.setFirstJoinSpawnName("");
            } else {
                config.setDeathSpawnName("");
            }
            Messages.okKey(context, firstJoin ? "spawnroute.firstjoin_cleared" : "spawnroute.death_cleared", Map.of());
            return;
        }
        if (key == null || !spawnManager.hasNamedSpawn(key)) {
            Messages.errKey(context, "spawnroute.named_missing", Map.of("spawn", rawName));
            return;
        }
        if (firstJoin) {
            config.setFirstJoinSpawnName(key);
        } else {
            config.setDeathSpawnName(key);
        }
        Messages.okKey(context, firstJoin ? "spawnroute.firstjoin_set" : "spawnroute.death_set", Map.of("spawn", key));
    }

    private void listRoutes(@Nonnull CommandContext context) {
        if (!canManage(context, "/spawnroute")) return;
        Messages.sendKey(context, "spawnroute.header", Map.of("mode", config.getSpawnRouteSelectionMode()));
        SpawnModel main = config.getSpawn();
        if (main == null) {
            Messages.sendKey(context, "spawnroute.main_none", Map.of());
        } else {
            Messages.sendKey(context, "spawnroute.main_entry", spawnPlaceholders("Main", "hyessentialsx.spawn", main));
        }
        if (config.getNamedSpawns().isEmpty()) {
            Messages.sendKey(context, "spawnroute.named_none", Map.of());
        } else {
            for (Map.Entry<String, SpawnModel> entry : config.getNamedSpawns().entrySet()) {
                Map<String, String> placeholders = spawnPlaceholders(entry.getKey(), "hyessentialsx.spawn." + entry.getKey(), entry.getValue());
                Messages.sendKey(context, "spawnroute.named_entry", placeholders);
            }
        }
        Messages.sendKey(context, "spawnroute.single", Map.of("label", "First join", "spawn", valueOrNone(config.getFirstJoinSpawnName())));
        Messages.sendKey(context, "spawnroute.single", Map.of("label", "Death", "spawn", valueOrNone(config.getDeathSpawnName())));
        for (String route : List.of("command", "firstjoin", "join", "respawn", "death")) {
            Messages.sendKey(context, "spawnroute.order_entry", Map.of(
                    "route", route,
                    "order", valueOrNone(String.join(", ", config.getSpawnRouteOrder(route)))
            ));
        }
        if (config.getWorldSpawnRoutes().isEmpty()) {
            Messages.sendKey(context, "spawnroute.world_none", Map.of());
        } else {
            for (Map.Entry<String, List<String>> entry : config.getWorldSpawnRoutes().entrySet()) {
                Messages.sendKey(context, "spawnroute.world_entry", Map.of("world", entry.getKey(), "spawns", String.join(", ", entry.getValue())));
            }
        }
        if (config.getGroupSpawnRoutes().isEmpty()) {
            Messages.sendKey(context, "spawnroute.group_none", Map.of());
        } else {
            for (SpawnRouteGroupModel group : config.getGroupSpawnRoutes()) {
                Messages.sendKey(context, "spawnroute.group_entry", Map.of(
                        "id", group.getId(),
                        "permission", group.getPermission(),
                        "priority", String.valueOf(group.getPriority()),
                        "spawns", String.join(", ", group.getSpawns())
                ));
            }
        }
    }

    private boolean validateSpawnList(@Nonnull CommandContext context, @Nonnull List<String> spawns) {
        if (spawns.isEmpty()) {
            Messages.errKey(context, "spawnroute.invalid_spawns", Map.of());
            return false;
        }
        for (String spawn : spawns) {
            if (!spawnManager.hasNamedSpawn(spawn)) {
                Messages.errKey(context, "spawnroute.named_missing", Map.of("spawn", spawn));
                return false;
            }
        }
        return true;
    }

    @Nonnull
    private Map<String, String> spawnPlaceholders(@Nonnull String name,
                                                  @Nonnull String permission,
                                                  @Nonnull SpawnModel spawn) {
        return Map.of(
                "name", name,
                "permission", permission,
                "world", spawn.getWorldName(),
                "x", formatCoord(spawn.getX()),
                "y", formatCoord(spawn.getY()),
                "z", formatCoord(spawn.getZ())
        );
    }

    @Nonnull
    private String formatCoord(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    @Nonnull
    private List<String> parseSpawnList(@Nonnull String raw) {
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String key = SpawnManager.normalizeSpawnName(part);
            if (key != null && !out.contains(key)) {
                out.add(key);
            }
        }
        return out;
    }

    private boolean isNone(@Nonnull String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return value.equals("none") || value.equals("clear") || value.equals("off") || value.equals("-");
    }

    private String normalizeRouteId(@Nonnull String raw) {
        return SpawnManager.normalizeSpawnName(raw);
    }

    private String valueOrNone(@Nonnull String value) {
        return value.isBlank() ? "none" : value;
    }
}
