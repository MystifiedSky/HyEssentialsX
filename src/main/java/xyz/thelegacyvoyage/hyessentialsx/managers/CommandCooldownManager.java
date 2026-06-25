package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import xyz.thelegacyvoyage.hyessentialsx.models.CommandRuleModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandBypassUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Map;

public final class CommandCooldownManager {

    private final ConfigManager config;
    private final StorageManager storage;
    private final EconomyManager economy;

    public CommandCooldownManager(@Nonnull ConfigManager config,
                                  @Nonnull StorageManager storage,
                                  @Nonnull EconomyManager economy) {
        this.config = config;
        this.storage = storage;
        this.economy = economy;
    }

    public boolean canUse(@Nonnull CommandContext context,
                          @Nonnull PlayerRef playerRef,
                          @Nonnull String key,
                          @Nonnull String commandLabel,
                          @Nonnull String bypassPermission) {
        return canUse(context, playerRef, key, commandLabel, bypassPermission, null);
    }

    public boolean canUse(@Nonnull CommandContext context,
                          @Nonnull PlayerRef playerRef,
                          @Nonnull String key,
                          @Nonnull String commandLabel,
                          @Nonnull String bypassPermission,
                          @Nullable World world) {
        if (!isRuleAllowed(context, playerRef, key, commandLabel, bypassPermission, world)) {
            return false;
        }
        int cooldownSeconds = getEffectiveCooldownSeconds(context.sender(), playerRef, key, bypassPermission);
        if (cooldownSeconds <= 0) {
            return canAfford(context, playerRef, key, commandLabel, bypassPermission);
        }
        if (CommandBypassUtil.hasCooldownBypass(context.sender(), key, bypassPermission)
                || CommandBypassUtil.hasCooldownBypass(playerRef, key, bypassPermission)) {
            return canAfford(context, playerRef, key, commandLabel, bypassPermission);
        }

        PlayerDataModel data = storage.getPlayerData(playerRef.getUuid());
        Long lastUsed = data.getCommandCooldowns().get(key);
        if (lastUsed == null) {
            return true;
        }

        long elapsedSeconds = (System.currentTimeMillis() - lastUsed) / 1000L;
        long remaining = cooldownSeconds - elapsedSeconds;
        if (remaining <= 0) {
            return true;
        }

        Messages.warnKey(context, "cooldown.wait", Map.of(
                "time", TimeUtil.formatDurationSeconds(remaining),
                "command", commandLabel
        ));
        return false;
    }

    public boolean canUse(@Nonnull PlayerRef playerRef,
                          @Nonnull String key,
                          @Nonnull String commandLabel,
                          @Nonnull String bypassPermission,
                          @Nullable World world) {
        if (!isRuleAllowed(null, playerRef, key, commandLabel, bypassPermission, world)) {
            return false;
        }
        return canUse(playerRef, key, commandLabel, bypassPermission);
    }

    public boolean canUse(@Nonnull PlayerRef playerRef,
                          @Nonnull String key,
                          @Nonnull String commandLabel,
                          @Nonnull String bypassPermission) {
        int cooldownSeconds = getEffectiveCooldownSeconds(playerRef, playerRef, key, bypassPermission);
        if (cooldownSeconds <= 0) {
            return canAfford(playerRef, key, commandLabel, bypassPermission);
        }
        if (CommandBypassUtil.hasCooldownBypass(playerRef, key, bypassPermission)) {
            return canAfford(playerRef, key, commandLabel, bypassPermission);
        }

        PlayerDataModel data = storage.getPlayerData(playerRef.getUuid());
        Long lastUsed = data.getCommandCooldowns().get(key);
        if (lastUsed == null) {
            return true;
        }

        long elapsedSeconds = (System.currentTimeMillis() - lastUsed) / 1000L;
        long remaining = cooldownSeconds - elapsedSeconds;
        if (remaining <= 0) {
            return true;
        }

        Messages.sendPrefixedKey(playerRef, "cooldown.wait", Map.of(
                "time", TimeUtil.formatDurationSeconds(remaining),
                "command", commandLabel
        ));
        return false;
    }

    public boolean apply(@Nonnull PlayerRef playerRef, @Nonnull String key) {
        if (!charge(playerRef, key, "/" + key, "hyessentialsx." + key + ".bypass")) {
            return false;
        }
        int cooldownSeconds = getEffectiveCooldownSeconds(playerRef, playerRef, key, "hyessentialsx." + key + ".bypass");
        if (cooldownSeconds <= 0) {
            return true;
        }
        PlayerDataModel data = storage.getPlayerData(playerRef.getUuid());
        data.getCommandCooldowns().put(key, System.currentTimeMillis());
        storage.savePlayerDataAsync(playerRef.getUuid(), data);
        return true;
    }

    public boolean hasWarmupBypass(@Nonnull CommandContext context,
                                   @Nonnull PlayerRef playerRef,
                                   @Nonnull String key,
                                   @Nonnull String commandBypassPermission) {
        return CommandBypassUtil.hasWarmupBypass(context.sender(), key, commandBypassPermission)
                || CommandBypassUtil.hasWarmupBypass(playerRef, key, commandBypassPermission);
    }

    public boolean hasWarmupBypass(@Nonnull PlayerRef playerRef,
                                   @Nonnull String key,
                                   @Nonnull String commandBypassPermission) {
        return CommandBypassUtil.hasWarmupBypass(playerRef, key, commandBypassPermission);
    }

    public int getEffectiveWarmupSeconds(@Nullable Object sender,
                                         @Nonnull PlayerRef playerRef,
                                         @Nonnull String key,
                                         @Nonnull String commandBypassPermission) {
        if (CommandBypassUtil.hasWarmupBypass(sender, key, commandBypassPermission)
                || CommandBypassUtil.hasWarmupBypass(playerRef, key, commandBypassPermission)) {
            return 0;
        }
        CommandRuleModel rule = config.getCommandRule(key);
        int seconds = rule.getWarmupSeconds();
        for (CommandRuleModel.Reduction reduction : rule.getReductions()) {
            if (!matchesReduction(sender, playerRef, reduction)) {
                continue;
            }
            seconds = applyPercentReduction(seconds, reduction.getWarmupReductionPercent());
            if (reduction.getWarmupSeconds() >= 0) {
                seconds = Math.min(seconds, reduction.getWarmupSeconds());
            }
        }
        return Math.max(0, seconds);
    }

    public boolean shouldCancelWarmupOnMove(@Nonnull String key) {
        return config.getCommandRule(key).isCancelWarmupOnMove();
    }

    public long getEffectivePrice(@Nullable Object sender,
                                  @Nonnull PlayerRef playerRef,
                                  @Nonnull String key,
                                  @Nonnull String commandBypassPermission) {
        if (CommandBypassUtil.hasPriceBypass(sender, key, commandBypassPermission)
                || CommandBypassUtil.hasPriceBypass(playerRef, key, commandBypassPermission)) {
            return 0L;
        }
        CommandRuleModel rule = config.getCommandRule(key);
        long price = rule.getPrice();
        for (CommandRuleModel.Reduction reduction : rule.getReductions()) {
            if (!matchesReduction(sender, playerRef, reduction)) {
                continue;
            }
            price = applyPercentReduction(price, reduction.getPriceReductionPercent());
            if (reduction.getPrice() >= 0L) {
                price = Math.min(price, reduction.getPrice());
            }
        }
        return Math.max(0L, price);
    }

    public boolean charge(@Nonnull CommandContext context,
                          @Nonnull PlayerRef playerRef,
                          @Nonnull String key,
                          @Nonnull String commandLabel,
                          @Nonnull String bypassPermission) {
        long price = getEffectivePrice(context.sender(), playerRef, key, bypassPermission);
        return chargeAmount(context, playerRef, commandLabel, price);
    }

    public boolean charge(@Nonnull PlayerRef playerRef,
                          @Nonnull String key,
                          @Nonnull String commandLabel,
                          @Nonnull String bypassPermission) {
        long price = getEffectivePrice(playerRef, playerRef, key, bypassPermission);
        return chargeAmount(playerRef, commandLabel, price);
    }

    private boolean isRuleAllowed(@Nullable CommandContext context,
                                  @Nonnull PlayerRef playerRef,
                                  @Nonnull String key,
                                  @Nonnull String commandLabel,
                                  @Nonnull String bypassPermission,
                                  @Nullable World world) {
        CommandRuleModel rule = config.getCommandRule(key);
        if (!rule.isEnabled()
                && !CommandPermissionUtil.hasPermission(context != null ? context.sender() : playerRef, "hyessentialsx.commandrules.disabled.bypass")
                && !CommandPermissionUtil.hasPermission(playerRef, "hyessentialsx.commandrules.disabled.bypass." + normalizeKey(key))) {
            sendRuleBlocked(context, playerRef, "command.disabled", Map.of("command", commandLabel));
            return false;
        }
        if (world != null && isWorldBlacklisted(rule, world.getName())
                && !CommandPermissionUtil.hasPermission(context != null ? context.sender() : playerRef, "hyessentialsx.commandrules.world.bypass")
                && !CommandPermissionUtil.hasPermission(playerRef, "hyessentialsx.commandrules.world.bypass." + normalizeKey(key))) {
            sendRuleBlocked(context, playerRef, "command.world_blacklisted", Map.of(
                    "command", commandLabel,
                    "world", world.getName()
            ));
            return false;
        }
        return true;
    }

    private int getEffectiveCooldownSeconds(@Nullable Object sender,
                                            @Nonnull PlayerRef playerRef,
                                            @Nonnull String key,
                                            @Nonnull String commandBypassPermission) {
        CommandRuleModel rule = config.getCommandRule(key);
        int seconds = rule.getCooldownSeconds();
        for (CommandRuleModel.Reduction reduction : rule.getReductions()) {
            if (!matchesReduction(sender, playerRef, reduction)) {
                continue;
            }
            seconds = applyPercentReduction(seconds, reduction.getCooldownReductionPercent());
            if (reduction.getCooldownSeconds() >= 0) {
                seconds = Math.min(seconds, reduction.getCooldownSeconds());
            }
        }
        return Math.max(0, seconds);
    }

    private boolean canAfford(@Nonnull CommandContext context,
                              @Nonnull PlayerRef playerRef,
                              @Nonnull String key,
                              @Nonnull String commandLabel,
                              @Nonnull String bypassPermission) {
        long price = getEffectivePrice(context.sender(), playerRef, key, bypassPermission);
        if (price <= 0L) {
            return true;
        }
        if (!economy.isEnabled()) {
            Messages.errKey(context, "economy.disabled", Map.of());
            return false;
        }
        long balance = economy.getBalance(playerRef.getUuid());
        if (balance >= price) {
            return true;
        }
        Messages.errKey(context, "command.price.insufficient", Map.of(
                "command", commandLabel,
                "price", economy.formatAmount(price),
                "balance", economy.formatAmount(balance)
        ));
        return false;
    }

    private boolean canAfford(@Nonnull PlayerRef playerRef,
                              @Nonnull String key,
                              @Nonnull String commandLabel,
                              @Nonnull String bypassPermission) {
        long price = getEffectivePrice(playerRef, playerRef, key, bypassPermission);
        if (price <= 0L) {
            return true;
        }
        if (!economy.isEnabled()) {
            Messages.sendPrefixedKey(playerRef, "economy.disabled", Map.of());
            return false;
        }
        long balance = economy.getBalance(playerRef.getUuid());
        if (balance >= price) {
            return true;
        }
        Messages.sendPrefixedKey(playerRef, "command.price.insufficient", Map.of(
                "command", commandLabel,
                "price", economy.formatAmount(price),
                "balance", economy.formatAmount(balance)
        ));
        return false;
    }

    private boolean chargeAmount(@Nonnull CommandContext context,
                                 @Nonnull PlayerRef playerRef,
                                 @Nonnull String commandLabel,
                                 long price) {
        if (price <= 0L) {
            return true;
        }
        if (!economy.isEnabled()) {
            Messages.errKey(context, "economy.disabled", Map.of());
            return false;
        }
        if (!economy.withdraw(playerRef.getUuid(), price)) {
            Messages.errKey(context, "command.price.insufficient", Map.of(
                    "command", commandLabel,
                    "price", economy.formatAmount(price),
                    "balance", economy.formatAmount(economy.getBalance(playerRef.getUuid()))
            ));
            return false;
        }
        Messages.okKey(context, "command.price.charged", Map.of(
                "command", commandLabel,
                "price", economy.formatAmount(price)
        ));
        return true;
    }

    private boolean chargeAmount(@Nonnull PlayerRef playerRef,
                                 @Nonnull String commandLabel,
                                 long price) {
        if (price <= 0L) {
            return true;
        }
        if (!economy.isEnabled()) {
            Messages.sendPrefixedKey(playerRef, "economy.disabled", Map.of());
            return false;
        }
        if (!economy.withdraw(playerRef.getUuid(), price)) {
            Messages.sendPrefixedKey(playerRef, "command.price.insufficient", Map.of(
                    "command", commandLabel,
                    "price", economy.formatAmount(price),
                    "balance", economy.formatAmount(economy.getBalance(playerRef.getUuid()))
            ));
            return false;
        }
        Messages.sendPrefixedKey(playerRef, "command.price.charged", Map.of(
                "command", commandLabel,
                "price", economy.formatAmount(price)
        ));
        return true;
    }

    private boolean matchesReduction(@Nullable Object sender,
                                     @Nonnull PlayerRef playerRef,
                                     @Nonnull CommandRuleModel.Reduction reduction) {
        String permission = reduction.getPermission();
        if (permission.isBlank()) {
            return false;
        }
        return CommandPermissionUtil.hasPermission(sender, permission)
                || CommandPermissionUtil.hasPermission(playerRef, permission);
    }

    private static int applyPercentReduction(int value, int percent) {
        if (value <= 0 || percent <= 0) {
            return Math.max(0, value);
        }
        return (int) Math.max(0L, Math.round(value * ((100.0D - Math.min(100, percent)) / 100.0D)));
    }

    private static long applyPercentReduction(long value, int percent) {
        if (value <= 0L || percent <= 0) {
            return Math.max(0L, value);
        }
        return Math.max(0L, Math.round(value * ((100.0D - Math.min(100, percent)) / 100.0D)));
    }

    private static boolean isWorldBlacklisted(@Nonnull CommandRuleModel rule, @Nonnull String worldName) {
        String normalized = worldName.trim().toLowerCase(Locale.ROOT);
        for (String world : rule.getBlacklistedWorlds()) {
            if (world != null && normalized.equals(world.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void sendRuleBlocked(@Nullable CommandContext context,
                                 @Nonnull PlayerRef playerRef,
                                 @Nonnull String key,
                                 @Nonnull Map<String, String> placeholders) {
        if (context != null) {
            Messages.errKey(context, key, placeholders);
            return;
        }
        Messages.sendPrefixedKey(playerRef, key, placeholders);
    }

    @Nonnull
    private static String normalizeKey(@Nonnull String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }
}

