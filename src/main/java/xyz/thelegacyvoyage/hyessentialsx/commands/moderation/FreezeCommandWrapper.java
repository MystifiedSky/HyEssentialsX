package xyz.thelegacyvoyage.hyessentialsx.commands.moderation;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.ParserContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.managers.FreezeManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandWrapperUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class FreezeCommandWrapper extends AbstractCommand {

    private static final String BYPASS_PERMISSION = "hyessentialsx.freeze.bypass";

    private final AbstractCommand delegate;
    private final FreezeManager freezeManager;

    public FreezeCommandWrapper(@Nonnull AbstractCommand delegate, @Nonnull FreezeManager freezeManager) {
        super(delegate.getName(), delegate.getDescription());
        this.delegate = delegate;
        this.freezeManager = freezeManager;
        CommandWrapperUtil.mirrorCommandShape(this, delegate);
    }

    @Nullable
    @Override
    public CompletableFuture<Void> acceptCall(@Nonnull CommandSender sender,
                                              @Nonnull ParserContext context,
                                              @Nonnull ParseResult parseResult) {
        if (shouldBlock(sender)) {
            return CompletableFuture.completedFuture(null);
        }
        return delegate.acceptCall(sender, context, parseResult);
    }

    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        if (shouldBlock(context.sender())) {
            return CompletableFuture.completedFuture(null);
        }
        return invokeDelegate(context);
    }

    private boolean shouldBlock(@Nonnull CommandSender sender) {
        PlayerRef player = resolvePlayer(sender);
        if (player == null) return false;
        if (xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(sender, BYPASS_PERMISSION)) return false;
        if (!freezeManager.isFrozenOrStored(player.getUuid())) return false;
        String name = delegate.getName();
        if (name != null) {
            String lower = name.toLowerCase();
            if ("freeze".equals(lower) || "unfreeze".equals(lower)) {
                return false;
            }
        }
        player.sendMessage(Messages.m(Messages.tr(player, "freeze.blocked", java.util.Map.of())));
        return true;
    }

    @Nonnull
    private CompletableFuture<Void> invokeDelegate(@Nonnull CommandContext context) {
        try {
            Method method = delegate.getClass().getDeclaredMethod("execute", CommandContext.class);
            method.setAccessible(true);
            Object result = method.invoke(delegate, context);
            if (result instanceof CompletableFuture<?> future) {
                return (CompletableFuture<Void>) future;
            }
            return CompletableFuture.completedFuture(null);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception ignored) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            Method method = delegate.getClass().getDeclaredMethod("executeSync", CommandContext.class);
            method.setAccessible(true);
            method.invoke(delegate, context);
        } catch (Exception ignored) {
        }
        return CompletableFuture.completedFuture(null);
    }

    @Nullable
    private PlayerRef resolvePlayer(@Nonnull CommandSender sender) {
        if (sender instanceof PlayerRef playerRef) {
            return playerRef;
        }
        try {
            Method method = sender.getClass().getMethod("getPlayerRef");
            Object value = method.invoke(sender);
            if (value instanceof PlayerRef playerRef) {
                return playerRef;
            }
        } catch (Exception ignored) {
        }
        try {
            Method method = sender.getClass().getMethod("getUuid");
            Object value = method.invoke(sender);
            if (value instanceof UUID uuid) {
                return Universe.get().getPlayer(uuid);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}

