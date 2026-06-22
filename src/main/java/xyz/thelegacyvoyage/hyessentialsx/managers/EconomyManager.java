package xyz.thelegacyvoyage.hyessentialsx.managers;

import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

public final class EconomyManager {

    private final StorageManager storage;
    private final ConfigManager config;

    public EconomyManager(@Nonnull StorageManager storage, @Nonnull ConfigManager config) {
        this.storage = storage;
        this.config = config;
    }

    public boolean isEnabled() {
        return config.isEconomyEnabled();
    }

    @Nonnull
    public String getCurrencySymbol() {
        String symbol = config.getEconomyCurrencySymbol();
        return symbol == null ? "" : symbol;
    }

    @Nonnull
    public String getCurrencyName() {
        String name = config.getEconomyHudLabel();
        if (name == null || name.isBlank()) {
            return "HyCoins";
        }
        return name;
    }

    public long getStartingBalance() {
        return Math.max(0L, config.getEconomyStartingBalance());
    }

    public int getDecimalPlaces() {
        return config.getEconomyDecimalPlaces();
    }

    @Nonnull
    public String formatAmount(long amount) {
        String symbol = getCurrencySymbol();
        return symbol + formatAmountRaw(amount);
    }

    @Nonnull
    public String formatAmountCompact(long amount) {
        String symbol = getCurrencySymbol();
        return symbol + formatCompact(amount);
    }

    @Nonnull
    public String formatAmountRaw(long amount) {
        int scale = getDecimalPlaces();
        long clamped = Math.max(0L, amount);
        if (scale <= 0) {
            return String.valueOf(clamped);
        }
        BigDecimal value = BigDecimal.valueOf(clamped, scale).setScale(scale, RoundingMode.DOWN);
        return value.toPlainString();
    }

    @Nonnull
    public String formatAmountCompactRaw(long amount) {
        return formatCompact(amount);
    }

    public long getBalance(@Nonnull UUID uuid) {
        PlayerDataModel data = storage.getPlayerData(uuid);
        normalizeBalanceScale(uuid, data);
        long balance = data.getBalance();
        if (balance < 0L) {
            data.setBalance(0L);
            data.setBalanceScale(getDecimalPlaces());
            storage.savePlayerDataAsync(uuid, data);
            return 0L;
        }
        return balance;
    }

    public long setBalance(@Nonnull UUID uuid, long amount) {
        PlayerDataModel data = storage.getPlayerData(uuid);
        normalizeBalanceScale(uuid, data);
        long clamped = Math.max(0L, amount);
        data.setBalance(clamped);
        data.setBalanceScale(getDecimalPlaces());
        storage.savePlayerDataAsync(uuid, data);
        return clamped;
    }

    public long deposit(@Nonnull UUID uuid, long amount) {
        if (amount <= 0L) {
            return getBalance(uuid);
        }
        PlayerDataModel data = storage.getPlayerData(uuid);
        normalizeBalanceScale(uuid, data);
        long balance = Math.max(0L, data.getBalance());
        long updated;
        try {
            updated = Math.addExact(balance, amount);
        } catch (ArithmeticException overflow) {
            updated = Long.MAX_VALUE;
        }
        data.setBalance(updated);
        data.setBalanceScale(getDecimalPlaces());
        storage.savePlayerDataAsync(uuid, data);
        return updated;
    }

    public boolean withdraw(@Nonnull UUID uuid, long amount) {
        if (amount <= 0L) {
            return false;
        }
        PlayerDataModel data = storage.getPlayerData(uuid);
        normalizeBalanceScale(uuid, data);
        long balance = Math.max(0L, data.getBalance());
        if (balance < amount) {
            return false;
        }
        data.setBalance(balance - amount);
        data.setBalanceScale(getDecimalPlaces());
        storage.savePlayerDataAsync(uuid, data);
        return true;
    }

    public void ensureStartingBalance(@Nonnull UUID uuid) {
        if (!isEnabled()) {
            return;
        }
        long starting = getStartingBalance();
        if (starting <= 0L) {
            return;
        }
        PlayerDataModel data = storage.getPlayerData(uuid);
        normalizeBalanceScale(uuid, data);
        String lastKnownName = data.getLastKnownName();
        boolean firstJoin = data.getLastSeenAt() == 0L && (lastKnownName == null || lastKnownName.isBlank());
        if (firstJoin && data.getBalance() <= 0L) {
            data.setBalance(starting);
            data.setBalanceScale(getDecimalPlaces());
            storage.savePlayerDataAsync(uuid, data);
        }
    }

    public long parseAmount(@Nullable String raw) {
        if (raw == null) {
            return -1L;
        }
        String normalized = raw.trim()
                .replace(",", "")
                .replace(getCurrencySymbol(), "")
                .trim();
        if (normalized.isEmpty()) {
            return -1L;
        }
        try {
            BigDecimal value = new BigDecimal(normalized);
            if (value.signum() < 0) {
                return -1L;
            }
            int scale = getDecimalPlaces();
            BigDecimal shifted = value.setScale(scale, RoundingMode.DOWN).movePointRight(scale);
            return shifted.longValueExact();
        } catch (ArithmeticException | NumberFormatException ignored) {
            return -1L;
        }
    }

    private void normalizeBalanceScale(@Nonnull UUID uuid, @Nonnull PlayerDataModel data) {
        int targetScale = getDecimalPlaces();
        Integer storedScale = data.getBalanceScale();
        if (storedScale != null && storedScale == targetScale) {
            return;
        }
        int sourceScale = storedScale == null ? 0 : Math.max(0, storedScale);
        long value = Math.max(0L, data.getBalance());
        long converted = rescale(value, sourceScale, targetScale);
        data.setBalance(converted);
        data.setBalanceScale(targetScale);
        storage.savePlayerDataAsync(uuid, data);
    }

    private static long rescale(long value, int sourceScale, int targetScale) {
        if (sourceScale == targetScale) {
            return value;
        }
        if (sourceScale < targetScale) {
            long factor = pow10(targetScale - sourceScale);
            try {
                return Math.multiplyExact(value, factor);
            } catch (ArithmeticException ignored) {
                return Long.MAX_VALUE;
            }
        }
        long factor = pow10(sourceScale - targetScale);
        return factor <= 0L ? value : value / factor;
    }

    private static long pow10(int exponent) {
        long value = 1L;
        for (int i = 0; i < exponent; i++) {
            value *= 10L;
        }
        return value;
    }

    @Nonnull
    private String formatCompact(long amount) {
        double value = BigDecimal.valueOf(Math.max(0L, amount), getDecimalPlaces()).doubleValue();
        String[] suffixes = {"k", "m", "b", "t"};
        int idx = 0;
        while (Math.abs(value) >= 1000.0 && idx < suffixes.length) {
            value /= 1000.0;
            idx++;
        }
        if (idx == 0) {
            return formatAmountRaw(amount);
        }
        String formatted = Math.abs(value) >= 10.0
                ? String.format(Locale.US, "%.0f", value)
                : String.format(Locale.US, "%.1f", value);
        return formatted + suffixes[idx - 1];
    }
}

