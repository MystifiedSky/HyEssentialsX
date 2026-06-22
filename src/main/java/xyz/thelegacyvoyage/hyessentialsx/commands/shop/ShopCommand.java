package xyz.thelegacyvoyage.hyessentialsx.commands.shop;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopAdminDraftCache;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopManager;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.ui.ShopAdminUI;
import xyz.thelegacyvoyage.hyessentialsx.ui.ShopBrowseUI;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public final class ShopCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.shop";
    private static final String ADMIN_PERMISSION = "hyessentialsx.shop.admin";

    private final ShopManager shopManager;
    private final EconomyManager economy;
    private final ShopAdminDraftCache draftCache;

    public ShopCommand(@Nonnull ShopManager shopManager,
                       @Nonnull EconomyManager economy,
                       @Nonnull ShopAdminDraftCache draftCache) {
        super("adminshop", "Open or manage admin shops");
        this.shopManager = shopManager;
        this.economy = economy;
        this.draftCache = draftCache;
        this.setPermissionGroup(null);
        this.setAllowsExtraArguments(true);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
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
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
            Messages.noPerm(context, "/adminshop");
            return;
        }

        List<String> args = CommandInputUtil.getArgs(context);
        if (args.isEmpty()) {
            listShops(context);
            return;
        }

        String sub = args.get(0).toLowerCase();
        if ("list".equals(sub)) {
            listShops(context);
            return;
        }

        if ("create".equals(sub)) {
            if (!context.sender().hasPermission(ADMIN_PERMISSION)) {
                Messages.noPerm(context, "/adminshop create");
                return;
            }
            if (args.size() < 2) {
                Messages.send(context, "&cUsage: /adminshop create <name>");
                return;
            }
            String name = args.get(1);
            ShopModel created = shopManager.createShop(name);
            if (created == null) {
                Messages.send(context, "&cShop already exists or name invalid.");
                return;
            }
            Messages.send(context, "&aShop created: &f" + created.getDisplayName());
            return;
        }

        if ("delete".equals(sub)) {
            if (!context.sender().hasPermission(ADMIN_PERMISSION)) {
                Messages.noPerm(context, "/adminshop delete");
                return;
            }
            if (args.size() < 2) {
                Messages.send(context, "&cUsage: /adminshop delete <name>");
                return;
            }
            String name = args.get(1);
            if (shopManager.deleteShop(name)) {
                Messages.send(context, "&cShop deleted.");
            } else {
                Messages.send(context, "&cShop not found.");
            }
            return;
        }

        if ("edit".equals(sub)) {
            if (args.size() < 2) {
                Messages.send(context, "&cUsage: /adminshop edit <name>");
                return;
            }
            String name = args.get(1);
            openAdmin(context, store, ref, playerRef, name);
            return;
        }

        openBrowse(context, store, ref, playerRef, args.get(0));
    }

    private void listShops(@Nonnull CommandContext context) {
        List<String> names = shopManager.listShops();
        if (names.isEmpty()) {
            Messages.send(context, "&cNo shops configured.");
            return;
        }
        List<String> display = new ArrayList<>();
        for (String name : names) {
            ShopModel shop = shopManager.getShop(name);
            if (shop != null && !shop.getDisplayName().equalsIgnoreCase(name)) {
                display.add(name + " (" + shop.getDisplayName() + ")");
            } else {
                display.add(name);
            }
        }
        Messages.send(context, "&aShops: &f" + String.join(", ", display));
    }

    private void openBrowse(@Nonnull CommandContext context,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull Ref<EntityStore> ref,
                            @Nonnull PlayerRef playerRef,
                            @Nonnull String name) {
        ShopModel shop = shopManager.getShop(name);
        if (shop == null) {
            Messages.send(context, "&cShop not found.");
            return;
        }
        if (!shop.getUsePermission().isBlank()
                && !context.sender().hasPermission(shop.getUsePermission())) {
            Messages.noPerm(context, "/adminshop " + name);
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.send(context, "&cCould not open shop UI.");
            return;
        }
        ShopBrowseUI ui = new ShopBrowseUI(playerRef, shopManager, economy, shop);
        ui.open(player, ref, store);
    }

    private void openAdmin(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull String name) {
        ShopModel shop = shopManager.getShop(name);
        if (shop == null) {
            Messages.send(context, "&cShop not found.");
            return;
        }
        if (!shop.getEditPermission().isBlank()
                && !context.sender().hasPermission(shop.getEditPermission())) {
            Messages.noPerm(context, "/adminshop edit " + name);
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.send(context, "&cCould not open shop UI.");
            return;
        }
        ShopAdminUI ui = new ShopAdminUI(playerRef, shopManager, economy, shop, draftCache);
        ui.open(player, ref, store);
    }
}
