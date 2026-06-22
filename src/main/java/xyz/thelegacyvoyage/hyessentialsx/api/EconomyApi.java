package xyz.thelegacyvoyage.hyessentialsx.api;

import javax.annotation.Nonnull;
import java.util.UUID;

public interface EconomyApi {
    boolean isEnabled();

    @Nonnull
    String getCurrencySymbol();

    @Nonnull
    String formatAmount(long amount);

    long getBalance(@Nonnull UUID uuid);

    long setBalance(@Nonnull UUID uuid, long amount);

    long deposit(@Nonnull UUID uuid, long amount);

    boolean withdraw(@Nonnull UUID uuid, long amount);
}

