package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;

public final class PlaceholderApiUtil {

    private static final String PLACEHOLDER_API_CLASS = "at.helpch.placeholderapi.PlaceholderAPI";
    private static final Object INIT_LOCK = new Object();
    private static volatile boolean initialized = false;
    private static volatile boolean available = false;
    private static volatile Method setPlaceholdersMethod;
    private static volatile boolean expectsMessageFirst = true;
    private static volatile boolean expectsPlayerRef = true;
    private static volatile boolean expectsPlayerEntity = false;

    private PlaceholderApiUtil() {
    }

    @Nonnull
    public static Message apply(@Nullable PlayerRef player,
                                @Nonnull String text,
                                @Nonnull ConfigManager config) {
        Message base = Messages.m(text);
        if (!config.isPlaceholderApiEnabled()) {
            return base;
        }
        if (!isAvailable()) {
            return base;
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
            return base;
        }
        try {
            Object first = expectsMessageFirst ? base : resolvePlayerArg(effectivePlayer);
            Object second = expectsMessageFirst ? resolvePlayerArg(effectivePlayer) : base;
            Object result = setPlaceholdersMethod.invoke(null, first, second);
            if (result instanceof Message msg) {
                return msg;
            }
        } catch (Throwable ignored) {
        }
        return base;
    }

    @Nullable
    private static Object resolvePlayerArg(@Nullable PlayerRef playerRef) {
        if (expectsPlayerRef) {
            return playerRef;
        }
        if (expectsPlayerEntity) {
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
                        Method candidate = null;
                        boolean messageFirst = true;
                        boolean playerRef = true;
                        boolean playerEntity = false;
                        for (Method method : apiClass.getMethods()) {
                            if (!"setPlaceholders".equals(method.getName())) {
                                continue;
                            }
                            Class<?>[] params = method.getParameterTypes();
                            if (params.length != 2) {
                                continue;
                            }
                            if (Message.class.equals(params[0]) && PlayerRef.class.equals(params[1])) {
                                candidate = method;
                                messageFirst = true;
                                playerRef = true;
                                playerEntity = false;
                                break;
                            }
                            if (PlayerRef.class.equals(params[0]) && Message.class.equals(params[1])) {
                                candidate = method;
                                messageFirst = false;
                                playerRef = true;
                                playerEntity = false;
                                break;
                            }
                            if (Message.class.equals(params[0]) && Player.class.equals(params[1])) {
                                candidate = method;
                                messageFirst = true;
                                playerRef = false;
                                playerEntity = true;
                            } else if (Player.class.equals(params[0]) && Message.class.equals(params[1])) {
                                candidate = method;
                                messageFirst = false;
                                playerRef = false;
                                playerEntity = true;
                            }
                        }
                        setPlaceholdersMethod = candidate;
                        expectsMessageFirst = messageFirst;
                        expectsPlayerRef = playerRef;
                        expectsPlayerEntity = playerEntity;
                        available = candidate != null;
                    } catch (Throwable ignored) {
                        available = false;
                        setPlaceholdersMethod = null;
                    }
                    initialized = true;
                }
            }
        }
        return available && setPlaceholdersMethod != null;
    }
}

