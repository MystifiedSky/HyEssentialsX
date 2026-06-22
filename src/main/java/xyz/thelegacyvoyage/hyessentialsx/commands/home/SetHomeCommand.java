package xyz.thelegacyvoyage.hyessentialsx.commands.home;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.HomeManager;
import xyz.thelegacyvoyage.hyessentialsx.models.HomeModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class SetHomeCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.sethome";
    private static final String MAX_PERMISSION_PREFIX = "hyessentialsx.sethome.max.";
    private static final int MAX_PERMISSION_SCAN = 1000;

    private final HomeManager homeManager;
    private final ConfigManager config;
    private final RequiredArg<String> nameArg;
    public SetHomeCommand(@Nonnull HomeManager homeManager, @Nonnull ConfigManager config) {
        super("sethome", "Sets a home location");
        this.homeManager = homeManager;
        this.config = config;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"ecreatehome"});
        this.nameArg = withRequiredArg("name", "Home name", ArgTypes.STRING);
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
            Messages.noPerm(context, "/sethome");
            return;
        }
        if (!config.isHomesEnabled()) {
            Messages.errKey(context, "home.disabled", java.util.Map.of());
            return;
        }

        String name = context.get(nameArg);
        if (name == null || name.isBlank()) {
            Messages.errKey(context, "home.name_required", java.util.Map.of());
            return;
        }

        boolean alreadyExists = homeManager.hasHome(playerRef.getUuid(), name);
        if (!alreadyExists) {
            int maxHomes = resolveMaxHomes(context.sender());
            if (maxHomes >= 0) {
                int current = homeManager.getHomeCount(playerRef.getUuid());
                if (current >= maxHomes) {
                    Messages.errKey(context, "home.limit_reached", java.util.Map.of("limit", String.valueOf(maxHomes)));
                    return;
                }
            }
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            Messages.errKey(context, "home.position_failed", java.util.Map.of());
            return;
        }

        Vector3d pos = transform.getPosition();
        float yaw = 0f;
        float pitch = 0f;
        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        if (headRotation != null) {
            Vector3f rot = headRotation.getRotation();
            if (rot != null) {
                pitch = rot.getX();
                yaw = rot.getY();
            }
        } else {
            Vector3f rot = transform.getRotation();
            if (rot != null) {
                yaw = rot.getY();
                pitch = rot.getX();
            }
        }

        if (pos == null
                || !Double.isFinite(pos.getX())
                || !Double.isFinite(pos.getY())
                || !Double.isFinite(pos.getZ())
                || !Float.isFinite(yaw)
                || !Float.isFinite(pitch)) {
            Messages.errKey(context, "home.position_failed", java.util.Map.of());
            return;
        }

        String worldId = playerRef.getWorldUuid() != null ? playerRef.getWorldUuid().toString() : null;
        homeManager.setHome(playerRef.getUuid(), new HomeModel(
                name,
                worldId,
                world.getName(),
                pos.getX(), pos.getY(), pos.getZ(),
                yaw, pitch
        ));

        Messages.okKey(context, "home.set", java.util.Map.of("home", name));
    }

    private int resolveMaxHomes(@Nonnull com.hypixel.hytale.server.core.command.system.CommandSender sender) {
        if (sender.hasPermission(MAX_PERMISSION_PREFIX + "*")
                || sender.hasPermission(MAX_PERMISSION_PREFIX + "unlimited")) {
            return -1;
        }

        int max = -1;
        for (int i = 1; i <= MAX_PERMISSION_SCAN; i++) {
            if (sender.hasPermission(MAX_PERMISSION_PREFIX + i)) {
                max = i;
            }
        }
        return max;
    }
}




