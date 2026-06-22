package xyz.thelegacyvoyage.hyessentialsx.util;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class AtomicFileUtil {

    private AtomicFileUtil() {}

    public static void writeStringAtomically(@Nonnull Path path,
                                             @Nonnull String content,
                                             @Nonnull Charset charset) throws IOException {
        Path absolutePath = path.toAbsolutePath();
        Path directory = absolutePath.getParent();
        if (directory != null) {
            Files.createDirectories(directory);
        }

        Path tempFile = Files.createTempFile(
                directory,
                buildTempPrefix(absolutePath),
                ".tmp"
        );
        try {
            Files.writeString(
                    tempFile,
                    content,
                    charset,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            moveAtomically(tempFile, absolutePath);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Nonnull
    private static String buildTempPrefix(@Nonnull Path path) {
        String name = path.getFileName() != null ? path.getFileName().toString() : "tmp";
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < name.length() && prefix.length() < 32; i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                prefix.append(c);
            } else {
                prefix.append('_');
            }
        }
        while (prefix.length() < 3) {
            prefix.append('_');
        }
        return prefix.toString();
    }

    private static void moveAtomically(@Nonnull Path source, @Nonnull Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
