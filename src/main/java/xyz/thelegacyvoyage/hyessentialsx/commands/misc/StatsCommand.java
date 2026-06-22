package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.StatsManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.ui.StatsUI;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandSenderUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class StatsCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.stats";
    private static final String OTHER_PERMISSION = "hyessentialsx.stats.other";
    private static final int CATEGORY_LIMIT = 8;

    private final StatsManager stats;
    private final StorageManager storage;
    private final OptionalArg<String> firstArg;
    private final OptionalArg<String> secondArg;

    public StatsCommand(@Nonnull StatsManager stats, @Nonnull StorageManager storage) {
        super("stats", "View player statistics");
        this.stats = stats;
        this.storage = storage;
        this.setPermissionGroups();
        CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"statistics"});
        this.firstArg = withOptionalArg("player_or_category", "Player name or category", ArgTypes.STRING);
        this.secondArg = withOptionalArg("category", "Category", ArgTypes.STRING);
        this.firstArg.suggest(this::suggestCategories);
        this.secondArg.suggest(this::suggestCategories);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/stats");
            return;
        }
        if (!stats.isEnabled()) {
            Messages.errKey(context, "stats.disabled", Map.of());
            return;
        }

        PlayerRef sender = CommandSenderUtil.resolvePlayer(context);
        String first = context.provided(firstArg) ? context.get(firstArg) : null;
        String second = context.provided(secondArg) ? context.get(secondArg) : null;

        UUID targetId;
        String targetName;
        String category = null;
        StatsUI.Tab initialTab = StatsUI.Tab.GENERAL;

        if (first == null || first.isBlank()) {
            if (sender == null) {
                Messages.errKey(context, "stats.usage", Map.of());
                return;
            }
            targetId = sender.getUuid();
            targetName = sender.getUsername();
        } else if (isCategory(first)) {
            if (sender == null) {
                Messages.errKey(context, "stats.usage", Map.of());
                return;
            }
            targetId = sender.getUuid();
            targetName = sender.getUsername();
            category = first;
            initialTab = tabForCategory(first);
        } else {
            if (!CommandPermissionUtil.hasPermission(context.sender(), OTHER_PERMISSION)) {
                Messages.noPerm(context, "/stats <player>");
                return;
            }
            PlayerRef online = Universe.get().getPlayerByUsername(first, NameMatching.EXACT_IGNORE_CASE);
            targetId = online != null ? online.getUuid() : storage.resolvePlayerIdByName(first);
            if (targetId == null) {
                Messages.errKey(context, "player.not_found", Map.of());
                return;
            }
            targetName = online != null ? online.getUsername() : resolveStoredName(targetId, first);
            if (second != null && !second.isBlank()) {
                category = second;
                initialTab = tabForCategory(second);
            }
        }

        if (sender != null && openStatsUi(sender, targetId, targetName, initialTab)) {
            return;
        }

        if (category != null && !category.isBlank()) {
            sendCategory(context, targetId, targetName, category);
            return;
        }
        sendSummary(context, targetId, targetName);
    }

    private void sendSummary(@Nonnull CommandContext context, @Nonnull UUID targetId, @Nonnull String targetName) {
        Messages.sendKey(context, "stats.header", Map.of("player", targetName));
        Messages.sendKey(context, "stats.line.play_time", Map.of(
                "value", TimeUtil.formatDurationSeconds(stats.get(targetId, StatsManager.CATEGORY_CUSTOM, "play_time"))
        ));
        Messages.sendKey(context, "stats.line.basic", Map.of(
                "connections", stats.formatNumber(stats.get(targetId, StatsManager.CATEGORY_CUSTOM, "times_connected")),
                "messages", stats.formatNumber(stats.get(targetId, StatsManager.CATEGORY_CUSTOM, "messages_sent")),
                "deaths", stats.formatNumber(stats.get(targetId, StatsManager.CATEGORY_CUSTOM, "deaths"))
        ));
        Messages.sendKey(context, "stats.line.combat", Map.of(
                "player_kills", stats.formatNumber(stats.get(targetId, StatsManager.CATEGORY_CUSTOM, "player_kills")),
                "mob_kills", stats.formatNumber(stats.get(targetId, StatsManager.CATEGORY_CUSTOM, "mob_kills")),
                "damage_dealt", stats.formatNumber(stats.get(targetId, StatsManager.CATEGORY_CUSTOM, "damage_dealt")),
                "damage_taken", stats.formatNumber(stats.get(targetId, StatsManager.CATEGORY_CUSTOM, "damage_taken"))
        ));
        Messages.sendKey(context, "stats.line.world", Map.of(
                "distance", stats.formatDistance(stats.get(targetId, StatsManager.CATEGORY_CUSTOM, "distance_traveled"))
        ));
        Messages.sendKey(context, "stats.footer", Map.of());
    }

    private void sendCategory(@Nonnull CommandContext context,
                              @Nonnull UUID targetId,
                              @Nonnull String targetName,
                              @Nonnull String category) {
        String normalized = category.toLowerCase(Locale.ROOT);
        List<Map.Entry<String, Long>> entries = stats.topStats(targetId, normalized, CATEGORY_LIMIT);
        if (entries.isEmpty()) {
            Messages.warnKey(context, "stats.category.empty", Map.of("category", normalized));
            return;
        }
        Messages.sendKey(context, "stats.category.header", Map.of(
                "player", targetName,
                "category", normalized
        ));
        for (Map.Entry<String, Long> entry : entries) {
            Messages.sendKey(context, "stats.category.line", Map.of(
                    "name", stats.displayName(entry.getKey()),
                    "value", stats.formatNumber(entry.getValue())
            ));
        }
    }

    @Nonnull
    private String resolveStoredName(@Nonnull UUID uuid, @Nonnull String fallback) {
        PlayerDataModel data = storage.getPlayerData(uuid);
        String stored = data.getLastKnownName();
        return stored == null || stored.isBlank() ? fallback : stored;
    }

    private boolean isCategory(@Nonnull String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return "general".equals(normalized)
                || "items".equals(normalized)
                || "mobs".equals(normalized)
                || "custom".equals(normalized)
                || StatsManager.CATEGORY_MINED.equals(normalized)
                || StatsManager.CATEGORY_PLACED.equals(normalized)
                || StatsManager.CATEGORY_KILLED.equals(normalized)
                || StatsManager.CATEGORY_KILLED_BY.equals(normalized);
    }

    private boolean openStatsUi(@Nonnull PlayerRef viewer,
                                @Nonnull UUID targetId,
                                @Nonnull String targetName,
                                @Nonnull StatsUI.Tab initialTab) {
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
            new StatsUI(viewer, targetId, targetName, stats, initialTab).open(player, ref, store);
        });
        return true;
    }

    @Nonnull
    private StatsUI.Tab tabForCategory(@Nonnull String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if ("items".equals(normalized)
                || StatsManager.CATEGORY_MINED.equals(normalized)
                || StatsManager.CATEGORY_PLACED.equals(normalized)
                || "crafted".equals(normalized)
                || "picked_up".equals(normalized)
                || "dropped".equals(normalized)) {
            return StatsUI.Tab.ITEMS;
        }
        if ("mobs".equals(normalized)
                || StatsManager.CATEGORY_KILLED.equals(normalized)
                || StatsManager.CATEGORY_KILLED_BY.equals(normalized)) {
            return StatsUI.Tab.MOBS;
        }
        return StatsUI.Tab.GENERAL;
    }

    private void suggestCategories(com.hypixel.hytale.server.core.command.system.CommandSender sender,
                                   String input,
                                   int offset,
                                   com.hypixel.hytale.server.core.command.system.suggestion.SuggestionResult result) {
        String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        for (String category : List.of("general", "items", "mobs", "custom", "mined", "placed", "killed", "killed_by")) {
            if (normalized.isEmpty() || category.startsWith(normalized)) {
                result.suggest(category);
            }
        }
    }
}
