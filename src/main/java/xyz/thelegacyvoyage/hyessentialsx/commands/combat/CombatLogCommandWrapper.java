package xyz.thelegacyvoyage.hyessentialsx.commands.combat;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.ParserContext;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import xyz.thelegacyvoyage.hyessentialsx.managers.CombatLogManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

public final class CombatLogCommandWrapper extends AbstractCommand {

    private final AbstractCommand delegate;
    private final CombatLogManager combatManager;
    private final ConfigManager config;

    public CombatLogCommandWrapper(@Nonnull AbstractCommand delegate,
                                   @Nonnull CombatLogManager combatManager,
                                   @Nonnull ConfigManager config) {
        super(delegate.getName(), delegate.getDescription());
        this.delegate = delegate;
        this.combatManager = combatManager;
        this.config = config;
        for (String alias : delegate.getAliases()) {
            this.addAliases(new String[]{alias});
        }
    }

    @Nullable
    @Override
    public CompletableFuture<Void> acceptCall(@Nonnull CommandSender sender,
                                              @Nonnull ParserContext context,
                                              @Nonnull ParseResult parseResult) {
        if (config.isCombatLogEnabled() && config.isCombatLogBlockCommands()) {
            PlayerRef player = resolvePlayer(sender);
            if (player != null && combatManager.isInCombat(player.getUuid())) {
                if (!PermissionsModule.get().hasPermission(player.getUuid(), CombatLogManager.BYPASS_PERMISSION)) {
                    player.sendMessage(combatManager.buildPrefixedMessage(player,
                            config.getCombatLogCommandBlockedMessage(), java.util.Map.of()));
                    return CompletableFuture.completedFuture(null);
                }
            }
        }
        return delegate.acceptCall(sender, context, parseResult);
    }

    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        if (config.isCombatLogEnabled() && config.isCombatLogBlockCommands()) {
            PlayerRef player = resolvePlayer(context.sender());
            if (player != null && combatManager.isInCombat(player.getUuid())) {
                if (!PermissionsModule.get().hasPermission(player.getUuid(), CombatLogManager.BYPASS_PERMISSION)) {
                    player.sendMessage(combatManager.buildPrefixedMessage(player,
                            config.getCombatLogCommandBlockedMessage(), java.util.Map.of()));
                    return CompletableFuture.completedFuture(null);
                }
            }
        }
        return invokeDelegate(context);
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
        return null;
    }
}

