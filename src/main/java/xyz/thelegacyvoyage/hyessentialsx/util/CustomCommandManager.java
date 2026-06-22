package xyz.thelegacyvoyage.hyessentialsx.util;

import org.yaml.snakeyaml.Yaml;
import xyz.thelegacyvoyage.hyessentialsx.models.CustomCommandDefinition;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CustomCommandManager {

    private final Path file;
    private final Yaml yaml = new Yaml();
    private final Map<String, CustomCommandDefinition> commands = new LinkedHashMap<>();

    public CustomCommandManager(@Nonnull Path dataFolder) {
        this.file = dataFolder.resolve("commands.yml");
        ensureExists();
        load();
    }

    @Nonnull
    public Map<String, CustomCommandDefinition> getCommands() {
        return Collections.unmodifiableMap(commands);
    }

    private void ensureExists() {
        if (Files.exists(file)) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, defaultYaml(), StandardCharsets.UTF_8);
            Log.info("Created default commands.yml");
        } catch (Exception e) {
            Log.warn("Failed to create commands.yml: " + e.getMessage());
        }
    }

    private String defaultYaml() {
        return ""
                + "# Custom text commands\n"
                + "# Permission is auto-generated as: hyessentialsx.custom.<command>\n"
                + "# Example: /discord -> hyessentialsx.custom.discord\n"
                + "commands:\n"
                + "  discord:\n"
                + "    message: \"&#A7F3D0Join our Discord: https://discord.gg/U58ax8cZZ2\"\n"
                + "    aliases: [dc]\n"
                + "  website:\n"
                + "    message: \"&#93C5FDVisit our website: https://thelegacyvoyage.xyz/\"\n";
    }

    @SuppressWarnings("unchecked")
    private void load() {
        commands.clear();
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            Object data = yaml.load(content);
            if (!(data instanceof Map<?, ?> root)) {
                Log.warn("commands.yml is empty or invalid.");
                return;
            }

            Map<?, ?> commandSection = root.containsKey("commands")
                    ? asMap(root.get("commands"))
                    : root;

            if (commandSection == null || commandSection.isEmpty()) return;

            for (Map.Entry<?, ?> entry : commandSection.entrySet()) {
                String name = String.valueOf(entry.getKey()).trim();
                if (name.isBlank()) continue;

                String message = null;
                List<String> aliases = new ArrayList<>();

                Object value = entry.getValue();
                if (value instanceof String s) {
                    message = s;
                } else if (value instanceof Map<?, ?> map) {
                    Object msg = map.get("message");
                    if (msg == null) msg = map.get("text");
                    if (msg == null) msg = map.get("reply");
                    if (msg != null) message = String.valueOf(msg);

                    Object aliasObj = map.get("aliases");
                    if (aliasObj instanceof List<?> list) {
                        for (Object a : list) {
                            if (a != null && !String.valueOf(a).isBlank()) {
                                aliases.add(String.valueOf(a).trim());
                            }
                        }
                    } else if (aliasObj instanceof String s) {
                        for (String part : s.split(",")) {
                            if (!part.isBlank()) aliases.add(part.trim());
                        }
                    }
                }

                if (message == null || message.isBlank()) continue;
                String permission = "hyessentialsx.custom." + name.toLowerCase();
                commands.put(name.toLowerCase(), new CustomCommandDefinition(name, message, permission, aliases));
            }
        } catch (Exception e) {
            Log.warn("Failed to load commands.yml: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> asMap(Object value) {
        if (value instanceof Map<?, ?> map) return map;
        return null;
    }
}
