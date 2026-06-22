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
        this.setPermissionGroup(null);
        CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.modArg = withRequiredArg("mod", "Mod to migrate from", ArgTypes.STRING);
        this.mergeArg = withOptionalArg("merge", "Merge with existing data (default: true)", ArgTypes.BOOLEAN);
    }

    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context) {
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
            Messages.noPerm(context, "/hyessentialsx migrate");
            return CompletableFuture.completedFuture(null);
        }

        String modName = context.get(modArg);
        MigrationManager.ModType mod = MigrationManager.ModType.fromName(modName);
        if (mod == null) {
            Messages.err(context, "Invalid mod. Supported: " + MigrationManager.ModType.supportedNames());
            return CompletableFuture.completedFuture(null);
        }

        Boolean mergeValue = context.get(mergeArg);
        boolean merge = mergeValue == null || mergeValue;

        Path sourceDir = migrationManager.resolveSourceDir(mod);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            Messages.err(context, "Source directory not found: " + sourceDir.toAbsolutePath());
            return CompletableFuture.completedFuture(null);
        }

        Messages.warn(context, "Starting migration from " + mod.getDisplayName() + "...");
        Messages.send(context, "&7Source: " + sourceDir.toAbsolutePath());
        Messages.send(context, "&7Merge mode: " + (merge ? "enabled" : "disabled (WILL REPLACE DATA)"));

        Messages.warn(context, "Creating backup of HyEssentialsX data...");
        Path backup = createBackup();
        if (backup != null) {
            Messages.ok(context, "Backup created: " + backup.getFileName());
        } else {
            Messages.warn(context, "Warning: failed to create backup, continuing anyway.");
        }

        ModMigration.MigrationResult result = migrationManager.migrate(mod, merge);
        if (result.isSuccess()) {
            Messages.ok(context, "Migration completed successfully.");
            Messages.send(context, "&7" + result.getSummary());
            for (String notice : result.getNotices()) {
                Messages.warn(context, notice);
            }
            if (backup != null) {
                Messages.send(context, "&7Backup saved to: " + backup.toAbsolutePath());
            }
            Messages.warn(context, "Please restart the server for changes to take full effect.");
        } else {
            Messages.err(context, "Migration failed: " + result.getErrorMessage());
            if (backup != null) {
                Messages.warn(context, "You can restore from backup: " + backup.toAbsolutePath());
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
