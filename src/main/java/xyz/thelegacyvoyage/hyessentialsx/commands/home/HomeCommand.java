package xyz.thelegacyvoyage.hyessentialsx.commands.home;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.HomeManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.models.HomeModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;
import xyz.thelegacyvoyage.hyessentialsx.ui.HomesUI;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HomeCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.home";
    private static final String BYPASS_PERMISSION = "hyessentialsx.home.bypass";
    private static final String OTHER_PERMISSION = "hyessentialsx.home.other";

    private final HomeManager homeManager;
    private final TPManager tpManager;
    private final ConfigManager config;
    private final CommandCooldownManager cooldowns;
    private final BackManager backManager;
    private final StorageManager storage;
    public HomeCommand(@Nonnull HomeManager homeManager,
                       @Nonnull TPManager tpManager,
                       @Nonnull ConfigManager config,
                       @Nonnull CommandCooldownManager cooldowns,
                       @Nonnull BackManager backManager,
                       @Nonnull StorageManager storage) {
        super("home", "Teleports to a home");
        this.homeManager = homeManager;
        this.tpManager = tpManager;
        this.config = config;
        this.cooldowns = cooldowns;
        this.backManager = backManager;
        this.storage = storage;
        this.setPermissionGroup(null);
        this.setAllowsExtraArguments(true);
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
            Messages.noPerm(context, "/home");
            return;
        }
        if (!config.isHomesEnabled()) {
            Messages.errKey(context, "home.disabled", Map.of());
            return;
        }
        List<String> args = CommandInputUtil.getArgs(context);
        if (args.isEmpty()) {
            List<String> homes = homeManager.listHomes(playerRef.getUuid());
            if (homes.isEmpty()) {
                Messages.errKey(context, "home.none", Map.of());
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                Messages.errKey(context, "home.ui_failed", Map.of());
                return;
            }
            HomesUI page = new HomesUI(playerRef, homeManager, tpManager, config, cooldowns, backManager);
            page.open(player, ref, store);
            return;
        }

        String firstArg = args.get(0);
        if ("player".equalsIgnoreCase(firstArg) && args.size() >= 2) {
            handlePlayerHomes(context, store, ref, playerRef, world, args);
            return;
        }

        String name = firstArg;
        if (!cooldowns.canUse(context, playerRef, CooldownKeys.HOME, "/home", BYPASS_PERMISSION)) {
            return;
        }

        HomeModel home = homeManager.getHome(playerRef.getUuid(), name.trim());
        if (home == null) {
            Messages.errKey(context, "home.not_found", Map.of());
            return;
        }

        final String homeName = name.trim();
        int warmupSeconds = config.getHomeWarmupSeconds();
        if (warmupSeconds > 0) {
            if (tpManager.hasPending(playerRef.getUuid())) {
                Messages.errKey(context, "teleport.pending", Map.of());
                return;
            }
            com.hypixel.hytale.math.vector.Transform transform = playerRef.getTransform();
            if (transform == null || transform.getPosition() == null) {
                Messages.errKey(context, "teleport.position_unavailable", Map.of());
                return;
            }
            com.hypixel.hytale.math.vector.Vector3f rot = transform.getRotation();
            float startYaw = (rot != null) ? rot.getY() : 0f;
            float startPitch = (rot != null) ? rot.getX() : 0f;
            com.hypixel.hytale.math.vector.Vector3d startPos = transform.getPosition().clone();
            tpManager.queue(
                    playerRef.getUuid(),
                    startPos,
                    warmupSeconds,
                    buffer -> {
                        String err = TeleportationUtil.teleportToLocation(
                                buffer,
                                ref,
                                home.getWorldId(),
                                home.getWorldName(),
                                home.getX(), home.getY(), home.getZ(),
                                home.getYaw(), home.getPitch()
                        );
                        if (err != null) {
                            Messages.sendPrefixed(playerRef, err);
                            return;
                        }
                        backManager.recordLocation(
                                playerRef.getUuid(),
                                world.getName(),
                                startPos.getX(), startPos.getY(), startPos.getZ(),
                                startYaw, startPitch
                        );
                        cooldowns.apply(playerRef, CooldownKeys.HOME);
                        Messages.sendPrefixedKey(playerRef, "teleport.success.home", Map.of("home", homeName));
                    }
            );
            Messages.sendPrefixedKey(playerRef, "teleport.warmup", Map.of("seconds", String.valueOf(warmupSeconds)));
            return;
        }

        com.hypixel.hytale.math.vector.Transform transform = playerRef.getTransform();
        if (transform != null && transform.getPosition() != null) {
            com.hypixel.hytale.math.vector.Vector3f rot = transform.getRotation();
            float startYaw = (rot != null) ? rot.getY() : 0f;
            float startPitch = (rot != null) ? rot.getX() : 0f;
            backManager.recordLocation(
                    playerRef.getUuid(),
                    world.getName(),
                    transform.getPosition().getX(), transform.getPosition().getY(), transform.getPosition().getZ(),
                    startYaw, startPitch
            );
        }

        tpManager.cancel(playerRef.getUuid(), null);
        String err = TeleportationUtil.teleportToLocation(
                store,
                ref,
                home.getWorldId(),
                home.getWorldName(),
                home.getX(), home.getY(), home.getZ(),
                home.getYaw(), home.getPitch()
        );
        if (err != null) {
            Messages.err(context, err);
            return;
        }

        cooldowns.apply(playerRef, CooldownKeys.HOME);
        Messages.okKey(context, "teleport.success.home", Map.of("home", homeName));
    }

    private void handlePlayerHomes(@Nonnull CommandContext context,
                                   @Nonnull Store<EntityStore> store,
                                   @Nonnull Ref<EntityStore> ref,
                                   @Nonnull PlayerRef playerRef,
                                   @Nonnull World world,
                                   @Nonnull List<String> args) {
        if (!context.sender().hasPermission(OTHER_PERMISSION)) {
            Messages.noPerm(context, "/home player");
            return;
        }
        if (args.size() < 2) {
            Messages.err(context, "Usage: /home player <player> [home]");
            return;
        }
        String targetName = args.get(1);
        if (targetName == null || targetName.isBlank()) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }

        PlayerRef online = Universe.get().getPlayerByUsername(targetName, NameMatching.EXACT_IGNORE_CASE);
        UUID targetId = online != null ? online.getUuid() : storage.resolvePlayerIdByName(targetName);
        if (targetId == null) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }

        String displayName = resolveDisplayName(online, targetId, targetName);
        if (args.size() < 3) {
            List<String> homes = homeManager.listHomes(targetId);
            if (homes.isEmpty()) {
                Messages.err(context, displayName + " has no homes.");
                return;
            }
            Collections.sort(homes, String.CASE_INSENSITIVE_ORDER);
            Messages.ok(context, "Homes for " + displayName + ": " + String.join(", ", homes));
            return;
        }

        String homeName = String.join(" ", args.subList(2, args.size())).trim();
        if (homeName.isEmpty()) {
            Messages.errKey(context, "home.name_required", Map.of());
            return;
        }

        if (!cooldowns.canUse(context, playerRef, CooldownKeys.HOME, "/home", BYPASS_PERMISSION)) {
            return;
        }

        HomeModel home = homeManager.getHome(targetId, homeName);
        if (home == null) {
            Messages.errKey(context, "home.not_found", Map.of());
            return;
        }

        teleportToHome(context, store, ref, playerRef, world, homeName, home);
    }

    private void teleportToHome(@Nonnull CommandContext context,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull Ref<EntityStore> ref,
                                @Nonnull PlayerRef playerRef,
                                @Nonnull World world,
                                @Nonnull String homeName,
                                @Nonnull HomeModel home) {
        int warmupSeconds = config.getHomeWarmupSeconds();
        if (warmupSeconds > 0) {
            if (tpManager.hasPending(playerRef.getUuid())) {
                Messages.errKey(context, "teleport.pending", Map.of());
                return;
            }
            com.hypixel.hytale.math.vector.Transform transform = playerRef.getTransform();
            if (transform == null || transform.getPosition() == null) {
                Messages.errKey(context, "teleport.position_unavailable", Map.of());
                return;
            }
            com.hypixel.hytale.math.vector.Vector3f rot = transform.getRotation();
            float startYaw = (rot != null) ? rot.getY() : 0f;
            float startPitch = (rot != null) ? rot.getX() : 0f;
            com.hypixel.hytale.math.vector.Vector3d startPos = transform.getPosition().clone();
            tpManager.queue(
                    playerRef.getUuid(),
                    startPos,
                    warmupSeconds,
                    buffer -> {
                        String err = TeleportationUtil.teleportToLocation(
                                buffer,
                                ref,
                                home.getWorldId(),
                                home.getWorldName(),
                                home.getX(), home.getY(), home.getZ(),
                                home.getYaw(), home.getPitch()
                        );
                        if (err != null) {
                            Messages.sendPrefixed(playerRef, err);
                            return;
                        }
                        backManager.recordLocation(
                                playerRef.getUuid(),
                                world.getName(),
                                startPos.getX(), startPos.getY(), startPos.getZ(),
                                startYaw, startPitch
                        );
                        cooldowns.apply(playerRef, CooldownKeys.HOME);
                        Messages.sendPrefixedKey(playerRef, "teleport.success.home", Map.of("home", homeName));
                    }
            );
            Messages.sendPrefixedKey(playerRef, "teleport.warmup", Map.of("seconds", String.valueOf(warmupSeconds)));
            return;
        }

        com.hypixel.hytale.math.vector.Transform transform = playerRef.getTransform();
        if (transform != null && transform.getPosition() != null) {
            com.hypixel.hytale.math.vector.Vector3f rot = transform.getRotation();
            float startYaw = (rot != null) ? rot.getY() : 0f;
            float startPitch = (rot != null) ? rot.getX() : 0f;
            backManager.recordLocation(
                    playerRef.getUuid(),
                    world.getName(),
                    transform.getPosition().getX(), transform.getPosition().getY(), transform.getPosition().getZ(),
                    startYaw, startPitch
            );
        }

        String err = TeleportationUtil.teleportToLocation(
                store,
                ref,
                home.getWorldId(),
                home.getWorldName(),
                home.getX(), home.getY(), home.getZ(),
                home.getYaw(), home.getPitch()
        );
        if (err != null) {
            Messages.err(context, err);
            return;
        }

        cooldowns.apply(playerRef, CooldownKeys.HOME);
        Messages.okKey(context, "teleport.success.home", Map.of("home", homeName));
    }

    @Nonnull
    private String resolveDisplayName(@Nullable PlayerRef online, @Nonnull UUID uuid, @Nonnull String fallback) {
        if (online != null) return online.getUsername();
        String name = storage.getPlayerData(uuid).getLastKnownName();
        if (name != null && !name.isBlank()) return name;
        return fallback;
    }
}




