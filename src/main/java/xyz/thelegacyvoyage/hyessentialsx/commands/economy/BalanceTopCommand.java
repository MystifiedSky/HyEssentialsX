package xyz.thelegacyvoyage.hyessentialsx.commands.economy;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.ui.BalTopUI;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ExplicitPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BalanceTopCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.baltop";
    private static final int DEFAULT_LIMIT = 10;

    private final EconomyManager economy;
    private final StorageManager storage;
    private final ConfigManager config;

    public BalanceTopCommand(@Nonnull EconomyManager economy, @Nonnull StorageManager storage, @Nonnull ConfigManager config) {
        super("baltop", "Shows the top balances");
        this.economy = economy;
        this.storage = storage;
        this.config = config;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addUsageVariant(new LimitedBalanceTopCommand());
        this.addAliases(new String[]{"balancetop"});
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
            Messages.noPerm(context, "/baltop");
            return;
        }
        if (!economy.isEnabled()) {
            Messages.errKey(context, "economy.disabled", Map.of());
            return;
        }

        if (config.isEconomyBaltopGuiEnabled()) {
            Player playerEntity = store.getComponent(ref, Player.getComponentType());
            if (playerEntity != null) {
                BalTopUI ui = new BalTopUI(playerRef, economy, storage);
                ui.open(playerEntity, ref, store);
                return;
            }
        }

        renderTop(context, DEFAULT_LIMIT);
    }

    private void renderTop(@Nonnull CommandContext context, int requestedLimit) {
        List<BalanceEntry> entries = new ArrayList<>();
        for (UUID uuid : storage.listPlayerIds()) {
            if (isExempt(uuid)) {
                continue;
            }
            PlayerRef online = Universe.get().getPlayer(uuid);
            String name = online != null ? online.getUsername() : null;
            PlayerDataModel data = storage.getPlayerData(uuid);
            if (name == null || name.isBlank()) {
                name = data.getLastKnownName();
            }
            if (name == null || name.isBlank()) {
                name = uuid.toString();
            }
            long balance = Math.max(0L, data.getBalance());
            entries.add(new BalanceEntry(name, balance));
        }

        if (entries.isEmpty()) {
            context.sendMessage(Messages.m(Messages.tr(null, "economy.baltop.none", Map.of())));
            return;
        }

        entries.sort(Comparator.comparingLong((BalanceEntry entry) -> entry.balance).reversed()
                .thenComparing(entry -> entry.name.toLowerCase()));

        context.sendMessage(Messages.m(Messages.tr(null, "economy.baltop.header", Map.of())));
        int limit = Math.min(Math.max(1, requestedLimit), entries.size());
        for (int i = 0; i < limit; i++) {
            BalanceEntry entry = entries.get(i);
            String line = Messages.tr(null, "economy.baltop.entry", Map.of(
                    "index", String.valueOf(i + 1),
                    "player", entry.name,
                    "amount", economy.formatAmount(entry.balance)
            ));
            context.sendMessage(Messages.m(line));
        }
    }

    private boolean isExempt(@Nonnull UUID uuid) {
        return ExplicitPermissionUtil.hasExplicitPermission(uuid, BalTopUI.EXEMPT_PERMISSION);
    }

    private final class LimitedBalanceTopCommand extends AbstractPlayerCommand {
        private final RequiredArg<Integer> limitArg;

        private LimitedBalanceTopCommand() {
            super("Shows a custom number of top balances");
            this.limitArg = withRequiredArg("limit", "Number of balances to show", ArgTypes.INTEGER);
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
                Messages.noPerm(context, "/baltop");
                return;
            }
            if (!economy.isEnabled()) {
                Messages.errKey(context, "economy.disabled", Map.of());
                return;
            }
            Integer limit = context.get(limitArg);
            renderTop(context, limit == null ? DEFAULT_LIMIT : limit);
        }
    }

    private static final class BalanceEntry {
        private final String name;
        private final long balance;

        private BalanceEntry(@Nonnull String name, long balance) {
            this.name = name;
            this.balance = balance;
        }
    }
}

