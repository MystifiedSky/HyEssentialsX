package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import org.joml.Vector3d;
import org.joml.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;

import javax.annotation.Nonnull;

public final class DeathBackListener {

    private final BackManager back;

    public DeathBackListener(@Nonnull BackManager back) {
        this.back = back;
    }

    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        // Some SDKs use registerSystem, some use registerSystems
        // If registerSystem doesn't exist, IntelliJ will suggest the correct one.
        registry.registerSystem(new DeathSys(back));
    }

    private static final class DeathSys extends RefChangeSystem<EntityStore, DeathComponent> {

        private final BackManager back;

        private DeathSys(@Nonnull BackManager back) {
            this.back = back;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }

        @Override
        public void onComponentAdded(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull DeathComponent component,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer
        ) {
            PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null) return;
            if (!PermissionsModule.get().hasPermission(player.getUuid(), "hyessentialsx.back.ondeath", false)) {
                return;
            }

            TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
            if (t == null) return;

            World world = store.getExternalData().getWorld();
            if (world == null) return;

            Vector3d pos = t.getPosition();
            com.hypixel.hytale.math.vector.Rotation3f rot = t.getRotation();

            back.recordDeath(
                    player.getUuid(),
                    world.getName(),
                    pos.x(), pos.y(), pos.z(),
                    rot.y(),   // yaw (matches your other usage)
                    rot.x()    // pitch
            );
        }

        @Override
        public void onComponentSet(
                @Nonnull Ref<EntityStore> ref,
                DeathComponent oldC,
                @Nonnull DeathComponent newC,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer
        ) { }

        @Override
        public void onComponentRemoved(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull DeathComponent component,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer
        ) { }

        @Override
        public com.hypixel.hytale.component.ComponentType<EntityStore, DeathComponent> componentType() {
            return DeathComponent.getComponentType();
        }
    }
}

