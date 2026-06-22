package xyz.thelegacyvoyage.hyessentialsx.managers;

import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;

import javax.annotation.Nonnull;
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

    public long getStartingBalance() {
        return Math.max(0L, config.getEconomyStartingBalance());
    }

    @Nonnull
    public String formatAmount(long amount) {
        String symbol = getCurrencySymbol();
        return symbol + amount;
    }

    public long getBalance(@Nonnull UUID uuid) {
        PlayerDataModel data = storage.getPlayerData(uuid);
        long balance = data.getBalance();
        if (balance < 0L) {
            data.setBalance(0L);
            storage.savePlayerDataAsync(uuid, data);
            return 0L;
        }
        return balance;
    }

    public long setBalance(@Nonnull UUID uuid, long amount) {
        PlayerDataModel data = storage.getPlayerData(uuid);
        long clamped = Math.max(0L, amount);
        data.setBalance(clamped);
        storage.savePlayerDataAsync(uuid, data);
        return clamped;
    }

    public long deposit(@Nonnull UUID uuid, long amount) {
        if (amount <= 0L) {
            return getBalance(uuid);
        }
        PlayerDataModel data = storage.getPlayerData(uuid);
        long balance = Math.max(0L, data.getBalance());
        long updated;
        try {
            updated = Math.addExact(balance, amount);
        } catch (ArithmeticException overflow) {
            updated = Long.MAX_VALUE;
        }
        data.setBalance(updated);
        storage.savePlayerDataAsync(uuid, data);
        return updated;
    }

    public boolean withdraw(@Nonnull UUID uuid, long amount) {
        if (amount <= 0L) {
            return false;
        }
        PlayerDataModel data = storage.getPlayerData(uuid);
        long balance = Math.max(0L, data.getBalance());
        if (balance < amount) {
            return false;
        }
        data.setBalance(balance - amount);
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
        String lastKnownName = data.getLastKnownName();
        boolean firstJoin = data.getLastSeenAt() == 0L && (lastKnownName == null || lastKnownName.isBlank());
        if (firstJoin && data.getBalance() <= 0L) {
            data.setBalance(starting);
            storage.savePlayerDataAsync(uuid, data);
        }
    }
}
