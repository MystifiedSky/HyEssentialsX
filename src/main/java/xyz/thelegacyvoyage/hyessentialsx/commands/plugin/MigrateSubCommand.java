package xyz.thelegacyvoyage.hyessentialsx.commands.plugin;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import xyz.thelegacyvoyage.hyessentialsx.managers.SpawnManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.migration.MigrationManager;
import xyz.thelegacyvoyage.hyessentialsx.migration.ModMigration;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class MigrateSubCommand extends AbstractAsyncCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.migrate";
    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final MigrationManager migrationManager;
    private final Path dataFolder;
    private final RequiredArg<String> modArg;
    private final OptionalArg<Boolean> mergeArg;

    public MigrateSubCommand(@Nonnull StorageManager storage,
                             @Nonnull SpawnManager spawnManager,
                             @Nonnull Path dataFolder) {
        super("migrate", "Migrate data from other mods");
        this.migrationManager = new MigrationManager(storage, spawnManager, dataFolder);
        this.dataFolder = dataFolder;
        this.setPermissionGroups();
        CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.modArg = withRequiredArg("mod", "Mod to migrate from", ArgTypes.STRING);
        this.mergeArg = withOptionalArg("merge", "Merge with existing data (default: true)", ArgTypes.BOOLEAN);
    }

    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/hyessentialsx migrate");
            return CompletableFuture.completedFuture(null);
        }

        String modName = context.get(modArg);
        MigrationManager.ModType mod = MigrationManager.ModType.fromName(modName);
        if (mod == null) {
            Messages.errKey(context, "migrate.invalid_mod", java.util.Map.of(
                    "mods", MigrationManager.ModType.supportedNames()
            ));
            return CompletableFuture.completedFuture(null);
        }

        Boolean mergeValue = context.get(mergeArg);
        boolean merge = mergeValue == null || mergeValue;

        Path sourceDir = migrationManager.resolveSourceDir(mod);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            Messages.errKey(context, "migrate.source_missing", java.util.Map.of(
                    "path", sourceDir.toAbsolutePath().toString()
            ));
            return CompletableFuture.completedFuture(null);
        }

        Messages.warnKey(context, "migrate.start", java.util.Map.of("mod", mod.getDisplayName()));
        Messages.send(context, Messages.tr(null, "migrate.source", java.util.Map.of(
                "path", sourceDir.toAbsolutePath().toString()
        )));
        String mergeState = Messages.tr(null,
                merge ? "migrate.merge.enabled" : "migrate.merge.disabled",
                java.util.Map.of());
        Messages.send(context, Messages.tr(null, "migrate.merge", java.util.Map.of(
                "state", mergeState
        )));

        Messages.warnKey(context, "migrate.backup.start", java.util.Map.of());
        Path backup = createBackup();
        if (backup != null) {
            Messages.okKey(context, "migrate.backup.created", java.util.Map.of(
                    "file", backup.getFileName().toString()
            ));
        } else {
            Messages.warnKey(context, "migrate.backup.failed", java.util.Map.of());
        }

        ModMigration.MigrationResult result = migrationManager.migrate(mod, merge);
        if (result.isSuccess()) {
            Messages.okKey(context, "migrate.success", java.util.Map.of());
            Messages.send(context, Messages.tr(null, "migrate.summary", java.util.Map.of(
                    "summary", result.getSummary()
            )));
            for (String notice : result.getNotices()) {
                Messages.warn(context, notice);
            }
            if (backup != null) {
                Messages.send(context, Messages.tr(null, "migrate.backup.saved", java.util.Map.of(
                        "path", backup.toAbsolutePath().toString()
                )));
            }
            Messages.warnKey(context, "migrate.restart", java.util.Map.of());
        } else {
            Messages.errKey(context, "migrate.failed", java.util.Map.of(
                    "error", result.getErrorMessage()
            ));
            if (backup != null) {
                Messages.warnKey(context, "migrate.backup.restore", java.util.Map.of(
                        "path", backup.toAbsolutePath().toString()
                ));
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    private Path createBackup() {
        try {
            Path parent = dataFolder.getParent() != null ? dataFolder.getParent() : dataFolder;
            Path backupDir = parent.resolve("HyEssentialsX-Backups");
            Files.createDirectories(backupDir);
            String timestamp = LocalDateTime.now().format(BACKUP_FORMAT);
            Path backupFile = backupDir.resolve("HyEssentialsX-Backup_" + timestamp + ".zip");
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(backupFile))) {
                zipDirectory(dataFolder.toFile(), dataFolder.getFileName().toString(), zos);
            }
            return backupFile;
        } catch (Exception e) {
            return null;
        }
    }

    private void zipDirectory(@Nonnull File dir, @Nonnull String baseName, @Nonnull ZipOutputStream zos)
            throws IOException {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                zipDirectory(file, baseName + "/" + file.getName(), zos);
                continue;
            }
            try (FileInputStream fis = new FileInputStream(file)) {
                ZipEntry entry = new ZipEntry(baseName + "/" + file.getName());
                zos.putNextEntry(entry);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = fis.read(buffer)) >= 0) {
                    zos.write(buffer, 0, length);
                }
                zos.closeEntry();
            }
        }
    }
}
