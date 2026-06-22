package xyz.thelegacyvoyage.hyessentialsx.commands.plugin;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import xyz.thelegacyvoyage.hyessentialsx.managers.LanguageManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * /hyessentialsx language [code]
 */
public class LanguageSubCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.language";

    private final LanguageManager languageManager;
    private final OptionalArg<String> codeArg;

    public LanguageSubCommand(@Nonnull LanguageManager languageManager) {
        super("language", "Set your HyEssentialsX language");
        this.languageManager = languageManager;
        this.setPermissionGroup(null);
        this.codeArg = withOptionalArg("code", "Language code (e.g. en-us)", ArgTypes.STRING);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/hyessentialsx language");
            return;
        }

        PlayerRef player = resolvePlayer(context);
        if (player == null) {
            Messages.errKey(context, "error.player_only", Map.of());
            return;
        }

        String code = context.get(codeArg);
        if (code == null || code.isBlank()) {
            String current = languageManager.getPlayerLanguage(player.getUuid());
            if (current == null) current = languageManager.getDefaultLanguage();
            Messages.sendKey(context, "language.current",
                    Map.of("language", current, "available", String.join(", ", languageManager.getAvailableLanguages())));
            return;
        }

        String normalized = code.trim().toLowerCase();
        if (!languageManager.hasLanguage(normalized)) {
            Messages.errKey(context, "language.not_found",
                    Map.of("language", normalized, "available", String.join(", ", languageManager.getAvailableLanguages())));
            return;
        }

        languageManager.setPlayerLanguage(player.getUuid(), normalized);
        Messages.okKey(context, "language.changed", Map.of("language", normalized));
    }

    private static PlayerRef resolvePlayer(@Nonnull CommandContext context) {
        Object sender = context.sender();
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

