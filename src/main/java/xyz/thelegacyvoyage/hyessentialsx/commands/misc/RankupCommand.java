package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.PlaytimeManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.PlaytimeRewardManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.RankupManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.RankupTier;
import xyz.thelegacyvoyage.hyessentialsx.ui.PlaytimeAdminUI;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.Map;

public final class RankupCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.rankup";
    private static final String ADMIN_PERMISSION = "hyessentialsx.playtime.admin";
    private final RankupManager rankups;
    private final EconomyManager economy;
    private final PlaytimeManager playtime;
    private final PlaytimeRewardManager rewards;
    private final StorageManager storage;
    private final ConfigManager config;

    public RankupCommand(@Nonnull RankupManager rankups,
                         @Nonnull EconomyManager economy,
                         @Nonnull PlaytimeManager playtime,
                         @Nonnull PlaytimeRewardManager rewards,
                         @Nonnull StorageManager storage,
                         @Nonnull ConfigManager config) {
        super("rankup", "Ranks up to the next rank");
        this.addAliases(new String[]{"rewards"});
        this.rankups = rankups;
        this.economy = economy;
        this.playtime = playtime;
        this.rewards = rewards;
        this.storage = storage;
        this.config = config;
        this.setPermissionGroup(null);
        setAllowsExtraArguments(true);
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
            @Nonnull PlayerRef player,
            @Nonnull World world
    ) {
        var args = CommandInputUtil.getArgs(context);
        if (!args.isEmpty() && "admin".equalsIgnoreCase(args.get(0))) {
            openAdminPanel(context, player, store, ref);
            return;
        }
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
            Messages.noPerm(context, "/rankup");
            return;
        }
        if (!rankups.isEnabled()) {
            Messages.warnKey(context, "rankup.disabled", Map.of());
            return;
        }

        if (!args.isEmpty() && "confirm".equalsIgnoreCase(args.get(0))) {
            RankupTier pending = rankups.getPendingTier(player.getUuid());
            if (pending == null) {
                Messages.warnKey(context, "rankup.confirm.expired", Map.of());
                return;
            }
            if (!rankups.performRankup(player, pending, true)) {
                Messages.warnKey(context, "rankup.not_ready", Map.of());
            }
            rankups.clearPending(player.getUuid());
            return;
        }

        RankupTier next = rankups.getNextTier(player.getUuid());
        if (next == null) {
            Messages.warnKey(context, "rankup.maxed", Map.of());
            return;
        }

        RankupManager.Eligibility eligibility = rankups.checkEligibility(player, next);
        boolean missingPlaytime = !eligibility.playtimeMet;
        boolean missingMoney = eligibility.cost > 0L && !eligibility.currencyMet;
        if (missingPlaytime) {
            Messages.warnKey(context, "rankup.playtime_needed", Map.of(
                    "required", formatHours(next.getPlaytimeSeconds()),
                    "current", formatHours(eligibility.playtimeSeconds)
            ));
        }
        if (missingMoney) {
            long balance = economy.getBalance(player.getUuid());
            Messages.warnKey(context, "rankup.money_needed", Map.of(
                    "cost", economy.formatAmount(eligibility.cost),
                    "balance", economy.formatAmount(balance)
            ));
        }
        if (missingPlaytime || missingMoney) {
            return;
        }

        if (eligibility.cost > 0L) {
            rankups.setPendingConfirm(player.getUuid(), next);
            Messages.sendPrefixedKey(player, "rankup.confirm", Map.of(
                    "rank", next.getRank(),
                    "cost", economy.formatAmount(next.getCost())
            ));
        } else {
            rankups.performRankup(player, next, false);
        }
    }

    private void openAdminPanel(@Nonnull CommandContext context,
                                @Nonnull PlayerRef playerRef,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull Ref<EntityStore> ref) {
        if (!context.sender().hasPermission(ADMIN_PERMISSION)) {
            Messages.noPerm(context, "/rankup admin");
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
    private static String formatHours(long seconds) {
        double hours = Math.max(0.0, seconds / 3600.0);
        if (hours < 1.0) {
            return String.format(Locale.US, "%.2f", hours);
        }
        if (hours < 10.0) {
            return String.format(Locale.US, "%.1f", hours);
        }
        return String.format(Locale.US, "%.0f", hours);
    }
}

