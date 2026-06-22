package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerCraftEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
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

        events.registerGlobal(PlayerInteractEvent.class, event -> {
            PlayerRef player = resolveRef(event);
            if (player != null) {
                afk.handleActivity(player);
            }
        });

        events.registerGlobal(PlayerCraftEvent.class, event -> {
            PlayerRef player = resolveRef(event);
            if (player != null) {
                afk.handleActivity(player);
            }
        });
    }

    private PlayerRef resolveRef(PlayerEvent<?> event) {
        Player player = event.getPlayer();
        if (player == null) return null;
        return player.getPlayerRef();
    }
}

