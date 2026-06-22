package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
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
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.nameArg = withRequiredArg("player", "Player name", ArgTypes.STRING);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
            Messages.noPerm(context, "/whois");
            return;
        }

        String name = context.get(nameArg);
        if (name == null || name.isBlank()) {
            Messages.err(context, "Player name required.");
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
            Messages.err(context, "Player not found.");
            return;
        }

        PlayerDataModel data = storage.getPlayerData(uuid);
        String displayName = (data.getLastKnownName() != null && !data.getLastKnownName().isBlank())
                ? data.getLastKnownName()
                : name;

        context.sendMessage(Messages.m("&8----------------------------------------"));
        context.sendMessage(Messages.m("&aWHOIS &7- &f" + displayName));
        context.sendMessage(Messages.m("&aUUID: &f" + uuid));

        boolean isOnline = online != null;
        context.sendMessage(Messages.m("&aOnline: &f" + (isOnline ? "Yes" : "No")));

        if (isOnline) {
            Transform t = online.getTransform();
            World world = Universe.get().getWorld(online.getWorldUuid());
            if (t != null) {
                Vector3d pos = t.getPosition();
                if (pos != null) {
                    context.sendMessage(Messages.m("&aLocation: &f" + (world != null ? world.getName() : "unknown") +
                            " &7(" + (int) pos.getX() + ", " + (int) pos.getY() + ", " + (int) pos.getZ() + ")"));
                }
            }
            if (world != null) {
                world.execute(() -> {
                    GameMode gameMode = resolveGameModeOnWorldThread(online);
                    String gmText = gameMode != null ? gameMode.name() : "Unknown";
                    context.sendMessage(Messages.m("&aGamemode: &f" + gmText));
                });
            } else {
                context.sendMessage(Messages.m("&aGamemode: &fUnknown"));
            }
        } else {
            context.sendMessage(Messages.m("&aGamemode: &fUnknown"));
        }

        long lastSeen = data.getLastSeenAt();
        context.sendMessage(Messages.m("&aLast Seen: &f" + formatTimestamp(lastSeen) + formatAgo(lastSeen)));

        String language = data.getLanguage();
        context.sendMessage(Messages.m("&aLanguage: &f" + (language == null || language.isBlank() ? "default" : language)));

        long playtimeSeconds = data.getPlaytimeSeconds();
        context.sendMessage(Messages.m("&aPlaytime: &f" + TimeUtil.formatDurationSeconds(playtimeSeconds)));

        context.sendMessage(Messages.m("&aMute: &f" + formatMute(data.getMute())));
        context.sendMessage(Messages.m("&aBan: &f" + formatBan(data.getBan())));
        context.sendMessage(Messages.m("&8----------------------------------------"));
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
    private static String formatTimestamp(long millis) {
        if (millis <= 0L) return "Never";
        Instant instant = Instant.ofEpochMilli(millis);
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }

    @Nonnull
    private static String formatAgo(long millis) {
        if (millis <= 0L) return "";
        long diff = Math.max(0, System.currentTimeMillis() - millis);
        String ago = TimeUtil.formatDurationSeconds(diff / 1000L);
        return " &7(" + ago + " ago)";
    }

    @Nonnull
    private static String formatMute(@Nullable MuteModel mute) {
        if (mute == null) return "No";
        String remaining = TimeUtil.formatRemaining(mute.getExpiresAt());
        String reason = (mute.getReason() != null && !mute.getReason().isBlank()) ? mute.getReason() : "No reason";
        String actor = (mute.getActorName() != null && !mute.getActorName().isBlank()) ? mute.getActorName() : "Unknown";
        return "Yes &7(" + remaining + ", " + reason + ", by " + actor + ")";
    }

    @Nonnull
    private static String formatBan(@Nullable BanModel ban) {
        if (ban == null) return "No";
        String remaining = TimeUtil.formatRemaining(ban.getExpiresAt());
        String reason = (ban.getReason() != null && !ban.getReason().isBlank()) ? ban.getReason() : "No reason";
        String actor = (ban.getActorName() != null && !ban.getActorName().isBlank()) ? ban.getActorName() : "Unknown";
        return "Yes &7(" + remaining + ", " + reason + ", by " + actor + ")";
    }
}



