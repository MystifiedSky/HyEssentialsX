package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.LeaderboardDefinition;
import xyz.thelegacyvoyage.hyessentialsx.managers.StatsManager;
import xyz.thelegacyvoyage.hyessentialsx.ui.LeaderboardUI;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandSenderUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public final class LeaderboardCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.leaderboard";
    private static final int MAX_LIMIT = 25;

    private final StatsManager stats;
    private final OptionalArg<String> statArg;
    private final OptionalArg<Integer> limitArg;

    public LeaderboardCommand(@Nonnull StatsManager stats) {
        super("leaderboard", "Shows stat leaderboards");
        this.stats = stats;
        this.setPermissionGroups();
        CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"leaderboards", "lb"});
        this.statArg = withOptionalArg("stat", "Stat to rank", ArgTypes.STRING);
        this.limitArg = withOptionalArg("limit", "Number of players to show", ArgTypes.INTEGER);
        this.statArg.suggest(this::suggestStats);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/leaderboard");
            return;
        }
        if (!stats.isEnabled()) {
            Messages.errKey(context, "stats.disabled", Map.of());
            return;
        }

        String statInput = context.provided(statArg) ? context.get(statArg) : null;
        Integer requestedLimit = context.provided(limitArg) ? context.get(limitArg) : null;
        if (statInput != null && statInput.matches("\\d+")) {
            requestedLimit = parsePositive(statInput);
            statInput = null;
        }

        LeaderboardDefinition selection = LeaderboardDefinition.resolve(statInput);
        if (selection == null) {
            Messages.errKey(context, "leaderboard.invalid_stat", Map.of());
            return;
        }

        PlayerRef playerRef = CommandSenderUtil.resolvePlayer(context);
        if (playerRef != null && openUi(playerRef, selection)) {
            return;
        }

        int limit = 10;
        if (requestedLimit != null && requestedLimit > 0) {
            limit = Math.min(MAX_LIMIT, requestedLimit);
        }

        List<StatsManager.LeaderboardEntry> entries = stats.topPlayers(
                selection.category(),
                selection.stat(),
                limit,
                LeaderboardUI.EXEMPT_PERMISSION
        );
        if (entries.isEmpty()) {
            Messages.warnKey(context, "leaderboard.empty", Map.of("stat", selection.display()));
            return;
        }

        Messages.sendKey(context, "leaderboard.header", Map.of("stat", selection.display()));
        for (int i = 0; i < entries.size(); i++) {
            StatsManager.LeaderboardEntry entry = entries.get(i);
            Messages.sendKey(context, "leaderboard.line", Map.of(
                    "index", String.valueOf(i + 1),
                    "player", entry.playerName(),
                    "value", formatValue(selection, entry.value())
            ));
        }
    }

    @Nonnull
    private String formatValue(@Nonnull LeaderboardDefinition selection, long value) {
        if (selection.distance()) {
            return stats.formatDistance(value);
        }
        return stats.formatNumber(value);
    }

    private boolean openUi(@Nonnull PlayerRef viewer, @Nonnull LeaderboardDefinition selection) {
        Ref<EntityStore> ref = viewer.getReference();
        if (ref == null || !ref.isValid()) {
            return false;
        }
        if (viewer.getWorldUuid() == null) {
            return false;
        }
        World world = Universe.get().getWorld(viewer.getWorldUuid());
        if (world == null) {
            return false;
        }
        world.execute(() -> {
            if (!ref.isValid()) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            if (store == null) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            new LeaderboardUI(viewer, stats, selection).open(player, ref, store);
        });
        return true;
    }

    private Integer parsePositive(@Nonnull String input) {
        try {
            int value = Integer.parseInt(input);
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void suggestStats(com.hypixel.hytale.server.core.command.system.CommandSender sender,
                              String input,
                              int offset,
                              com.hypixel.hytale.server.core.command.system.suggestion.SuggestionResult result) {
        String normalized = input == null ? "" : input.trim().toLowerCase(java.util.Locale.ROOT);
        for (String stat : List.of(
                "player_kills", "mob_kills", "deaths", "damage_dealt", "damage_taken",
                "distance_traveled", "messages_sent", "times_connected", "drops",
                "mined:<id>", "placed:<id>", "crafted:<id>", "dropped:<id>",
                "picked_up:<id>", "killed:<id>", "killed_by:<id>"
        )) {
            if (normalized.isEmpty() || stat.startsWith(normalized)) {
                result.suggest(stat);
            }
        }
    }
}
