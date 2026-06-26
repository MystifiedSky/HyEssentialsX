package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.managers.AdminChatManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.MuteManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.NicknameManager;
import xyz.thelegacyvoyage.hyessentialsx.models.MuteModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.HyFactionsUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.LuckPermsUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.PlaceholderApiUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatModerationListener {

    private static final String ADMINCHAT_PERMISSION = "hyessentialsx.adminchat";
    private static final String MESSAGE_TOKEN = "__HX_MESSAGE__";
    private static final Pattern LUCKPERMS_META_PERCENT = Pattern.compile("%luckperms_meta_([A-Za-z0-9_.-]+)%");
    private static final Pattern LUCKPERMS_META_BRACE = Pattern.compile("\\{luckperms_meta:([A-Za-z0-9_.-]+)}");

    private final MuteManager muteManager;
    private final AdminChatManager adminChatManager;
    private final ConfigManager config;
    private final NicknameManager nicknames;

    public ChatModerationListener(@Nonnull MuteManager muteManager,
                                  @Nonnull AdminChatManager adminChatManager,
                                  @Nonnull ConfigManager config,
                                  @Nonnull NicknameManager nicknames) {
        this.muteManager = muteManager;
        this.adminChatManager = adminChatManager;
        this.config = config;
        this.nicknames = nicknames;
    }

    public void register(@Nonnull EventRegistry events) {
        events.registerGlobal(EventPriority.FIRST, PlayerChatEvent.class, event -> {
            PlayerRef sender = event.getSender();
            if (sender == null) return;

            MuteModel mute = muteManager.getMute(sender.getUuid());
            if (mute != null) {
                event.setCancelled(true);
                String remaining = TimeUtil.formatRemaining(mute.getExpiresAt());
                if (remaining != null && !remaining.isBlank()) {
                    Messages.send(sender, Messages.tr(sender, "mute.blocked_remaining", java.util.Map.of(
                            "time", remaining
                    )));
                } else {
                    Messages.send(sender, "mute.blocked");
                }
                if (mute.getReason() != null && !mute.getReason().isBlank()) {
                    Messages.send(sender, Messages.tr(sender, "mute.reason", java.util.Map.of(
                            "reason", mute.getReason()
                    )));
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
                    target.sendMessage(Messages.m(formatted));
                }
                return;
            }

            String raw = event.getContent();
            if (raw == null) raw = "";

            if (config.isChatFormatEnabled()) {
                setChatFormatter(event, sender);
                return;
            }

            if (hasColorCodes(raw)) {
                Message formatted = Messages.m(raw);
                if (trySetMessage(event, formatted)) {
                    return;
                }
                return;
            }
        });

        events.registerGlobal(EventPriority.LAST, PlayerChatEvent.class, event -> {
            if (event.isCancelled()) return;
            if (!config.isChatFormatEnabled()) return;
            PlayerRef sender = event.getSender();
            if (sender == null) return;
            setChatFormatter(event, sender);
        });
    }

    private void setChatFormatter(@Nonnull PlayerChatEvent event, @Nonnull PlayerRef sender) {
        String formattedBase = buildFormattedBase(sender);
        event.setFormatter((playerRef, message) -> {
            String content = message != null ? message : "";
            String baseWithToken = formattedBase.replace("{message}", MESSAGE_TOKEN);
            String resolvedBase = PlaceholderApiUtil.applyString(sender, baseWithToken);
            String resolved = resolvedBase.replace(MESSAGE_TOKEN, content);
            return Messages.m(resolved);
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
        UUID uuid = sender.getUuid();
        String luckPermsPrefix = LuckPermsUtil.getPrefix(uuid);
        String luckPermsSuffix = LuckPermsUtil.getSuffix(uuid);
        String localPrefix = config.getChatPrefixForGroup(groupName);
        String localSuffix = config.getChatSuffixForGroup(groupName);
        String prefix = luckPermsPrefix.isBlank() ? localPrefix : luckPermsPrefix;
        String suffix = luckPermsSuffix.isBlank() ? localSuffix : luckPermsSuffix;
        String resolved = format
                .replace("{player}", nicknames.displayName(sender))
                .replace("{displayname}", nicknames.displayName(sender))
                .replace("{realname}", sender.getUsername())
                .replace("{username}", sender.getUsername())
                .replace("%luckperms_prefix%", luckPermsPrefix)
                .replace("{luckperms_prefix}", luckPermsPrefix)
                .replace("{local_prefix}", localPrefix)
                .replace("{prefix}", prefix)
                .replace("%luckperms_suffix%", luckPermsSuffix)
                .replace("{luckperms_suffix}", luckPermsSuffix)
                .replace("{local_suffix}", localSuffix)
                .replace("{suffix}", suffix)
                .replace("%luckperms_primary_group%", groupName)
                .replace("{luckperms_primary_group}", groupName)
                .replace("{group}", groupName)
                .replace("{faction}", faction);
        return replaceLuckPermsMeta(uuid, resolved);
    }

    @Nonnull
    private String replaceLuckPermsMeta(@Nonnull UUID uuid, @Nonnull String text) {
        String resolved = replaceLuckPermsMeta(uuid, text, LUCKPERMS_META_PERCENT);
        return replaceLuckPermsMeta(uuid, resolved, LUCKPERMS_META_BRACE);
    }

    @Nonnull
    private String replaceLuckPermsMeta(@Nonnull UUID uuid, @Nonnull String text, @Nonnull Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String value = LuckPermsUtil.getMetaValue(uuid, matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(out);
        return out.toString();
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
        return CommandPermissionUtil.hasPermission(ref, ADMINCHAT_PERMISSION);
    }
}

