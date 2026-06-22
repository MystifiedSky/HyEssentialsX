package xyz.thelegacyvoyage.hyessentialsx.api;

import javax.annotation.Nullable;

public final class HyEssentialsXApiProvider {

    private static volatile HyEssentialsXApi instance;

    private HyEssentialsXApiProvider() {}

    public static void register(@Nullable HyEssentialsXApi api) {
        instance = api;
    }

    @Nullable
    public static HyEssentialsXApi get() {
        return instance;
    }

    public static void clear() {
        instance = null;
    }
}

