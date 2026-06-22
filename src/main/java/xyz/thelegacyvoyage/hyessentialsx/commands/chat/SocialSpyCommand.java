package xyz.thelegacyvoyage.hyessentialsx.commands.chat;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.SocialSpyManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.Map;

public final class SocialSpyCommand extends AbstractPlayerCommand {

    private final SocialSpyManager socialSpyManager;

    public SocialSpyCommand(@Nonnull SocialSpyManager socialSpyManager) {
        super("socialspy", "Toggle SocialSpy monitoring");
        this.socialSpyManager = socialSpyManager;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, SocialSpyManager.PERMISSION_NODE);
        this.addAliases(new String[]{"sspy", "esocialspy"});
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), SocialSpyManager.PERMISSION_NODE)) {
            Messages.noPerm(context, "/socialspy");
            return;
        }

        boolean enabled = socialSpyManager.toggle(playerRef.getUuid());
        Messages.okKey(context, enabled ? "socialspy.enabled" : "socialspy.disabled", Map.of());
    }
}
