package xyz.thelegacyvoyage.hyessentialsx.placeholders;

import at.helpch.placeholderapi.expansion.PlaceholderExpansion;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import xyz.thelegacyvoyage.hyessentialsx.HyEssentialsXPlugin;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.HomeManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.MailManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.NicknameManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.PlaytimeManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.LuckPermsUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class HyEssentialsXPlaceholderExpansion extends PlaceholderExpansion {

    private static final String IDENTIFIER = "hyessentialsx";

    private final HyEssentialsXPlugin plugin;
    private final EconomyManager economy;
    private final PlaytimeManager playtime;
    private final StorageManager storage;
    private final HomeManager homes;
    private final MailManager mail;
    private final NicknameManager nicknames;

    public HyEssentialsXPlaceholderExpansion(@Nonnull HyEssentialsXPlugin plugin,
                                             @Nonnull EconomyManager economy,
                                             @Nonnull PlaytimeManager playtime,
                                             @Nonnull StorageManager storage,
                                             @Nonnull HomeManager homes,
                                             @Nonnull MailManager mail,
                                             @Nonnull NicknameManager nicknames) {
        this.plugin = plugin;
        this.economy = economy;
        this.playtime = playtime;
        this.storage = storage;
        this.homes = homes;
        this.mail = mail;
        this.nicknames = nicknames;
    }

    @Override
    public @Nonnull String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public @Nonnull String getAuthor() {
        try {
            var manifest = plugin.getManifest();
            if (manifest != null && manifest.getAuthors() != null && !manifest.getAuthors().isEmpty()) {
                var author = manifest.getAuthors().get(0);
                if (author != null && author.getName() != null && !author.getName().isBlank()) {
                    return author.getName();
                }
            }
        } catch (Exception ignored) {
        }
        return "HyEssentialsX";
    }

    @Override
    public @Nonnull String getVersion() {
        try {
            var manifest = plugin.getManifest();
            if (manifest != null && manifest.getVersion() != null) {
                return manifest.getVersion().toString();
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nonnull List<String> getPlaceholders() {
        return List.of(
                "balance",
                "balance_raw",
                "currency_symbol",
                "playtime",
                "playtime_seconds",
                "rank",
                "luckperms_prefix",
                "luckperms_suffix",
                "luckperms_primary_group",
                "luckperms_meta_<key>",
                "nickname",
                "nickname_plain",
                "displayname",
                "realname",
                "homes",
                "mail_unread",
                "mail_inbox",
                "mail_sent",
                "fly",
                "frozen",
                "language"
        );
    }

    @Override
    public @Nullable String onPlaceholderRequest(@Nullable PlayerRef player, @Nullable String params) {
        if (player == null || params == null) {
            return "";
        }
        String key = params.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return "";
        }
        UUID uuid = player.getUuid();
        PlayerDataModel data = storage.getPlayerData(uuid);
        if (key.startsWith("luckperms_meta_")) {
            String value = LuckPermsUtil.getMetaValue(uuid, key.substring("luckperms_meta_".length()));
            return value == null ? "" : value;
        }

        return switch (key) {
            case "balance" -> formatBalance(resolveBalance(uuid, data));
            case "balance_raw" -> String.valueOf(resolveBalance(uuid, data));
            case "currency_symbol" -> economy.getCurrencySymbol();
            case "playtime" -> TimeUtil.formatDurationSeconds(playtime.getPlaytimeSeconds(uuid));
            case "playtime_seconds" -> String.valueOf(playtime.getPlaytimeSeconds(uuid));
            case "rank" -> resolveRank(uuid);
            case "luckperms_prefix" -> LuckPermsUtil.getPrefix(uuid);
            case "luckperms_suffix" -> LuckPermsUtil.getSuffix(uuid);
            case "luckperms_primary_group" -> resolveRank(uuid);
            case "nickname" -> nicknames.hasNickname(player) ? nicknames.displayName(player) : "";
            case "nickname_plain" -> nicknames.hasNickname(player) ? NicknameManager.plain(nicknames.displayName(player)) : "";
            case "displayname" -> nicknames.displayName(player);
            case "realname" -> player.getUsername();
            case "homes" -> String.valueOf(homes.getHomeCount(uuid));
            case "mail_unread" -> String.valueOf(mail.getUnreadCount(uuid));
            case "mail_inbox" -> String.valueOf(data.getMailInbox().size());
            case "mail_sent" -> String.valueOf(data.getMailSent().size());
            case "fly" -> String.valueOf(data.isFlyEnabled());
            case "frozen" -> String.valueOf(data.isFrozen());
            case "language" -> data.getLanguage() == null ? "" : data.getLanguage();
            default -> null;
        };
    }

    private long resolveBalance(@Nonnull UUID uuid, @Nonnull PlayerDataModel data) {
        if (economy.isEnabled()) {
            return economy.getBalance(uuid);
        }
        return data.getBalance();
    }

    @Nonnull
    private String formatBalance(long amount) {
        if (!economy.isEnabled()) {
            return String.valueOf(amount);
        }
        return economy.formatAmount(amount);
    }

    @Nonnull
    private String resolveRank(@Nonnull UUID uuid) {
        String primary = LuckPermsUtil.getPrimaryGroup(uuid);
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        Set<String> groups = LuckPermsUtil.getGroupsFallback(uuid);
        if (!groups.isEmpty()) {
            return groups.iterator().next();
        }
        return "default";
    }
}
