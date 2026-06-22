package xyz.thelegacyvoyage.hyessentialsx.commands.economy;

import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyAuditManager;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandSenderUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MoneyCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.balance";
    private static final String ADMIN_PERMISSION = "hyessentialsx.money.admin";
    private static final String MONEY_SET_PERMISSION = "hyessentialsx.money.set";
    private static final String MONEY_GIVE_PERMISSION = "hyessentialsx.money.give";
    private static final String BALANCE_OTHERS_PERMISSION = "hyessentialsx.balance.others";

    private final EconomyManager economy;
    private final StorageManager storage;
    @Nullable
    private final EconomyAuditManager audit;

    public MoneyCommand(@Nonnull EconomyManager economy, @Nonnull StorageManager storage) {
        this(economy, storage, null);
    }

    public MoneyCommand(@Nonnull EconomyManager economy,
                        @Nonnull StorageManager storage,
                        @Nullable EconomyAuditManager audit) {
        super("money", "Shows your balance or manages money");
        this.economy = economy;
        this.storage = storage;
        this.audit = audit;
        this.setPermissionGroup(null);
        this.setAllowsExtraArguments(true);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"balance", "bal", "eco", "cash", "wallet"});
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!economy.isEnabled()) {
            Messages.errKey(context, "economy.disabled", Map.of());
            return;
        }

        List<String> args = CommandInputUtil.getArgs(context);
        if (args.isEmpty()) {
            PlayerRef player = CommandSenderUtil.resolvePlayer(context);
            if (player == null) {
                Messages.errKey(context, "error.player_only", Map.of());
                return;
            }
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)
                    && !xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), ADMIN_PERMISSION)) {
                Messages.noPerm(context, "/balance");
                return;
            }
            sendBalance(context, player.getUuid(), player.getUsername());
            return;
        }

        String sub = args.get(0).toLowerCase();
        if (!sub.equals("set") && !sub.equals("give") && args.size() == 1) {
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), BALANCE_OTHERS_PERMISSION)) {
                Messages.noPerm(context, "/balance " + sub);
                return;
            }
            String targetName = args.get(0);
            PlayerRef online = Universe.get().getPlayerByUsername(targetName, NameMatching.EXACT_IGNORE_CASE);
            UUID uuid = online != null ? online.getUuid() : storage.resolvePlayerIdByName(targetName);
            if (uuid == null) {
                Messages.errKey(context, "player.not_found", Map.of());
                return;
            }
            String displayName = resolveDisplayName(online, uuid, targetName);
            long balance = economy.getBalance(uuid);
            Messages.okKey(context, "economy.balance_other", Map.of(
                    "player", displayName,
                    "amount", economy.formatAmount(balance)
            ));
            return;
        }
        if (!sub.equals("set") && !sub.equals("give")) {
            Messages.errKey(context, "economy.money.usage", Map.of());
            return;
        }

        String requiredPerm = sub.equals("set") ? MONEY_SET_PERMISSION : MONEY_GIVE_PERMISSION;
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), requiredPerm) && !xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), ADMIN_PERMISSION)) {
            Messages.noPerm(context, "/money " + sub);
            return;
        }

        if (args.size() < 3) {
            Messages.errKey(context, "economy.money.usage", Map.of());
            return;
        }

        String targetName = args.get(1);
        String amountRaw = args.get(2);
        long amount = parseAmount(amountRaw);
        if (amount < 0L || (amount == 0L && sub.equals("give"))) {
            Messages.errKey(context, "economy.invalid_amount", Map.of());
            return;
        }

        PlayerRef online = Universe.get().getPlayerByUsername(targetName, NameMatching.EXACT_IGNORE_CASE);
        UUID uuid = online != null ? online.getUuid() : storage.resolvePlayerIdByName(targetName);
        if (uuid == null) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }

        String displayName = resolveDisplayName(online, uuid, targetName);
        String formatted = economy.formatAmount(amount);

        if (sub.equals("set")) {
            long updated = economy.setBalance(uuid, amount);
            formatted = economy.formatAmount(updated);
            Messages.okKey(context, "economy.money.set", Map.of(
                    "player", displayName,
                    "amount", formatted
            ));
            if (audit != null) {
                audit.log(
                        "ADMIN_SET",
                        resolveActorName(context),
                        displayName,
                        updated,
                        updated,
                        "command /money set"
                );
            }
            if (online != null) {
                Messages.sendPrefixedKey(online, "economy.money.set_target", Map.of(
                        "player", resolveActorName(context),
                        "amount", formatted
                ));
            }
            return;
        }

        long updated = economy.deposit(uuid, amount);
        Messages.okKey(context, "economy.money.give", Map.of(
                "player", displayName,
                "amount", formatted
        ));
        if (audit != null) {
            audit.log(
                    "ADMIN_GIVE",
                    resolveActorName(context),
                    displayName,
                    amount,
                    updated,
                    "command /money give"
            );
        }
        if (online != null) {
            Messages.sendPrefixedKey(online, "economy.money.give_target", Map.of(
                    "player", resolveActorName(context),
                    "amount", formatted
            ));
        }
    }

    private void sendBalance(@Nonnull CommandContext context, @Nonnull UUID uuid, @Nonnull String name) {
        long balance = economy.getBalance(uuid);
        String formatted = economy.formatAmount(balance);
        Messages.okKey(context, "economy.balance", Map.of(
                "player", name,
                "amount", formatted
        ));
    }

    private static long parseAmount(@Nonnull String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return -1L;
        if (!trimmed.matches("\\d+")) return -1L;
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    @Nonnull
    private String resolveDisplayName(@Nullable PlayerRef online, @Nonnull UUID uuid, @Nonnull String fallback) {
        if (online != null) return online.getUsername();
        PlayerDataModel data = storage.getPlayerData(uuid);
        String name = data.getLastKnownName();
        if (name != null && !name.isBlank()) return name;
        return fallback;
    }

    @Nonnull
    private static String resolveActorName(@Nonnull CommandContext context) {
        Object sender = context.sender();
        if (sender == null) return "Console";
        if (sender instanceof PlayerRef playerRef) return playerRef.getUsername();
        try {
            Method method = sender.getClass().getMethod("getName");
            Object value = method.invoke(sender);
            if (value instanceof String name && !name.isBlank()) return name;
        } catch (Exception ignored) {
        }
        return sender.getClass().getSimpleName();
    }
}

