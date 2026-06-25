package xyz.thelegacyvoyage.hyessentialsx.commands.warp;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.PlayerWarpManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerWarpModel;
import xyz.thelegacyvoyage.hyessentialsx.ui.PlayerWarpsUI;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PlayerWarpCommand extends AbstractPlayerCommand {

    private static final String USE_PERMISSION = "hyessentialsx.playerwarp.use";
    private static final String CREATE_PERMISSION = "hyessentialsx.playerwarp.create";
    private static final String ADMIN_PERMISSION = "hyessentialsx.playerwarp.admin";
    private static final String BYPASS_COST_PERMISSION = "hyessentialsx.playerwarp.bypasscost";

    private final PlayerWarpManager playerWarps;
    private final TPManager tpManager;
    private final ConfigManager config;
    private final CommandCooldownManager cooldowns;
    private final BackManager backManager;
    @Nullable
    private final EconomyManager economy;

    public PlayerWarpCommand(@Nonnull PlayerWarpManager playerWarps,
                             @Nonnull TPManager tpManager,
                             @Nonnull ConfigManager config,
                             @Nonnull CommandCooldownManager cooldowns,
                             @Nonnull BackManager backManager,
                             @Nullable EconomyManager economy) {
        super("pwarp", "Open or manage player warps");
        this.playerWarps = playerWarps;
        this.tpManager = tpManager;
        this.config = config;
        this.cooldowns = cooldowns;
        this.backManager = backManager;
        this.economy = economy;
        this.setPermissionGroups();
        this.addAliases(new String[]{"playerwarp", "playerwarps"});
        this.addUsageVariant(new DirectVisitCommand());
        this.addSubCommand(new CreateSubCommand());
        this.addSubCommand(new RenameSubCommand());
        this.addSubCommand(new DeleteSubCommand());
        this.addSubCommand(new MoveSubCommand());
        this.addSubCommand(new DescSubCommand());
        this.addSubCommand(new PublicSubCommand());
        this.addSubCommand(new PrivateSubCommand());
        this.addSubCommand(new VisitSubCommand());
        this.addSubCommand(new ModerateSubCommand());
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
        if (!enabled(context)) return;
        if (!CommandPermissionUtil.hasPermission(context.sender(), USE_PERMISSION)) {
            Messages.noPerm(context, "/pwarp");
            return;
        }
        if (config.isPlayerWarpsGuiEnabled()) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                new PlayerWarpsUI(playerRef, playerWarps, tpManager, config, cooldowns, backManager, economy).open(player, ref, store);
                return;
            }
        }
        List<PlayerWarpModel> visible = playerWarps.listVisibleWarps(playerRef.getUuid(), isAdmin(context), "");
        if (visible.isEmpty()) {
            Messages.sendKey(context, "playerwarp.none", Map.of());
            return;
        }
        Messages.sendKey(context, "playerwarp.list", Map.of("warps", visible.stream()
                .limit(30)
                .map(warp -> warp.getName() + "(" + warp.getOwnerName() + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElse("")));
    }

    private final class DirectVisitCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;

        private DirectVisitCommand() {
            super("Visit a player warp");
            this.nameArg = withRequiredArg("warp", "Player warp name", ArgTypes.STRING);
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
            visit(context, store, ref, playerRef, world, context.get(nameArg));
        }
    }

    private final class VisitSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;

        private VisitSubCommand() {
            super("visit", "Visit a player warp");
            this.nameArg = withRequiredArg("warp", "Player warp name", ArgTypes.STRING);
            this.addAliases(new String[]{"open", "go"});
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
            visit(context, store, ref, playerRef, world, context.get(nameArg));
        }
    }

    private final class CreateSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;
        private final OptionalArg<List<String>> descArg;

        private CreateSubCommand() {
            super("create", "Create a player warp at your location");
            this.nameArg = withRequiredArg("name", "Warp name", ArgTypes.STRING);
            this.descArg = withListOptionalArg("description", "Description", ArgTypes.STRING);
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
            if (!enabled(context)) return;
            if (!CommandPermissionUtil.hasPermission(context.sender(), CREATE_PERMISSION)
                    && !CommandPermissionUtil.hasPermission(context.sender(), ADMIN_PERMISSION)) {
                Messages.noPerm(context, "/pwarp create");
                return;
            }
            String name = PlayerWarpModel.normalizeName(context.get(nameArg));
            if (!validName(name)) {
                Messages.sendKey(context, "playerwarp.invalid_name", Map.of());
                return;
            }
            if (!playerWarps.isNameAvailable(name)) {
                Messages.sendKey(context, "playerwarp.exists", Map.of("warp", name));
                return;
            }
            if (!isAdmin(context) && config.getPlayerWarpMaxWarpsPerPlayer() > 0
                    && playerWarps.countOwnedWarps(playerRef.getUuid()) >= config.getPlayerWarpMaxWarpsPerPlayer()) {
                Messages.sendKey(context, "playerwarp.limit", Map.of("limit", String.valueOf(config.getPlayerWarpMaxWarpsPerPlayer())));
                return;
            }
            if (!charge(context, playerRef, config.getPlayerWarpCreateCost())) {
                return;
            }
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null || transform.getPosition() == null) {
                Messages.errKey(context, "error.position_unavailable", Map.of());
                return;
            }
            com.hypixel.hytale.math.vector.Rotation3f rot = transform.getRotation();
            String description = context.provided(descArg) ? String.join(" ", context.get(descArg)).trim() : "";
            PlayerWarpModel warp = new PlayerWarpModel(
                    name,
                    playerRef.getUuid().toString(),
                    playerRef.getUsername(),
                    description,
                    null,
                    world.getName(),
                    transform.getPosition().x(), transform.getPosition().y(), transform.getPosition().z(),
                    rot == null ? 0F : rot.y(),
                    rot == null ? 0F : rot.x(),
                    System.currentTimeMillis()
            );
            warp.setApproved(config.isPlayerWarpAutoApprove());
            playerWarps.setWarp(playerRef.getUuid(), warp);
            Messages.sendKey(context, "playerwarp.created", Map.of("warp", name));
        }
    }

    private final class DeleteSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;

        private DeleteSubCommand() {
            super("delete", "Delete one of your player warps");
            this.nameArg = withRequiredArg("name", "Warp name", ArgTypes.STRING);
            this.addAliases(new String[]{"del", "remove"});
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
            if (!enabled(context)) return;
            PlayerWarpModel warp = playerWarps.getOwnedWarp(playerRef.getUuid(), context.get(nameArg));
            if (warp == null) {
                Messages.sendKey(context, "playerwarp.not_found", Map.of());
                return;
            }
            playerWarps.deleteWarp(playerRef.getUuid(), warp.getName());
            Messages.sendKey(context, "playerwarp.deleted", Map.of("warp", warp.getName()));
        }
    }

    private final class RenameSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> oldNameArg;
        private final RequiredArg<String> newNameArg;

        private RenameSubCommand() {
            super("rename", "Rename one of your player warps");
            this.oldNameArg = withRequiredArg("oldName", "Current warp name", ArgTypes.STRING);
            this.newNameArg = withRequiredArg("newName", "New warp name", ArgTypes.STRING);
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
            if (!enabled(context)) return;
            String oldName = PlayerWarpModel.normalizeName(context.get(oldNameArg));
            String newName = PlayerWarpModel.normalizeName(context.get(newNameArg));
            if (!validName(newName)) {
                Messages.sendKey(context, "playerwarp.invalid_name", Map.of());
                return;
            }
            if (!oldName.equals(newName) && !playerWarps.isNameAvailable(newName)) {
                Messages.sendKey(context, "playerwarp.exists", Map.of("warp", newName));
                return;
            }
            if (!playerWarps.renameWarp(playerRef.getUuid(), oldName, newName)) {
                Messages.sendKey(context, "playerwarp.not_found", Map.of());
                return;
            }
            Messages.sendKey(context, "playerwarp.renamed", Map.of("old", oldName, "warp", newName));
        }
    }

    private final class MoveSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;

        private MoveSubCommand() {
            super("move", "Move a player warp to your current location");
            this.nameArg = withRequiredArg("name", "Warp name", ArgTypes.STRING);
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
            PlayerWarpModel warp = playerWarps.getOwnedWarp(playerRef.getUuid(), context.get(nameArg));
            if (warp == null) {
                Messages.sendKey(context, "playerwarp.not_found", Map.of());
                return;
            }
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null || transform.getPosition() == null) {
                Messages.errKey(context, "error.position_unavailable", Map.of());
                return;
            }
            com.hypixel.hytale.math.vector.Rotation3f rot = transform.getRotation();
            warp.updateLocation(null, world.getName(),
                    transform.getPosition().x(), transform.getPosition().y(), transform.getPosition().z(),
                    rot == null ? 0F : rot.y(), rot == null ? 0F : rot.x());
            playerWarps.setWarp(playerRef.getUuid(), warp);
            Messages.sendKey(context, "playerwarp.moved", Map.of("warp", warp.getName()));
        }
    }

    private final class DescSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;
        private final OptionalArg<List<String>> descArg;

        private DescSubCommand() {
            super("desc", "Set a player warp description");
            this.nameArg = withRequiredArg("name", "Warp name", ArgTypes.STRING);
            this.descArg = withListOptionalArg("description", "Description", ArgTypes.STRING);
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
            PlayerWarpModel warp = playerWarps.getOwnedWarp(playerRef.getUuid(), context.get(nameArg));
            if (warp == null) {
                Messages.sendKey(context, "playerwarp.not_found", Map.of());
                return;
            }
            String description = context.provided(descArg) ? String.join(" ", context.get(descArg)).trim() : "";
            warp.setDescription(description);
            playerWarps.setWarp(playerRef.getUuid(), warp);
            Messages.sendKey(context, "playerwarp.updated", Map.of("warp", warp.getName()));
        }
    }

    private final class PublicSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;

        private PublicSubCommand() {
            super("public", "Make one of your player warps public");
            this.nameArg = withRequiredArg("name", "Warp name", ArgTypes.STRING);
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
            setVisibility(context, playerRef, context.get(nameArg), true);
        }
    }

    private final class PrivateSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;

        private PrivateSubCommand() {
            super("private", "Make one of your player warps private");
            this.nameArg = withRequiredArg("name", "Warp name", ArgTypes.STRING);
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
            setVisibility(context, playerRef, context.get(nameArg), false);
        }
    }

    private final class ModerateSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> actionArg;
        private final RequiredArg<String> nameArg;

        private ModerateSubCommand() {
            super("moderate", "Approve, hide, show, or delete any player warp");
            this.actionArg = withRequiredArg("action", "approve|hide|show|delete", ArgTypes.STRING);
            this.nameArg = withRequiredArg("name", "Warp name", ArgTypes.STRING);
            this.addAliases(new String[]{"admin"});
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
            if (!CommandPermissionUtil.hasPermission(context.sender(), ADMIN_PERMISSION)) {
                Messages.noPerm(context, "/pwarp moderate");
                return;
            }
            PlayerWarpModel warp = playerWarps.findAnyWarp(context.get(nameArg));
            if (warp == null) {
                Messages.sendKey(context, "playerwarp.not_found", Map.of());
                return;
            }
            String action = context.get(actionArg).toLowerCase(Locale.ROOT);
            if ("delete".equals(action)) {
                playerWarps.deleteWarp(java.util.UUID.fromString(warp.getOwnerUuid()), warp.getName());
            } else if ("approve".equals(action)) {
                warp.setApproved(true);
                playerWarps.setWarp(java.util.UUID.fromString(warp.getOwnerUuid()), warp);
            } else if ("hide".equals(action)) {
                warp.setEnabled(false);
                playerWarps.setWarp(java.util.UUID.fromString(warp.getOwnerUuid()), warp);
            } else if ("show".equals(action)) {
                warp.setEnabled(true);
                playerWarps.setWarp(java.util.UUID.fromString(warp.getOwnerUuid()), warp);
            } else {
                Messages.sendKey(context, "playerwarp.moderate_usage", Map.of());
                return;
            }
            Messages.sendKey(context, "playerwarp.moderated", Map.of("warp", warp.getName(), "action", action));
        }
    }

    private void setVisibility(@Nonnull CommandContext context,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull String name,
                               boolean visible) {
        PlayerWarpModel warp = playerWarps.getOwnedWarp(playerRef.getUuid(), name);
        if (warp == null) {
            Messages.sendKey(context, "playerwarp.not_found", Map.of());
            return;
        }
        warp.setPublicWarp(visible);
        playerWarps.setWarp(playerRef.getUuid(), warp);
        Messages.sendKey(context, visible ? "playerwarp.public" : "playerwarp.private", Map.of("warp", warp.getName()));
    }

    private void visit(@Nonnull CommandContext context,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull Ref<EntityStore> ref,
                       @Nonnull PlayerRef playerRef,
                       @Nonnull World world,
                       @Nonnull String name) {
        if (!enabled(context)) return;
        if (!CommandPermissionUtil.hasPermission(context.sender(), USE_PERMISSION)) {
            Messages.noPerm(context, "/pwarp visit");
            return;
        }
        PlayerWarpModel warp = playerWarps.findVisibleWarp(name, playerRef.getUuid(), isAdmin(context));
        if (warp == null) {
            Messages.sendKey(context, "playerwarp.not_found", Map.of());
            return;
        }
        if (!charge(context, playerRef, config.getPlayerWarpVisitCost())) {
            return;
        }
        if (!cooldowns.canUse(context, playerRef, CooldownKeys.WARP, "/pwarp", "hyessentialsx.playerwarp.bypass")) {
            return;
        }
        com.hypixel.hytale.math.vector.Transform transform = playerRef.getTransform();
        if (transform != null && transform.getPosition() != null) {
            com.hypixel.hytale.math.vector.Rotation3f rot = transform.getRotation();
            backManager.recordLocation(playerRef.getUuid(), world.getName(),
                    transform.getPosition().x(), transform.getPosition().y(), transform.getPosition().z(),
                    rot == null ? 0F : rot.y(), rot == null ? 0F : rot.x());
        }
        String err = TeleportationUtil.teleportToLocation(store, ref, warp.getWorldId(), warp.getWorldName(),
                warp.getX(), warp.getY(), warp.getZ(), warp.getYaw(), warp.getPitch());
        if (err != null) {
            Messages.err(context, err);
            return;
        }
        warp.incrementVisits();
        playerWarps.setWarp(java.util.UUID.fromString(warp.getOwnerUuid()), warp);
        cooldowns.apply(playerRef, CooldownKeys.WARP);
        Messages.sendKey(context, "playerwarp.visited", Map.of("warp", warp.getName(), "owner", warp.getOwnerName()));
    }

    private boolean charge(@Nonnull CommandContext context, @Nonnull PlayerRef playerRef, long amount) {
        if (amount <= 0L || economy == null || !economy.isEnabled()
                || CommandPermissionUtil.hasPermission(context.sender(), BYPASS_COST_PERMISSION)) {
            return true;
        }
        if (!economy.withdraw(playerRef.getUuid(), amount)) {
            Messages.sendKey(context, "economy.insufficient_funds", Map.of());
            return false;
        }
        return true;
    }

    private boolean enabled(@Nonnull CommandContext context) {
        if (config.isPlayerWarpsEnabled()) {
            return true;
        }
        Messages.sendKey(context, "playerwarp.disabled", Map.of());
        return false;
    }

    private boolean isAdmin(@Nonnull CommandContext context) {
        return CommandPermissionUtil.hasPermission(context.sender(), ADMIN_PERMISSION);
    }

    private boolean validName(@Nonnull String name) {
        return name.matches("[a-z0-9_-]{2,32}");
    }
}
