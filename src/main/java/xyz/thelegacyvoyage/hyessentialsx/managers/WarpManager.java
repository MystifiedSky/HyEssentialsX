package xyz.thelegacyvoyage.hyessentialsx.managers;

import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;
import xyz.thelegacyvoyage.hyessentialsx.util.StorageManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class WarpManager {

    private final StorageManager storage;

    public WarpManager(@Nonnull StorageManager storage) {
        this.storage = storage;
    }

    public void setWarp(@Nonnull WarpModel warp) {
        storage.setWarp(warp.getName(), warp);
    }

    @Nullable
    public WarpModel getWarp(@Nonnull String name) {
        return storage.getWarp(name);
    }

    public boolean deleteWarp(@Nonnull String name) {
        return storage.deleteWarp(name);
    }

    @Nonnull
    public List<String> listWarps() {
        Map<String, WarpModel> warps = storage.getWarps();
        return new ArrayList<>(warps.keySet());
    }
}
