package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import xyz.thelegacyvoyage.hyessentialsx.managers.NicknameManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.Map;

public final class RealNameCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.realname";

    private final NicknameManager nicknames;
    private final RequiredArg<String> nameArg;

    public RealNameCommand(@Nonnull NicknameManager nicknames) {
        super("realname", "Find the real name behind a nickname");
        this.nicknames = nicknames;
        this.setPermissionGroups();
        CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.nameArg = withRequiredArg("name", "Nickname or player name", ArgTypes.STRING);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/realname");
            return;
        }
        String query = context.get(nameArg);
        NicknameManager.Match match = nicknames.findByNicknameOrName(query);
        if (match == null) {
            Messages.errKey(context, "realname.not_found", Map.of("name", query));
            return;
        }
        String nickname = match.nickname() == null || match.nickname().isBlank() ? "none" : match.nickname();
        Messages.okKey(context, "realname.result", Map.of(
                "nick", nickname,
                "player", match.realName()
        ));
    }
}
