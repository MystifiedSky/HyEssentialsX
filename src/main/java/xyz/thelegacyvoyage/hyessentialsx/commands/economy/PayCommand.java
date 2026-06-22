package xyz.thelegacyvoyage.hyessentialsx.commands.economy;

import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public final class PayCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.pay";

    private final EconomyManager economy;
    public PayCommand(@Nonnull EconomyManager economy) {
        super("pay", "Pays another player");
        this.economy = economy;
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
            Messages.noPerm(context, "/pay");
            return;
        }
        if (!economy.isEnabled()) {
            Messages.errKey(context, "economy.disabled", Map.of());
            return;
        }

        List<String> args = CommandInputUtil.getArgs(context);
        if (args.size() < 2) {
            Messages.errKey(context, "economy.pay.usage", Map.of());
            return;
        }

        String targetName = args.get(0);
        String amountRaw = args.get(1);
        long amount = parseAmount(amountRaw);
        if (amount <= 0L) {
            Messages.errKey(context, "economy.invalid_amount", Map.of());
            return;
        }

        PlayerRef target = Universe.get().getPlayerByUsername(targetName, NameMatching.EXACT_IGNORE_CASE);
        if (target == null) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }

        if (playerRef.getUuid().equals(target.getUuid())) {
            Messages.errKey(context, "economy.pay.self", Map.of());
            return;
        }

        long balance = economy.getBalance(playerRef.getUuid());
        if (balance < amount) {
            Messages.errKey(context, "economy.insufficient_funds", Map.of());
            return;
        }

        if (!economy.withdraw(playerRef.getUuid(), amount)) {
            Messages.errKey(context, "economy.insufficient_funds", Map.of());
            return;
        }
        economy.deposit(target.getUuid(), amount);

        String formatted = economy.formatAmount(amount);
        Messages.okKey(context, "economy.pay.sent", Map.of(
                "player", target.getUsername(),
                "amount", formatted
        ));
        Messages.sendPrefixedKey(target, "economy.pay.received", Map.of(
                "player", playerRef.getUsername(),
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
}

