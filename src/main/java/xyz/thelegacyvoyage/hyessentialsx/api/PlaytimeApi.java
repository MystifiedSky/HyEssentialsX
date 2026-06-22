package xyz.thelegacyvoyage.hyessentialsx.api;

import javax.annotation.Nonnull;
import java.util.UUID;

public interface PlaytimeApi {
    long getPlaytimeSeconds(@Nonnull UUID uuid);

    void setPlaytimeSeconds(@Nonnull UUID uuid, long seconds);

    void addPlaytimeSeconds(@Nonnull UUID uuid, long seconds);

    void resetPlaytime(@Nonnull UUID uuid);
}
