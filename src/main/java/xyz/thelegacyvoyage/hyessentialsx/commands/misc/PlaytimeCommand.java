package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.PlaytimeManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.PlaytimeRewardManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.RankupManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlaytimeRewardModel;
import xyz.thelegacyvoyage.hyessentialsx.ui.PlaytimeAdminUI;
import xyz.thelegacyvoyage.hyessentialsx.ui.PlaytimeUI;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlaytimeCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.playtime";
    private static final String OTHERS_PERMISSION = "hyessentialsx.playtime.other";
    private static final String ADMIN_PERMISSION = "hyessentialsx.playtime.admin";
    private static final int CHAT_TOP_LIMIT = 10;

    private final PlaytimeManager playtime;
    private final PlaytimeRewardManager rewards;
    private final RankupManager rankups;
    private final StorageManager storage;
    private final ConfigManager config;
    private final OptionalArg<String> actionArg;

    public PlaytimeCommand(@Nonnull PlaytimeManager playtime,
                           @Nonnull PlaytimeRewardManager rewards,
                           @Nonnull RankupManager rankups,
                           @Nonnull StorageManager storage,
                           @Nonnull ConfigManager config) {
        super("playtime", "Shows your playtime");
        this.playtime = playtime;
        this.rewards = rewards;
        this.rankups = rankups;
        this.storage = storage;
        this.config = config;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"pt"});
        this.actionArg = withOptionalArg("action", "rewards, top, admin, help, or player", ArgTypes.STRING);
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
            Messages.noPerm(context, "/playtime");
            return;
        }

        if (!context.provided(actionArg)) {
            if (tryOpenPlaytimeUi(playerRef, store, ref, PlaytimeUI.Tab.OVERVIEW)) {
                return;
            }
            sendSelfPlaytime(context, playerRef);
            return;
        }

        String sub = context.get(actionArg);
        if (sub == null || sub.isBlank()) {
            Messages.errKey(context, "playtime.usage", Map.of());
            return;
        }

        switch (sub.toLowerCase(Locale.ROOT)) {
            case "rewards" -> {
                if (tryOpenPlaytimeUi(playerRef, store, ref, PlaytimeUI.Tab.REWARDS)) {
                    return;
                }
                sendRewardsList(context, playerRef);
            }
            case "top" -> {
                if (tryOpenPlaytimeUi(playerRef, store, ref, PlaytimeUI.Tab.TOP)) {
                    return;
                }
                sendTopList(context);
            }
            case "admin" -> openAdminPanel(context, playerRef, store, ref);
            case "help" -> Messages.sendKey(context, "playtime.usage", Map.of());
            default -> sendOtherPlaytime(context, sub);
        }
    }

    private boolean tryOpenPlaytimeUi(@Nonnull PlayerRef playerRef,
                                      @Nonnull Store<EntityStore> store,
                                      @Nonnull Ref<EntityStore> ref,
                                      @Nonnull PlaytimeUI.Tab tab) {
        if (!config.isPlaytimeGuiEnabled()) {
            return false;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return false;
        }
        PlaytimeUI ui = new PlaytimeUI(playerRef, playtime, storage, config, tab);
        ui.open(player, ref, store);
        return true;
    }

    private void sendSelfPlaytime(@Nonnull CommandContext context, @Nonnull PlayerRef playerRef) {
        long seconds = playtime.getPlaytimeSeconds(playerRef.getUuid());
        String formatted = TimeUtil.formatDurationSeconds(seconds);
        Messages.sendKey(context, "playtime.self", Map.of("time", formatted));
    }

    private void sendOtherPlaytime(@Nonnull CommandContext context, @Nonnull String targetName) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), OTHERS_PERMISSION)) {
            Messages.noPerm(context, "/playtime <player>");
            return;
        }
        PlayerRef online = Universe.get().getPlayerByUsername(targetName, NameMatching.EXACT_IGNORE_CASE);
        UUID uuid = online != null ? online.getUuid() : storage.resolvePlayerIdByName(targetName);
        if (uuid == null) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }

        String displayName = resolveDisplayName(online, uuid, targetName);
        long seconds = playtime.getPlaytimeSeconds(uuid);
        String formatted = TimeUtil.formatDurationSeconds(seconds);
        Messages.okKey(context, "playtime.other", Map.of("player", displayName, "time", formatted));
    }

    private void sendRewardsList(@Nonnull CommandContext context, @Nonnull PlayerRef playerRef) {
        List<PlaytimeRewardModel> rewardList = new ArrayList<>(config.getPlaytimeRewards());
        if (rewardList.isEmpty()) {
            Messages.warnKey(context, "playtime.rewards.none", Map.of());
            return;
        }
        rewardList.sort(Comparator.comparingLong(PlaytimeRewardModel::getRequiredSeconds)
                .thenComparing(v -> v.getId().toLowerCase(Locale.ROOT)));

        PlayerDataModel data = storage.getPlayerData(playerRef.getUuid());
        long currentPlaytime = playtime.getPlaytimeSeconds(playerRef.getUuid());
        Messages.sendKey(context, "playtime.rewards.header", Map.of());

        for (PlaytimeRewardModel reward : rewardList) {
            boolean claimed = data.hasClaimedPlaytimeReward(reward.getId());
            boolean ready = !claimed && currentPlaytime >= reward.getRequiredSeconds();
            String status;
            if (claimed) {
                status = "&7Claimed";
            } else if (ready) {
                status = reward.isAutoClaim() ? "&aReady" : "&bReady (/rankup)";
            } else {
                status = "&eLocked";
            }

            String base = Messages.tr(null, "playtime.rewards.entry", Map.of(
                    "id", reward.getId(),
                    "required", TimeUtil.formatDurationSeconds(reward.getRequiredSeconds()),
                    "status", status
            ));
            if (reward.getRequiredCost() > 0L) {
                base = base + " &7| Cost: &f" + reward.getRequiredCost();
            }
            if (!reward.getRank().isBlank()) {
                base = base + " &7| Rank: &f" + reward.getRank();
            }
            Messages.send(context, base);
        }
    }

    private void sendTopList(@Nonnull CommandContext context) {
        List<PlaytimeEntry> entries = collectPlaytimeEntries();
        if (entries.isEmpty()) {
            Messages.warnKey(context, "playtime.top.none", Map.of());
            return;
        }
        Messages.sendKey(context, "playtime.top.header", Map.of());
        int limit = Math.min(entries.size(), Math.max(1, Math.min(CHAT_TOP_LIMIT, config.getPlaytimeTopLimit())));
        for (int i = 0; i < limit; i++) {
            PlaytimeEntry entry = entries.get(i);
            Messages.send(context, Messages.tr(null, "playtime.top.entry", Map.of(
                    "index", String.valueOf(i + 1),
                    "player", entry.name,
                    "time", TimeUtil.formatDurationSeconds(entry.playtimeSeconds)
            )));
        }
    }

    private void openAdminPanel(@Nonnull CommandContext context,
                                @Nonnull PlayerRef playerRef,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull Ref<EntityStore> ref) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), ADMIN_PERMISSION)) {
            Messages.noPerm(context, "/playtime admin");
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.errKey(context, "error.player_only", Map.of());
            return;
        }
        PlaytimeAdminUI ui = new PlaytimeAdminUI(playerRef, playtime, rewards, rankups, storage, config);
        ui.open(player, ref, store);
    }

    @Nonnull
    private List<PlaytimeEntry> collectPlaytimeEntries() {
        Set<UUID> ids = new java.util.HashSet<>(storage.listPlayerIds());
        Universe universe = Universe.get();
        if (universe != null) {
            for (PlayerRef online : universe.getPlayers()) {
                if (online != null) {
                    ids.add(online.getUuid());
                }
            }
        }

        List<PlaytimeEntry> out = new ArrayList<>();
        for (UUID id : ids) {
            long seconds = playtime.getPlaytimeSeconds(id);
            String displayName = resolveDisplayName(universe == null ? null : universe.getPlayer(id), id, id.toString());
            out.add(new PlaytimeEntry(displayName, seconds));
        }
        out.sort(Comparator.comparingLong((PlaytimeEntry entry) -> entry.playtimeSeconds).reversed()
                .thenComparing(entry -> entry.name.toLowerCase(Locale.ROOT)));
        return out;
    }

    @Nonnull
    private String resolveDisplayName(@Nullable PlayerRef online,
                                      @Nonnull UUID uuid,
                                      @Nonnull String fallback) {
        if (online != null && online.getUsername() != null && !online.getUsername().isBlank()) {
            return online.getUsername();
        }
        PlayerDataModel data = storage.getPlayerData(uuid);
        String name = data.getLastKnownName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return fallback;
    }

    private static final class PlaytimeEntry {
        private final String name;
        private final long playtimeSeconds;

        private PlaytimeEntry(@Nonnull String name, long playtimeSeconds) {
            this.name = name;
            this.playtimeSeconds = Math.max(0L, playtimeSeconds);
        }
    }
}
