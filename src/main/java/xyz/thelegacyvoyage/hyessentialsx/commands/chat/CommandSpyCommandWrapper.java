package xyz.thelegacyvoyage.hyessentialsx.commands.chat;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.ParserContext;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandSpyManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandWrapperUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

public final class CommandSpyCommandWrapper extends AbstractCommand {

    private final AbstractCommand delegate;
    private final CommandSpyManager commandSpy;

    public CommandSpyCommandWrapper(@Nonnull AbstractCommand delegate, @Nonnull CommandSpyManager commandSpy) {
        super(delegate.getName(), delegate.getDescription());
        this.delegate = delegate;
        this.commandSpy = commandSpy;
        CommandWrapperUtil.mirrorCommandShape(this, delegate);
    }

    @Nonnull
    public AbstractCommand delegate() {
        return delegate;
    }

    @Nullable
    @Override
    public CompletableFuture<Void> acceptCall(@Nonnull CommandSender sender,
                                              @Nonnull ParserContext context,
                                              @Nonnull ParseResult parseResult) {
        commandSpy.notifyCommand(sender, delegate.getName(), commandText(context));
        return delegate.acceptCall(sender, context, parseResult);
    }

    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        return invokeDelegate(context);
    }

    @Nonnull
    private String commandText(@Nonnull ParserContext context) {
        String raw = context.getRawInput();
        if (raw == null || raw.isBlank()) {
            raw = context.getInputString();
        }
        if (raw == null || raw.isBlank()) {
            return "/" + delegate.getName();
        }
        return raw.startsWith("/") ? raw : "/" + raw;
    }

    @Nonnull
    @SuppressWarnings("unchecked")
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
}
