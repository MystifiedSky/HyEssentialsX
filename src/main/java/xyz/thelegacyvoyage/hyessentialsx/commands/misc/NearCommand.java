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
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
            Messages.noPerm(context, "/near");
            return;
        }
        if (!config.isNearEnabled()) {
            Messages.err(context, "Near is disabled.");
            return;
        }
        if (!cooldowns.canUse(context, playerRef, CooldownKeys.NEAR, "/near", BYPASS_PERMISSION)) {
            return;
        }

        Transform selfTransform = playerRef.getTransform();
        if (selfTransform == null) {
            Messages.err(context, "Could not read your position.");
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
                nearby.add(other.getUsername() + " (" + (int) dist + "m)");
            } else {
                nearby.add(other.getUsername());
            }
        }

        if (nearby.isEmpty()) {
            Messages.send(context, "&7No nearby players.");
            return;
        }

        cooldowns.apply(playerRef, CooldownKeys.NEAR);
        Messages.send(context, "&aNearby: &f" + String.join(", ", nearby));
    }
}




