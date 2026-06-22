package xyz.thelegacyvoyage.hyessentialsx.commands.scoreboard;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.ScoreboardManager;
import xyz.thelegacyvoyage.hyessentialsx.ui.scoreboard.ScoreboardAdminMoveUI;
import xyz.thelegacyvoyage.hyessentialsx.ui.scoreboard.ScoreboardEditUI;
import xyz.thelegacyvoyage.hyessentialsx.ui.scoreboard.ScoreboardMoveUI;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class ScoreboardCommand extends AbstractCommand {

    private final ScoreboardManager scoreboardManager;
    private final ConfigManager config;

    public ScoreboardCommand(@Nonnull ScoreboardManager scoreboardManager,
                             @Nonnull ConfigManager config) {
        super("scoreboard", "Manage the scoreboard");
        this.scoreboardManager = scoreboardManager;
        this.config = config;
        this.addAliases(new String[]{"sb"});
        this.addSubCommand(new MoveCommand(scoreboardManager, config));
        this.addSubCommand(new ResetCommand(scoreboardManager, config));
        this.addSubCommand(new AdminMoveCommand(scoreboardManager, config));
        this.addSubCommand(new EditCommand(scoreboardManager, config));
        this.addSubCommand(new ShowCommand(scoreboardManager, config));
        this.addSubCommand(new HideCommand(scoreboardManager, config));
        this.addSubCommand(new ReloadCommand(scoreboardManager, config));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Nullable
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        Messages.send(context, "scoreboard.usage");
        return CompletableFuture.completedFuture(null);
    }

    private static final class MoveCommand extends AbstractPlayerCommand {

        private static final String PERMISSION_NODE = "hyessentialsx.scoreboard.move";
        private final ScoreboardManager scoreboardManager;
        private final ConfigManager config;

        private MoveCommand(@Nonnull ScoreboardManager scoreboardManager,
                            @Nonnull ConfigManager config) {
            super("move", "Adjust your scoreboard position");
            this.scoreboardManager = scoreboardManager;
            this.config = config;
            this.setPermissionGroups();
            CommandPermissionUtil.apply(this, PERMISSION_NODE);
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
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
                Messages.noPerm(context, "/scoreboard move");
                return;
            }
            if (!config.isScoreboardEnabled()) {
                Messages.errKey(context, "scoreboard.disabled", Map.of());
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                Messages.errKey(context, "error.player_only", Map.of());
                return;
            }
            ScoreboardMoveUI page = new ScoreboardMoveUI(playerRef, config, scoreboardManager);
            player.getPageManager().openCustomPage(ref, store, page);
        }
    }

    private static final class EditCommand extends AbstractPlayerCommand {

        private static final String PERMISSION_NODE = "hyessentialsx.scoreboard.edit";
        private final ScoreboardManager scoreboardManager;
        private final ConfigManager config;

        private EditCommand(@Nonnull ScoreboardManager scoreboardManager,
                            @Nonnull ConfigManager config) {
            super("edit", "Edit scoreboard lines");
            this.scoreboardManager = scoreboardManager;
            this.config = config;
            this.setPermissionGroups();
            CommandPermissionUtil.apply(this, PERMISSION_NODE);
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
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
                Messages.noPerm(context, "/scoreboard edit");
                return;
            }
            if (!config.isScoreboardEnabled()) {
                Messages.errKey(context, "scoreboard.disabled", Map.of());
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                Messages.errKey(context, "error.player_only", Map.of());
                return;
            }
            ScoreboardEditUI page = new ScoreboardEditUI(playerRef, config, scoreboardManager);
            player.getPageManager().openCustomPage(ref, store, page);
        }
    }

    private static final class ResetCommand extends AbstractPlayerCommand {

        private static final String PERMISSION_NODE = "hyessentialsx.scoreboard.reset";
        private final ScoreboardManager scoreboardManager;
        private final ConfigManager config;

        private ResetCommand(@Nonnull ScoreboardManager scoreboardManager,
                             @Nonnull ConfigManager config) {
            super("reset", "Reset your scoreboard position");
            this.scoreboardManager = scoreboardManager;
            this.config = config;
            this.setPermissionGroups();
            CommandPermissionUtil.apply(this, PERMISSION_NODE);
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
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
                Messages.noPerm(context, "/scoreboard reset");
                return;
            }
            if (!config.isScoreboardEnabled()) {
                Messages.errKey(context, "scoreboard.disabled", Map.of());
                return;
            }
            scoreboardManager.resetPlayerOffset(playerRef.getUuid());
            scoreboardManager.refreshPlayer(playerRef);
            Messages.okKey(context, "scoreboard.reset", Map.of());
        }
    }

    private static final class AdminMoveCommand extends AbstractPlayerCommand {

        private static final String PERMISSION_NODE = "hyessentialsx.scoreboard.adminmove";
        private final ScoreboardManager scoreboardManager;
        private final ConfigManager config;

        private AdminMoveCommand(@Nonnull ScoreboardManager scoreboardManager,
                                 @Nonnull ConfigManager config) {
            super("adminmove", "Adjust the default scoreboard position");
            this.scoreboardManager = scoreboardManager;
            this.config = config;
            this.setPermissionGroups();
            CommandPermissionUtil.apply(this, PERMISSION_NODE);
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
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
                Messages.noPerm(context, "/scoreboard adminmove");
                return;
            }
            if (!config.isScoreboardEnabled()) {
                Messages.errKey(context, "scoreboard.disabled", Map.of());
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                Messages.errKey(context, "error.player_only", Map.of());
                return;
            }
            ScoreboardAdminMoveUI page = new ScoreboardAdminMoveUI(playerRef, config, scoreboardManager);
            player.getPageManager().openCustomPage(ref, store, page);
        }
    }

    private static final class ShowCommand extends AbstractPlayerCommand {

        private static final String PERMISSION_NODE = "hyessentialsx.scoreboard.show";
        private final ScoreboardManager scoreboardManager;
        private final ConfigManager config;

        private ShowCommand(@Nonnull ScoreboardManager scoreboardManager,
                            @Nonnull ConfigManager config) {
            super("show", "Show the scoreboard");
            this.scoreboardManager = scoreboardManager;
            this.config = config;
            this.setPermissionGroups();
            CommandPermissionUtil.apply(this, PERMISSION_NODE);
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
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
                Messages.noPerm(context, "/scoreboard show");
                return;
            }
            if (!config.isScoreboardEnabled()) {
                Messages.errKey(context, "scoreboard.disabled", Map.of());
                return;
            }
            scoreboardManager.setPlayerHidden(playerRef.getUuid(), false);
            scoreboardManager.refreshPlayer(playerRef);
            Messages.okKey(context, "scoreboard.shown", Map.of());
        }
    }

    private static final class HideCommand extends AbstractPlayerCommand {

        private static final String PERMISSION_NODE = "hyessentialsx.scoreboard.hide";
        private final ScoreboardManager scoreboardManager;
        private final ConfigManager config;

        private HideCommand(@Nonnull ScoreboardManager scoreboardManager,
                            @Nonnull ConfigManager config) {
            super("hide", "Hide the scoreboard");
            this.scoreboardManager = scoreboardManager;
            this.config = config;
            this.setPermissionGroups();
            CommandPermissionUtil.apply(this, PERMISSION_NODE);
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
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
                Messages.noPerm(context, "/scoreboard hide");
                return;
            }
            if (!config.isScoreboardEnabled()) {
                Messages.errKey(context, "scoreboard.disabled", Map.of());
                return;
            }
            scoreboardManager.setPlayerHidden(playerRef.getUuid(), true);
            scoreboardManager.refreshPlayer(playerRef);
            Messages.okKey(context, "scoreboard.hidden", Map.of());
        }
    }

    private static final class ReloadCommand extends CommandBase {

        private static final String PERMISSION_NODE = "hyessentialsx.scoreboard.reload";
        private final ScoreboardManager scoreboardManager;
        private final ConfigManager config;

        private ReloadCommand(@Nonnull ScoreboardManager scoreboardManager,
                              @Nonnull ConfigManager config) {
            super("reload", "Reload scoreboard configuration");
            this.scoreboardManager = scoreboardManager;
            this.config = config;
            this.setPermissionGroups();
            CommandPermissionUtil.apply(this, PERMISSION_NODE);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
                Messages.noPerm(context, "/scoreboard reload");
                return;
            }
            if (!config.isScoreboardEnabled()) {
                Messages.errKey(context, "scoreboard.disabled", Map.of());
                return;
            }
            try {
                scoreboardManager.reloadConfiguration();
                Messages.okKey(context, "scoreboard.reloaded", Map.of());
            } catch (Exception e) {
                Messages.err(context, "Failed to reload scoreboard config: " + e.getMessage());
            }
        }
    }
}


