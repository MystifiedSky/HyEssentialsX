package xyz.thelegacyvoyage.hyessentialsx.managers;

import xyz.thelegacyvoyage.hyessentialsx.models.IpBanModel;
import xyz.thelegacyvoyage.hyessentialsx.util.IpUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class IpBanManager {

    private final StorageManager storage;

    public IpBanManager(@Nonnull StorageManager storage) {
        this.storage = storage;
    }

    public void ban(@Nonnull String ip, @Nonnull IpBanModel ban) {
        String normalized = IpUtil.normalizeIp(ip);
        if (normalized == null) return;
        storage.setIpBan(normalized, ban);
    }

    public boolean unban(@Nonnull String ip) {
        String normalized = IpUtil.normalizeIp(ip);
        if (normalized == null) return false;
        return storage.removeIpBan(normalized);
    }

    @Nullable
    public IpBanModel getBan(@Nonnull String ip) {
        String normalized = IpUtil.normalizeIp(ip);
        if (normalized == null) return null;
        return storage.getIpBan(normalized);
    }

    public boolean isBanned(@Nonnull String ip) {
        return getBan(ip) != null;
    }
}

