package xyz.thelegacyvoyage.hyessentialsx.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicFileUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void writeStringAtomicallyCreatesParentsAndReplacesContents() throws Exception {
        Path file = tempDir.resolve("nested").resolve("config.json");

        AtomicFileUtil.writeStringAtomically(file, "first", StandardCharsets.UTF_8);
        AtomicFileUtil.writeStringAtomically(file, "second", StandardCharsets.UTF_8);

        assertTrue(Files.exists(file));
        assertEquals("second", Files.readString(file, StandardCharsets.UTF_8));

        try (var stream = Files.list(file.getParent())) {
            List<String> names = stream
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
            assertEquals(List.of("config.json"), names);
        }
    }
}
