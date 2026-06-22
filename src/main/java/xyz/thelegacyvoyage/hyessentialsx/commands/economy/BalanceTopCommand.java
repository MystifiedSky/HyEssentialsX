package xyz.thelegacyvoyage.hyessentialsx.commands.economy;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.ui.BalTopUI;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
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
        this.setPermissionGroup(null);
        this.setAllowsExtraArguments(true);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
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
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
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

        List<BalanceEntry> entries = new ArrayList<>();
        for (UUID uuid : storage.listPlayerIds()) {
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
        int limit = resolveLimit(context, entries.size());
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

    private int resolveLimit(@Nonnull CommandContext context, int max) {
        List<String> args = CommandInputUtil.getArgs(context);
        if (!args.isEmpty()) {
            String raw = args.get(0);
            if (raw != null && raw.matches("\\d+")) {
                try {
                    int value = Integer.parseInt(raw);
                    if (value > 0) {
                        return Math.min(value, max);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return Math.min(DEFAULT_LIMIT, max);
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

