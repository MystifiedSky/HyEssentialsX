package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandSpyManager;

import javax.annotation.Nonnull;

public final class CommandSpyListener {

    private final CommandSpyManager commandSpy;

    public CommandSpyListener(@Nonnull CommandSpyManager commandSpy) {
        this.commandSpy = commandSpy;
    }

    public void register(@Nonnull EventRegistry events) {
        events.registerGlobal(EventPriority.FIRST, PlayerChatEvent.class, event -> {
            PlayerRef sender = event.getSender();
            if (sender == null) {
                return;
            }
            String content = event.getContent();
            if (content == null || !content.startsWith("/")) {
                return;
            }
            commandSpy.notifyRawCommand(sender, content);
        });
    }
}
