package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3i;
import xyz.thelegacyvoyage.hyessentialsx.managers.AuctionHouseManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopNpcModel;
import xyz.thelegacyvoyage.hyessentialsx.ui.AuctionHouseUI;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.ShopNpcEntityUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public final class AuctionHouseCommand extends AbstractPlayerCommand {

    private static final String USE_PERMISSION = "hyessentialsx.auctionhouse.use";
    private static final String ADMIN_PERMISSION = "hyessentialsx.auctionhouse.admin";

    private final AuctionHouseManager manager;
    private final EconomyManager economy;
    private final ConfigManager config;

    public AuctionHouseCommand(@Nonnull AuctionHouseManager manager,
                               @Nonnull EconomyManager economy,
                               @Nonnull ConfigManager config) {
        super("auctionhouse", "Open the auction house");
        this.manager = manager;
        this.economy = economy;
        this.config = config;
        this.setPermissionGroups();
        this.addAliases(new String[]{"auction"});
        this.addSubCommand(new NpcSubCommand());
        CommandPermissionUtil.apply(this, USE_PERMISSION);
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
        if (!checkEnabled(context)) return;
        if (!CommandPermissionUtil.hasPermission(context.sender(), USE_PERMISSION)) {
            Messages.noPerm(context, "/auctionhouse");
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.errKey(context, "auction.ui_failed", Map.of());
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new AuctionHouseUI(playerRef, manager, economy, config));
    }

    private boolean checkEnabled(@Nonnull CommandContext context) {
        if (!manager.isEnabled()) {
            Messages.errKey(context, "auction.disabled", Map.of());
            return false;
        }
        return true;
    }

    private final class NpcSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> actionArg;

        private NpcSubCommand() {
            super("npc", "Manage auction house NPCs");
            this.actionArg = withRequiredArg("action", "spawn, remove, or list", ArgTypes.STRING);
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
            if (!checkEnabled(context)) return;
            if (!CommandPermissionUtil.hasPermission(context.sender(), ADMIN_PERMISSION)) {
                Messages.noPerm(context, "/auctionhouse npc");
                return;
            }
            String action = context.get(actionArg);
            if (action == null) {
                Messages.errKey(context, "auction.npc.usage", Map.of());
                return;
            }
            switch (action.toLowerCase(java.util.Locale.ROOT)) {
                case "spawn", "create" -> spawnNpc(context, store, ref, playerRef, world);
                case "remove", "delete" -> removeNpc(context, store, ref, playerRef, world);
                case "list" -> listNpcs(context);
                default -> Messages.errKey(context, "auction.npc.usage", Map.of());
            }
        }
    }

    private void spawnNpc(@Nonnull CommandContext context,
                          @Nonnull Store<EntityStore> store,
                          @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef playerRef,
                          @Nonnull World world) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            Messages.errKey(context, "auction.npc.position_failed", Map.of());
            return;
        }
        Vector3d pos = transform.getPosition();
        Vector3i basePos = new Vector3i((int) Math.floor(pos.x()), (int) Math.floor(pos.y()), (int) Math.floor(pos.z()));
        Vector3d spawnPos = new Vector3d(basePos.x() + 0.5D, basePos.y(), basePos.z() + 0.5D);
        com.hypixel.hytale.math.vector.Rotation3f rotation =
                transform.getRotation() == null ? new com.hypixel.hytale.math.vector.Rotation3f(0f, 0f, 0f) : transform.getRotation();
        ShopNpcEntityUtil.RoleSelection role = ShopNpcEntityUtil.resolveRoleSelection(config.getAuctionHouseNpcRole());
        if (role == null) {
            Messages.errKey(context, "auction.npc.no_roles", Map.of());
            return;
        }
        ShopModel shop = manager.toNpcShopModel();
        ShopNpcEntityUtil.NpcLifecycleResult result = ShopNpcEntityUtil.moveOrSpawnShopNpc(
                world, store, shop, null, spawnPos, rotation, basePos, "Auction House",
                playerRef.getUuid().toString(), playerRef.getUsername() == null ? "" : playerRef.getUsername(),
                role.roleName(), role.roleIndex()
        );
        if (!result.success()) {
            Messages.errKey(context, "auction.npc.spawn_failed", Map.of());
            return;
        }
        manager.updateNpcsFromShop(shop);
        Messages.okKey(context, "auction.npc.spawned", Map.of());
    }

    private void removeNpc(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            Messages.errKey(context, "auction.npc.position_failed", Map.of());
            return;
        }
        Vector3d pos = transform.getPosition();
        ShopModel shop = manager.toNpcShopModel();
        boolean removed = false;
        for (ShopNpcModel npc : List.copyOf(shop.getNpcs())) {
            if (!npc.getWorldId().equalsIgnoreCase(world.getName())) continue;
            if (distance(pos, npc.getPosition()) > 5.0D) continue;
            if (ShopNpcEntityUtil.removeAllMatchingShopNpcs(store, shop, npc) > 0) {
                shop.getNpcs().removeIf(existing -> existing.getNpcId().equalsIgnoreCase(npc.getNpcId()));
                removed = true;
            }
        }
        if (!removed) {
            Messages.errKey(context, "auction.npc.none_nearby", Map.of());
            return;
        }
        manager.updateNpcsFromShop(shop);
        Messages.okKey(context, "auction.npc.removed", Map.of());
    }

    private void listNpcs(@Nonnull CommandContext context) {
        List<ShopNpcModel> npcs = manager.getNpcs();
        if (npcs.isEmpty()) {
            Messages.errKey(context, "auction.npc.none_registered", Map.of());
            return;
        }
        Messages.okKey(context, "auction.npc.list", Map.of(
                "ids", String.join(", ", npcs.stream().map(ShopNpcModel::getNpcId).toList())
        ));
    }

    private static double distance(@Nonnull Vector3d playerPos, @Nonnull Vector3i blockPos) {
        double dx = playerPos.x() - (blockPos.x() + 0.5D);
        double dy = playerPos.y() - blockPos.y();
        double dz = playerPos.z() - (blockPos.z() + 0.5D);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

}
