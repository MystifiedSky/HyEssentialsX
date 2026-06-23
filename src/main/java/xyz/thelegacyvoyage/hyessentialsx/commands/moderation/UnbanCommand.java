package xyz.thelegacyvoyage.hyessentialsx.commands.moderation;

import com.hypixel.hytale.server.core.auth.ProfileServiceClient;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import xyz.thelegacyvoyage.hyessentialsx.managers.BanManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.util.StaffActionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.VanillaBanUtil;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

public final class UnbanCommand extends AbstractAsyncCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.unban";

    private final BanManager banManager;
    private final StorageManager storage;
    private final RequiredArg<ProfileServiceClient.PublicGameProfile> profileArg;

    public UnbanCommand(@Nonnull BanManager banManager, @Nonnull StorageManager storage) {
        super("unban", "Unbans a player");
        this.banManager = banManager;
        this.storage = storage;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"pardon"});
        this.profileArg = withRequiredArg("player", "Player name", ArgTypes.GAME_PROFILE_LOOKUP);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/unban");
            return CompletableFuture.completedFuture(null);
        }

        ProfileServiceClient.PublicGameProfile profile = context.get(profileArg);
        UUID uuid = null;
        String name = null;
        if (profile != null) {
            uuid = profile.getUuid();
            name = profile.getUsername();
        }
        if (uuid == null || name == null || name.isBlank()) {
            Messages.errKey(context, "player.not_found", java.util.Map.of());
            return CompletableFuture.completedFuture(null);
        }

        if (banManager.isBanned(uuid)) {
            banManager.unban(uuid);
        }
        VanillaBanUtil.unbanVanilla(uuid);
        StaffActionUtil.log(storage, StaffActionUtil.resolveActorName(context), "unban", uuid, name, "");
        Messages.okKey(context, "ban.unbanned", java.util.Map.of("player", name));
        return CompletableFuture.completedFuture(null);
    }
}




