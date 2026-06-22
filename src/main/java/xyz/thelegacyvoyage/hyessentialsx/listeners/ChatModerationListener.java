package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.event.EventPriority;
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
import xyz.thelegacyvoyage.hyessentialsx.util.PlaceholderApiUtil;
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
        events.registerGlobal(EventPriority.FIRST, PlayerChatEvent.class, event -> {
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
                clearChatContent(event);

                List<PlayerRef> targets = new ArrayList<>();
                for (PlayerRef ref : Universe.get().getPlayers()) {
                    if (hasAdminChatPermission(ref)) {
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
                if (!config.isOverrideLuckPermsChatFormat()) {
                    return;
                }
                String formattedBase = buildFormattedBase(sender);
                event.setFormatter((playerRef, message) -> {
                    String content = message != null ? message : "";
                    return PlaceholderApiUtil.apply(sender, formattedBase.replace("{message}", content), config);
                });
                return;
            }

            if (hasColorCodes(raw)) {
                Message formatted = PlaceholderApiUtil.apply(sender, raw, config);
                if (trySetMessage(event, formatted)) {
                    return;
                }
                return;
            }
        });

        events.registerGlobal(EventPriority.LAST, PlayerChatEvent.class, event -> {
            if (event.isCancelled()) return;
            if (!config.isChatFormatEnabled() || !config.isOverrideLuckPermsChatFormat()) return;
            PlayerRef sender = event.getSender();
            if (sender == null) return;
            String formattedBase = buildFormattedBase(sender);
            event.setFormatter((playerRef, message) -> {
                String content = message != null ? message : "";
                return PlaceholderApiUtil.apply(sender, formattedBase.replace("{message}", content), config);
            });
        });
    }

    private void clearChatContent(@Nonnull PlayerChatEvent event) {
        try {
            event.setContent("");
        } catch (Exception ignored) {
        }
    }

    private boolean hasColorCodes(@Nonnull String text) {
        return text.contains("&") || text.contains("{#") || text.contains("<#");
    }

    @Nonnull
    private String buildFormattedBase(@Nonnull PlayerRef sender) {
        String groupName = LuckPermsUtil.getPrimaryGroup(sender.getUuid());
        if (groupName == null || groupName.isBlank()) {
            groupName = config.getHighestPriorityGroup(
                    LuckPermsUtil.getGroupsFallback(sender.getUuid())
            );
        }
        String format = config.getChatFormatForGroup(groupName);
        String faction = HyFactionsUtil.getFactionName(sender.getUuid());
        if (faction == null) faction = "";
        if (faction.isBlank()) {
            format = format
                    .replace("[{faction}]", "")
                    .replace("({faction})", "")
                    .replace("<{faction}>", "")
                    .replace("{faction} ", "")
                    .replace(" {faction}", "")
                    .replace("{faction}", "");
        }
        return format
                .replace("{player}", sender.getUsername())
                .replace("{group}", groupName)
                .replace("{faction}", faction);
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
        try {
            event.setFormatter((playerRef, content) -> message);
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean hasAdminChatPermission(@Nonnull PlayerRef ref) {
        return PermissionsModule.get().hasPermission(ref.getUuid(), ADMINCHAT_PERMISSION, false);
    }
}
