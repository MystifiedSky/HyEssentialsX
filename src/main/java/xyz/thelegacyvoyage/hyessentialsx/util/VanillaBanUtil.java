package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.modules.accesscontrol.AccessControlModule;
import com.hypixel.hytale.server.core.modules.accesscontrol.provider.HytaleBanProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.UUID;

public final class VanillaBanUtil {

    private static volatile Field banProviderField;

    private VanillaBanUtil() {}

    public static boolean unbanVanilla(@Nonnull UUID uuid) {
        HytaleBanProvider provider = resolveProvider();
        if (provider == null) {
            return false;
        }
        if (!provider.hasBan(uuid)) {
            return false;
        }
        try {
            provider.modify(bans -> {
                bans.remove(uuid);
                return true;
            });
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isVanillaBanned(@Nonnull UUID uuid) {
        HytaleBanProvider provider = resolveProvider();
        return provider != null && provider.hasBan(uuid);
    }

    @Nullable
    private static HytaleBanProvider resolveProvider() {
        try {
            AccessControlModule module = AccessControlModule.get();
            if (module == null) {
                return null;
            }
            Field field = banProviderField;
            if (field == null) {
                field = AccessControlModule.class.getDeclaredField("banProvider");
                field.setAccessible(true);
                banProviderField = field;
            }
            Object value = field.get(module);
            if (value instanceof HytaleBanProvider provider) {
                return provider;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
