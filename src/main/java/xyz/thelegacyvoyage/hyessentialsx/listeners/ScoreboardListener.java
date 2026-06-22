package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.ScoreboardManager;

import javax.annotation.Nonnull;

public final class ScoreboardListener {

    private final ScoreboardManager scoreboardManager;

    public ScoreboardListener(@Nonnull ScoreboardManager scoreboardManager) {
        this.scoreboardManager = scoreboardManager;
    }

    public void register(@Nonnull EventRegistry events) {
        events.registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
        events.register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
    }

    private void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Player playerEntity = event.getPlayer();
        if (playerEntity == null) {
            return;
        }
        World world = playerEntity.getWorld();
        if (world == null) {
            return;
        }
        Ref<EntityStore> ref = event.getPlayerRef();
        if (ref == null) {
            return;
        }
        world.execute(() -> {
            Store<EntityStore> store = ref.getStore();
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            scoreboardManager.scheduleInitial(playerRef);
        });
    }

    private void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef == null) {
            return;
        }
        scoreboardManager.onPlayerDisconnect(playerRef);
    }
}
