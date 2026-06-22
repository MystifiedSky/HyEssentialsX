package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class PlaceholderApiUtil {

    private static final String PLACEHOLDER_API_CLASS = "at.helpch.placeholderapi.PlaceholderAPI";
    private static final Object INIT_LOCK = new Object();
    private static volatile boolean initialized = false;
    private static volatile boolean available = false;
    private static volatile Method setPlaceholdersMethod;
    private static volatile Method setPlaceholdersStringMethod;
    private static volatile boolean expectsMessageFirst = true;
    private static volatile boolean expectsPlayerRef = true;
    private static volatile boolean expectsPlayerEntity = false;
    private static volatile boolean expectsStringFirst = true;
    private static volatile boolean expectsStringPlayerRef = true;
    private static volatile boolean expectsStringPlayerEntity = false;

    private PlaceholderApiUtil() {
    }

    @Nonnull
    public static Message apply(@Nullable PlayerRef player,
                                @Nonnull String text) {
        if (!isAvailable()) {
            return Messages.m(text);
        }
        PlayerRef effectivePlayer = player;
        if (effectivePlayer == null) {
            try {
                var players = Universe.get().getPlayers();
                if (players != null && !players.isEmpty()) {
                    effectivePlayer = players.iterator().next();
                }
            } catch (Throwable ignored) {
            }
        }
        if (effectivePlayer == null) {
            return Messages.m(text);
        }
        return applyWithWorldThread(effectivePlayer, text);
    }

    @Nonnull
    public static String applyString(@Nullable PlayerRef player,
                                     @Nonnull String text) {
        if (!isAvailable()) {
            return text;
        }
        PlayerRef effectivePlayer = player;
        if (effectivePlayer == null) {
            try {
                var players = Universe.get().getPlayers();
                if (players != null && !players.isEmpty()) {
                    effectivePlayer = players.iterator().next();
                }
            } catch (Throwable ignored) {
            }
        }
        if (effectivePlayer == null) {
            return text;
        }
        return applyStringWithWorldThread(effectivePlayer, text);
    }

    @Nonnull
    private static Message applyWithWorldThread(@Nonnull PlayerRef player,
                                                @Nonnull String text) {
        World world = resolveWorld(player);
        if (world == null || world.isInThread()) {
            return applyInternal(player, text);
        }
        CompletableFuture<Message> future = new CompletableFuture<>();
        try {
            world.execute(() -> future.complete(applyInternal(player, text)));
            return future.get(1, TimeUnit.SECONDS);
        } catch (Throwable ignored) {
            return applyInternal(player, text);
        }
    }

    @Nonnull
    private static String applyStringWithWorldThread(@Nonnull PlayerRef player,
                                                     @Nonnull String text) {
        World world = resolveWorld(player);
        if (world == null || world.isInThread()) {
            return applyStringInternal(player, text);
        }
        CompletableFuture<String> future = new CompletableFuture<>();
        try {
            world.execute(() -> future.complete(applyStringInternal(player, text)));
            return future.get(1, TimeUnit.SECONDS);
        } catch (Throwable ignored) {
            return applyStringInternal(player, text);
        }
    }

    @Nonnull
    private static Message applyInternal(@Nonnull PlayerRef player,
                                         @Nonnull String text) {
        String resolved = applyStringInternal(player, text);
        if (resolved != null) {
            return Messages.m(resolved);
        }
        Message base = Messages.m(text);
        try {
            if (setPlaceholdersMethod != null) {
                Object first = expectsMessageFirst ? base : resolvePlayerArg(player);
                Object second = expectsMessageFirst ? resolvePlayerArg(player) : base;
                Object result = setPlaceholdersMethod.invoke(null, first, second);
                if (result instanceof Message msg) {
                    return msg;
                }
            }
        } catch (Throwable ignored) {
        }
        return base;
    }

    @Nullable
    private static String applyStringInternal(@Nonnull PlayerRef player,
                                              @Nonnull String text) {
        if (setPlaceholdersStringMethod == null) {
            return text;
        }
        try {
            Object first = expectsStringFirst ? text : resolveStringPlayerArg(player);
            Object second = expectsStringFirst ? resolveStringPlayerArg(player) : text;
            Object result = setPlaceholdersStringMethod.invoke(null, first, second);
            if (result instanceof String resolved) {
                return resolved;
            }
        } catch (Throwable ignored) {
        }
        return text;
    }

    @Nullable
    private static World resolveWorld(@Nonnull PlayerRef player) {
        try {
            UUID worldId = player.getWorldUuid();
            if (worldId == null) return null;
            return Universe.get().getWorld(worldId);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static Object resolvePlayerArg(@Nullable PlayerRef playerRef) {
        return resolvePlayerArg(playerRef, expectsPlayerRef, expectsPlayerEntity);
    }

    @Nullable
    private static Object resolveStringPlayerArg(@Nullable PlayerRef playerRef) {
        return resolvePlayerArg(playerRef, expectsStringPlayerRef, expectsStringPlayerEntity);
    }

    @Nullable
    private static Object resolvePlayerArg(@Nullable PlayerRef playerRef,
                                           boolean expectsRef,
                                           boolean expectsEntity) {
        if (expectsRef) {
            return playerRef;
        }
        if (expectsEntity) {
            return resolvePlayerEntity(playerRef);
        }
        return playerRef;
    }

    @Nullable
    private static Player resolvePlayerEntity(@Nullable PlayerRef playerRef) {
        if (playerRef == null) return null;
        try {
            if (playerRef.getReference() != null && playerRef.getReference().isValid()) {
                var store = playerRef.getReference().getStore();
                if (store != null) {
                    return store.getComponent(playerRef.getReference(), Player.getComponentType());
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean isAvailable() {
        if (!initialized) {
            synchronized (INIT_LOCK) {
                if (!initialized) {
                    try {
                        Class<?> apiClass = Class.forName(PLACEHOLDER_API_CLASS);
                        Method messageMethod = findMethod(apiClass, PlayerRef.class, Message.class);
                        boolean messageFirst = false;
                        boolean messagePlayerRef = true;
                        boolean messagePlayerEntity = false;
                        if (messageMethod == null) {
                            messageMethod = findMethod(apiClass, Message.class, PlayerRef.class);
                            messageFirst = true;
                            messagePlayerRef = true;
                            messagePlayerEntity = false;
                        }
                        if (messageMethod == null) {
                            messageMethod = findMethod(apiClass, Player.class, Message.class);
                            messageFirst = false;
                            messagePlayerRef = false;
                            messagePlayerEntity = true;
                        }
                        if (messageMethod == null) {
                            messageMethod = findMethod(apiClass, Message.class, Player.class);
                            messageFirst = true;
                            messagePlayerRef = false;
                            messagePlayerEntity = true;
                        }

                        Method stringMethod = findMethod(apiClass, PlayerRef.class, String.class);
                        boolean stringFirst = false;
                        boolean stringPlayerRef = true;
                        boolean stringPlayerEntity = false;
                        if (stringMethod == null) {
                            stringMethod = findMethod(apiClass, String.class, PlayerRef.class);
                            stringFirst = true;
                            stringPlayerRef = true;
                            stringPlayerEntity = false;
                        }
                        if (stringMethod == null) {
                            stringMethod = findMethod(apiClass, Player.class, String.class);
                            stringFirst = false;
                            stringPlayerRef = false;
                            stringPlayerEntity = true;
                        }
                        if (stringMethod == null) {
                            stringMethod = findMethod(apiClass, String.class, Player.class);
                            stringFirst = true;
                            stringPlayerRef = false;
                            stringPlayerEntity = true;
                        }

                        setPlaceholdersMethod = messageMethod;
                        expectsMessageFirst = messageFirst;
                        expectsPlayerRef = messagePlayerRef;
                        expectsPlayerEntity = messagePlayerEntity;
                        setPlaceholdersStringMethod = stringMethod;
                        expectsStringFirst = stringFirst;
                        expectsStringPlayerRef = stringPlayerRef;
                        expectsStringPlayerEntity = stringPlayerEntity;
                        available = messageMethod != null || stringMethod != null;
                    } catch (Throwable ignored) {
                        available = false;
                        setPlaceholdersMethod = null;
                        setPlaceholdersStringMethod = null;
                    }
                    initialized = true;
                }
            }
        }
        return available && (setPlaceholdersMethod != null || setPlaceholdersStringMethod != null);
    }

    @Nullable
    private static Method findMethod(@Nonnull Class<?> target, @Nonnull Class<?> first, @Nonnull Class<?> second) {
        try {
            return target.getMethod("setPlaceholders", first, second);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}

