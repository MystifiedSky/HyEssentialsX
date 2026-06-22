package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.managers.AdminChatManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.MuteManager;
import xyz.thelegacyvoyage.hyessentialsx.models.MuteModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.HyFactionsUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.LuckPermsUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public final class ChatModerationListener {

    private static final String ADMINCHAT_PERMISSION = "hyessentialsx.adminchat";

    private final MuteManager muteManager;
    private final AdminChatManager adminChatManager;
    private final ConfigManager config;

    public ChatModerationListener(@Nonnull MuteManager muteManager,
                                  @Nonnull AdminChatManager adminChatManager,
                                  @Nonnull ConfigManager config) {
        this.muteManager = muteManager;
        this.adminChatManager = adminChatManager;
        this.config = config;
    }

    public void register(@Nonnull EventRegistry events) {
        events.registerGlobal(PlayerChatEvent.class, event -> {
            PlayerRef sender = event.getSender();
            if (sender == null) return;

            MuteModel mute = muteManager.getMute(sender.getUuid());
            if (mute != null) {
                event.setCancelled(true);
                String remaining = TimeUtil.formatRemaining(mute.getExpiresAt());
                Messages.send(sender, "&cYou are muted." +
                        (remaining != null ? " &7(" + remaining + ")" : ""));
                if (mute.getReason() != null && !mute.getReason().isBlank()) {
                    Messages.send(sender, "&7Reason: &f" + mute.getReason());
                }
                return;
            }

            if (adminChatManager.isEnabled(sender.getUuid())) {
                if (!config.isAdminChatEnabled()) {
                    return;
                }
                event.setCancelled(true);
                String msg = event.getContent();
                if (msg == null) msg = "";

                List<PlayerRef> targets = new ArrayList<>();
                for (PlayerRef ref : Universe.get().getPlayers()) {
                    if (PermissionsModule.get().hasPermission(ref.getUuid(), ADMINCHAT_PERMISSION)) {
                        targets.add(ref);
                    }
                }

                String formatted = "&c[Admin] &f" + sender.getUsername() + "&7: &f" + msg;
                for (PlayerRef target : targets) {
                    Messages.send(target, formatted);
                }
                return;
            }

            String raw = event.getContent();
            if (raw == null) raw = "";

            if (config.isChatFormatEnabled()) {
                String groupName = LuckPermsUtil.getPrimaryGroup(sender.getUuid());
                if (groupName == null || groupName.isBlank()) {
                    groupName = config.getHighestPriorityGroup(
                            LuckPermsUtil.getGroupsFallback(sender.getUuid())
                    );
                }
                String format = config.getChatFormatForGroup(groupName);
                String faction = HyFactionsUtil.getFactionName(sender.getUuid());
                if (faction == null) faction = "";
                String formattedText = format
                        .replace("{player}", sender.getUsername())
                        .replace("{message}", raw)
                        .replace("{group}", groupName)
                        .replace("{faction}", faction);
                Message formatted = Messages.m(formattedText);
                if (trySetMessage(event, formatted)) {
                    return;
                }
                event.setCancelled(true);
                Universe.get().sendMessage(formatted);
                return;
            }

            if (hasColorCodes(raw)) {
                Message formatted = Messages.m(raw);
                if (trySetMessage(event, formatted)) {
                    return;
                }
                event.setCancelled(true);
                String name = sender.getUsername();
                for (PlayerRef target : Universe.get().getPlayers()) {
                    target.sendMessage(Message.join(
                            Message.raw(name + ": ").color("#FFFFFF"),
                            formatted
                    ));
                }
            }
        });
    }

    private boolean hasColorCodes(@Nonnull String text) {
        return text.contains("&") || text.contains("{#") || text.contains("<#");
    }

    private boolean trySetMessage(@Nonnull PlayerChatEvent event, @Nonnull Message message) {
        String[] methods = {"setMessage", "setFormattedMessage", "setChatMessage"};
        for (String name : methods) {
            try {
                java.lang.reflect.Method method = event.getClass().getMethod(name, Message.class);
                method.invoke(event, message);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }
}
