package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.AfkManager;

import javax.annotation.Nonnull;

public final class AfkListener {

    private final AfkManager afk;

    public AfkListener(@Nonnull AfkManager afk) {
        this.afk = afk;
    }

    public void register(@Nonnull EventRegistry events) {
        events.registerGlobal(PlayerConnectEvent.class, event -> {
            PlayerRef player = event.getPlayerRef();
            if (player != null) {
                afk.handleConnect(player);
            }
        });

        events.registerGlobal(PlayerDisconnectEvent.class, event -> {
            PlayerRef player = event.getPlayerRef();
            if (player != null) {
                afk.handleDisconnect(player);
            }
        });

        events.registerGlobal(PlayerChatEvent.class, event -> {
            PlayerRef player = event.getSender();
            if (player != null) {
                afk.handleActivity(player);
            }
        });

        events.registerGlobal(PlayerMouseMotionEvent.class, event -> {
            PlayerRef player = resolveRef(event);
            if (player != null) {
                afk.handleActivity(player);
            }
        });

        events.registerGlobal(PlayerMouseButtonEvent.class, event -> {
            PlayerRef player = resolveRef(event);
            if (player != null) {
                afk.handleActivity(player);
            }
        });

    }

    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new AfkCraftSystem(afk));
    }

    private PlayerRef resolveRef(PlayerEvent<?> event) {
        var ref = event.getPlayerRef();
        if (ref == null) return null;
        return ref.getStore().getComponent(ref, PlayerRef.getComponentType());
    }

    private static final class AfkCraftSystem extends EntityEventSystem<EntityStore, CraftRecipeEvent.Post> {

        private final AfkManager afk;

        private AfkCraftSystem(@Nonnull AfkManager afk) {
            super(CraftRecipeEvent.Post.class);
            this.afk = afk;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(int index,
                           @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer,
                           @Nonnull CraftRecipeEvent.Post event) {
            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            if (playerRef != null) {
                afk.handleActivity(playerRef);
            }
        }
    }
}

