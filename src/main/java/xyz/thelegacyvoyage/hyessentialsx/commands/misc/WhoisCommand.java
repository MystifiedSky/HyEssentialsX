package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import org.joml.Vector3d;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.models.BanModel;
import xyz.thelegacyvoyage.hyessentialsx.models.MuteModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandSenderUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.IpUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class WhoisCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.whois";

    private final StorageManager storage;
    private final RequiredArg<String> nameArg;

    public WhoisCommand(@Nonnull StorageManager storage) {
        super("whois", "Shows player information");
        this.storage = storage;
        this.setPermissionGroups();
        this.addAliases(new String[]{"joindate"});
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.nameArg = withRequiredArg("player", "Player name", ArgTypes.STRING);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/whois");
            return;
        }

        PlayerRef viewer = CommandSenderUtil.resolvePlayer(context);

        String name = context.get(nameArg);
        if (name == null || name.isBlank()) {
            Messages.errKey(context, "player.name_required", java.util.Map.of());
            return;
        }

        PlayerRef online = Universe.get().getPlayerByUsername(name, NameMatching.EXACT_IGNORE_CASE);
        UUID uuid = null;
        if (online != null) {
            uuid = online.getUuid();
        } else {
            uuid = storage.resolvePlayerIdByName(name);
        }

        if (uuid == null) {
            Messages.errKey(context, "player.not_found", java.util.Map.of());
            return;
        }

        PlayerDataModel data = storage.getPlayerData(uuid);
        String displayName = (data.getLastKnownName() != null && !data.getLastKnownName().isBlank())
                ? data.getLastKnownName()
                : name;

        context.sendMessage(Messages.m(Messages.tr(viewer, "info.separator", java.util.Map.of())));
        context.sendMessage(Messages.m(Messages.tr(viewer, "whois.header", java.util.Map.of(
                "player", displayName
        ))));
        context.sendMessage(Messages.m(Messages.tr(viewer, "whois.uuid", java.util.Map.of(
                "uuid", uuid.toString()
        ))));

        boolean isOnline = online != null;
        String onlineText = Messages.tr(viewer, isOnline ? "info.yes" : "info.no", java.util.Map.of());
        context.sendMessage(Messages.m(Messages.tr(viewer, "whois.online", java.util.Map.of(
                "status", onlineText
        ))));
        String currentIp = null;
        if (online != null) {
            currentIp = IpUtil.extractIp(online.getPacketHandler());
            if (currentIp != null && !currentIp.isBlank()) {
                data.addOrUpdateIp(currentIp);
                storage.savePlayerDataAsync(uuid, data);
            }
        }
        if (currentIp == null || currentIp.isBlank()) {
            currentIp = data.getCurrentIp();
        }
        String ipText = (currentIp == null || currentIp.isBlank())
                ? Messages.tr(viewer, "info.unknown", java.util.Map.of())
                : currentIp;
        context.sendMessage(Messages.m(Messages.tr(viewer, "whois.current_ip", java.util.Map.of(
                "ip", ipText
        ))));

        if (isOnline) {
            Transform t = online.getTransform();
            World world = Universe.get().getWorld(online.getWorldUuid());
            if (t != null) {
                Vector3d pos = t.getPosition();
                if (pos != null) {
                    String worldName = world != null
                            ? world.getName()
                            : Messages.tr(viewer, "info.unknown", java.util.Map.of());
                    context.sendMessage(Messages.m(Messages.tr(viewer, "whois.location", java.util.Map.of(
                            "world", worldName,
                            "x", String.valueOf((int) pos.x()),
                            "y", String.valueOf((int) pos.y()),
                            "z", String.valueOf((int) pos.z())
                    ))));
                }
            }
            if (world != null) {
                PlayerRef viewerRef = viewer;
                String unknown = Messages.tr(viewer, "info.unknown", java.util.Map.of());
                world.execute(() -> {
                    GameMode gameMode = resolveGameModeOnWorldThread(online);
                    String gmText = gameMode != null ? gameMode.name() : unknown;
                    context.sendMessage(Messages.m(Messages.tr(viewerRef, "whois.gamemode", java.util.Map.of(
                            "gamemode", gmText
                    ))));
                });
            } else {
                context.sendMessage(Messages.m(Messages.tr(viewer, "whois.gamemode", java.util.Map.of(
                        "gamemode", Messages.tr(viewer, "info.unknown", java.util.Map.of())
                ))));
            }
        } else {
            context.sendMessage(Messages.m(Messages.tr(viewer, "whois.gamemode", java.util.Map.of(
                    "gamemode", Messages.tr(viewer, "info.unknown", java.util.Map.of())
            ))));
        }

        long lastSeen = data.getLastSeenAt();
        long firstJoin = data.getFirstJoinAt();
        context.sendMessage(Messages.m(Messages.tr(viewer, "whois.first_join", java.util.Map.of(
                "time", formatTimestamp(viewer, firstJoin),
                "ago", formatAgo(viewer, firstJoin)
        ))));
        context.sendMessage(Messages.m(Messages.tr(viewer, "whois.last_seen", java.util.Map.of(
                "time", formatTimestamp(viewer, lastSeen),
                "ago", formatAgo(viewer, lastSeen)
        ))));

        String language = data.getLanguage();
        String languageText = (language == null || language.isBlank())
                ? Messages.tr(viewer, "language.default", java.util.Map.of())
                : language;
        context.sendMessage(Messages.m(Messages.tr(viewer, "whois.language", java.util.Map.of(
                "language", languageText
        ))));

        long playtimeSeconds = data.getPlaytimeSeconds();
        context.sendMessage(Messages.m(Messages.tr(viewer, "whois.playtime", java.util.Map.of(
                "time", TimeUtil.formatDurationSeconds(playtimeSeconds)
        ))));

        context.sendMessage(Messages.m(Messages.tr(viewer, "whois.mute", java.util.Map.of(
                "status", formatMute(viewer, data.getMute())
        ))));
        context.sendMessage(Messages.m(Messages.tr(viewer, "whois.ban", java.util.Map.of(
                "status", formatBan(viewer, data.getBan())
        ))));
        context.sendMessage(Messages.m(Messages.tr(viewer, "info.separator", java.util.Map.of())));
    }

    @Nullable
    private static GameMode resolveGameModeOnWorldThread(@Nullable PlayerRef online) {
        if (online == null) return null;
        Ref<EntityStore> ref = online.getReference();
        if (ref == null) return null;
        Store<EntityStore> store = ref.getStore();
        if (store == null) return null;
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return null;
        return player.getGameMode();
    }

    @Nonnull
    private static String formatTimestamp(@Nullable PlayerRef viewer, long millis) {
        if (millis <= 0L) return Messages.tr(viewer, "time.never", java.util.Map.of());
        Instant instant = Instant.ofEpochMilli(millis);
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }

    @Nonnull
    private static String formatAgo(@Nullable PlayerRef viewer, long millis) {
        if (millis <= 0L) return "";
        long diff = Math.max(0, System.currentTimeMillis() - millis);
        String ago = TimeUtil.formatDurationSeconds(diff / 1000L);
        return Messages.tr(viewer, "time.ago", java.util.Map.of("time", ago));
    }

    @Nonnull
    private static String formatMute(@Nullable PlayerRef viewer, @Nullable MuteModel mute) {
        if (mute == null) return Messages.tr(viewer, "punishment.none", java.util.Map.of());
        String remaining = TimeUtil.formatRemaining(mute.getExpiresAt());
        String reason = (mute.getReason() != null && !mute.getReason().isBlank())
                ? mute.getReason()
                : Messages.tr(viewer, "reason.none", java.util.Map.of());
        String actor = (mute.getActorName() != null && !mute.getActorName().isBlank())
                ? mute.getActorName()
                : Messages.tr(viewer, "actor.unknown", java.util.Map.of());
        return Messages.tr(viewer, "punishment.active", java.util.Map.of(
                "remaining", remaining,
                "reason", reason,
                "actor", actor
        ));
    }

    @Nonnull
    private static String formatBan(@Nullable PlayerRef viewer, @Nullable BanModel ban) {
        if (ban == null) return Messages.tr(viewer, "punishment.none", java.util.Map.of());
        String remaining = TimeUtil.formatRemaining(ban.getExpiresAt());
        String reason = (ban.getReason() != null && !ban.getReason().isBlank())
                ? ban.getReason()
                : Messages.tr(viewer, "reason.none", java.util.Map.of());
        String actor = (ban.getActorName() != null && !ban.getActorName().isBlank())
                ? ban.getActorName()
                : Messages.tr(viewer, "actor.unknown", java.util.Map.of());
        return Messages.tr(viewer, "punishment.active", java.util.Map.of(
                "remaining", remaining,
                "reason", reason,
                "actor", actor
        ));
    }
}




