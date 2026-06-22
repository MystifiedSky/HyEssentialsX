package xyz.thelegacyvoyage.hyessentialsx.ui;

import com.google.gson.Gson;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.HomeManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.models.HomeModel;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HomesUI extends CustomUIPage {

    private static final String LAYOUT = "hyessentialsx/HomePage.ui";
    private static final String ROW_LAYOUT = "hyessentialsx/HomeRow.ui";
    private static final String BYPASS_PERMISSION = "hyessentialsx.home.bypass";

    private final PlayerRef playerRef;
    private final HomeManager homeManager;
    private final TPManager tpManager;
    private final ConfigManager config;
    private final CommandCooldownManager cooldowns;
    private final BackManager backManager;
    private final Gson gson = new Gson();

    public HomesUI(@Nonnull PlayerRef playerRef,
                   @Nonnull HomeManager homeManager,
                   @Nonnull TPManager tpManager,
                   @Nonnull ConfigManager config,
                   @Nonnull CommandCooldownManager cooldowns,
                   @Nonnull BackManager backManager) {
        super(playerRef, CustomPageLifetime.CanDismiss);
        this.playerRef = playerRef;
        this.homeManager = homeManager;
        this.tpManager = tpManager;
        this.config = config;
        this.cooldowns = cooldowns;
        this.backManager = backManager;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store
    ) {
        cmd.append(LAYOUT);

        List<String> homes = homeManager.listHomes(playerRef.getUuid());
        cmd.set("#HomeCount.Text", homes.size() + " Homes");

        buildHomesList(cmd, evt, homes);

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("action", "close"),
                false
        );
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            String data
    ) {
        if (data == null || data.isEmpty()) {
            return;
        }

        Map<?, ?> payload;
        try {
            payload = gson.fromJson(data, Map.class);
        } catch (Exception e) {
            return;
        }
        if (payload == null) {
            return;
        }
        Object actionObj = payload.get("action");
        if (!(actionObj instanceof String)) {
            return;
        }
        String action = (String) actionObj;
        if (action.isEmpty()) {
            return;
        }

        if (action.equals("close")) {
            close();
            return;
        }

        if (action.startsWith("teleport:")) {
            String name = action.substring("teleport:".length());
            teleportToHome(ref, store, name);
        }
    }

    public void open(@Nonnull Player player, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        player.getPageManager().openCustomPage(ref, store, this);
    }

    private void buildHomesList(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt, @Nonnull List<String> homes) {
        cmd.clear("#HomesList");

        if (homes.isEmpty()) {
            cmd.appendInline("#HomesList",
                    "Label { Text: \"You don't have any homes yet.\"; " +
                            "Style: (FontSize: 13, TextColor: #666666, HorizontalAlignment: Center); " +
                            "Anchor: (Top: 30); }");
            return;
        }

        int rowIndex = 0;
        for (String name : homes) {
            HomeModel home = homeManager.getHome(playerRef.getUuid(), name);
            if (home == null) {
                continue;
            }
            cmd.appendInline("#HomesList", "Group { Anchor: (Bottom: 6); }");
            cmd.append("#HomesList[" + rowIndex + "]", ROW_LAYOUT);
            String rowBase = "#HomesList[" + rowIndex + "][0]";
            cmd.set(rowBase + " #Info #HomeName.Text", name);
            String coords = String.format(Locale.ROOT, "%s: %.0f, %.0f, %.0f",
                    home.getWorldName(), home.x(), home.y(), home.z());
            cmd.set(rowBase + " #Info #HomeCoords.Text", coords);
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    rowBase + " #Actions #TeleportBtn",
                    EventData.of("action", "teleport:" + name),
                    false
            );
            rowIndex++;
        }
    }

    private void teleportToHome(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String name) {
        if (name.isEmpty()) {
            return;
        }
        if (!cooldowns.canUse(playerRef, CooldownKeys.HOME, "/home", BYPASS_PERMISSION)) {
            return;
        }

        HomeModel home = homeManager.getHome(playerRef.getUuid(), name);
        if (home == null) {
            Messages.sendPrefixedKey(playerRef, "home.not_found", Map.of());
            return;
        }

        close();

        int warmupSeconds = config.getHomeWarmupSeconds();
        if (cooldowns.hasWarmupBypass(playerRef, CooldownKeys.HOME, BYPASS_PERMISSION)) {
            warmupSeconds = 0;
        }
        if (warmupSeconds > 0) {
            if (tpManager.hasPending(playerRef.getUuid())) {
                Messages.sendPrefixedKey(playerRef, "teleport.pending", Map.of());
                return;
            }
            com.hypixel.hytale.math.vector.Transform transform = playerRef.getTransform();
            if (transform == null || transform.getPosition() == null) {
                Messages.sendPrefixedKey(playerRef, "teleport.position_unavailable", Map.of());
                return;
            }
            com.hypixel.hytale.math.vector.Rotation3f rot = transform.getRotation();
            float startYaw = (rot != null) ? rot.y() : 0f;
            float startPitch = (rot != null) ? rot.x() : 0f;
            org.joml.Vector3d startPos = new org.joml.Vector3d(transform.getPosition());
            String startWorldName = resolveWorldName(store);
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
                                home.x(), home.y(), home.z(),
                                home.yaw(), home.pitch()
                        );
                        if (err != null) {
                            Messages.sendPrefixed(playerRef, err);
                            return;
                        }
                        if (startWorldName != null) {
                            backManager.recordLocation(
                                    playerRef.getUuid(),
                                    startWorldName,
                                    startPos.x(), startPos.y(), startPos.z(),
                                    startYaw, startPitch
                            );
                        }
                        cooldowns.apply(playerRef, CooldownKeys.HOME);
                        Messages.sendPrefixedKey(playerRef, "teleport.success.home", Map.of("home", name));
                    }
            );
            Messages.sendPrefixedKey(playerRef, "teleport.warmup", Map.of("seconds", String.valueOf(warmupSeconds)));
            return;
        }

        com.hypixel.hytale.math.vector.Transform transform = playerRef.getTransform();
        if (transform != null && transform.getPosition() != null) {
            String worldName = resolveWorldName(store);
            if (worldName != null) {
                com.hypixel.hytale.math.vector.Rotation3f rot = transform.getRotation();
                float startYaw = (rot != null) ? rot.y() : 0f;
                float startPitch = (rot != null) ? rot.x() : 0f;
                backManager.recordLocation(
                        playerRef.getUuid(),
                        worldName,
                        transform.getPosition().x(),
                        transform.getPosition().y(),
                        transform.getPosition().z(),
                        startYaw, startPitch
                );
            }
        }

        String err = TeleportationUtil.teleportToLocation(
                store,
                ref,
                home.getWorldId(),
                home.getWorldName(),
                home.x(), home.y(), home.z(),
                home.yaw(), home.pitch()
        );
        if (err != null) {
            Messages.sendPrefixed(playerRef, err);
            return;
        }

        cooldowns.apply(playerRef, CooldownKeys.HOME);
        Messages.sendPrefixedKey(playerRef, "teleport.success.home", Map.of("home", name));
    }

    @Nullable
    private String resolveWorldName(@Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        return world != null ? world.getName() : null;
    }
}

