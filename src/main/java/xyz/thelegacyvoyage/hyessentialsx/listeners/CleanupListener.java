package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.FlyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.GodManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.InfiniteStaminaManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;

import javax.annotation.Nonnull;
import java.util.UUID;

public final class CleanupListener {

    private final TPManager tp;
    private final BackManager back;
    private final FlyManager fly;
    private final GodManager god;
    private final InfiniteStaminaManager stamina;

    public CleanupListener(@Nonnull TPManager tp,
                           @Nonnull BackManager back,
                           @Nonnull FlyManager fly,
                           @Nonnull GodManager god,
                           @Nonnull InfiniteStaminaManager stamina) {
        this.tp = tp;
        this.back = back;
        this.fly = fly;
        this.god = god;
        this.stamina = stamina;
    }

    public void register(@Nonnull EventRegistry events) {
        events.registerGlobal(PlayerDisconnectEvent.class, e -> {
            UUID uuid = e.getPlayerRef().getUuid();

            tp.onPlayerQuit(uuid);
            back.onPlayerQuit(uuid);
            fly.clear(uuid);
            god.clear(uuid);
            stamina.clear(uuid);
        });

        Log.info("Cleanup listener registered.");
    }
}
