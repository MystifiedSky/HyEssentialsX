package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public final class NearCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.near";
    private static final String BYPASS_PERMISSION = "hyessentialsx.near.bypass";

    private final ConfigManager config;
    private final CommandCooldownManager cooldowns;

    public NearCommand(@Nonnull ConfigManager config, @Nonnull CommandCooldownManager cooldowns) {
        super("near", "Shows nearby players");
        this.config = config;
        this.cooldowns = cooldowns;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"nearby"});
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
            Messages.noPerm(context, "/near");
            return;
        }
        if (!config.isNearEnabled()) {
            Messages.errKey(context, "near.disabled", java.util.Map.of());
            return;
        }
        if (!cooldowns.canUse(context, playerRef, CooldownKeys.NEAR, "/near", BYPASS_PERMISSION)) {
            return;
        }

        Transform selfTransform = playerRef.getTransform();
        if (selfTransform == null) {
            Messages.errKey(context, "error.position_unavailable", java.util.Map.of());
            return;
        }

        Vector3d selfPos = selfTransform.getPosition();
        double radius = config.getNearRadius();
        double radiusSq = radius * radius;

        List<String> nearby = new ArrayList<>();
        for (PlayerRef other : Universe.get().getPlayers()) {
            if (other.getUuid().equals(playerRef.getUuid())) continue;
            Transform t = other.getTransform();
            if (t == null) continue;
            Vector3d pos = t.getPosition();
            if (pos == null) continue;
            if (pos.distanceSquaredTo(selfPos) > radiusSq) continue;
            if (config.isNearShowDistance()) {
                double dist = Math.sqrt(pos.distanceSquaredTo(selfPos));
                nearby.add(Messages.tr(playerRef, "near.entry.distance", java.util.Map.of(
                        "player", other.getUsername(),
                        "distance", String.valueOf((int) dist)
                )));
            } else {
                nearby.add(other.getUsername());
            }
        }

        if (nearby.isEmpty()) {
            Messages.send(context, "near.none");
            return;
        }

        cooldowns.apply(playerRef, CooldownKeys.NEAR);
        Messages.send(context, Messages.tr(playerRef, "near.list", java.util.Map.of(
                "players", String.join(", ", nearby)
        )));
    }
}




