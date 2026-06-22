package xyz.thelegacyvoyage.hyessentialsx.commands.economy;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.Player;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyAuditManager;
import xyz.thelegacyvoyage.hyessentialsx.ui.PayUI;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public final class PayCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.pay";

    private final EconomyManager economy;
    @Nullable
    private final EconomyAuditManager audit;
    private final OptionalArg<PlayerRef> targetArg;
    private final OptionalArg<String> amountArg;

    public PayCommand(@Nonnull EconomyManager economy) {
        this(economy, null);
    }

    public PayCommand(@Nonnull EconomyManager economy,
                      @Nullable EconomyAuditManager audit) {
        super("pay", "Pays another player");
        this.economy = economy;
        this.audit = audit;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.targetArg = withOptionalArg("player", "Player to pay", ArgTypes.PLAYER_REF);
        this.amountArg = withOptionalArg("amount", "Amount to pay", ArgTypes.STRING);
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
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/pay");
            return;
        }
        if (!economy.isEnabled()) {
            Messages.errKey(context, "economy.disabled", Map.of());
            return;
        }

        if (!context.provided(targetArg) && !context.provided(amountArg)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null || audit == null) {
                Messages.errKey(context, "economy.pay.usage", Map.of());
                return;
            }
            PayUI ui = new PayUI(playerRef, economy, audit);
            ui.open(player, ref, store);
            return;
        }
        if (!context.provided(targetArg) || !context.provided(amountArg)) {
            Messages.errKey(context, "economy.pay.usage", Map.of());
            return;
        }

        PlayerRef target = context.get(targetArg);
        String amountRaw = context.get(amountArg);
        long amount = economy.parseAmount(amountRaw);
        if (amount <= 0L) {
            Messages.errKey(context, "economy.invalid_amount", Map.of());
            return;
        }

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
        if (audit != null) {
            audit.log(
                    "PAY",
                    playerRef.getUsername(),
                    target.getUsername(),
                    amount,
                    economy.getBalance(playerRef.getUuid()),
                    "command"
            );
        }
    }

}

