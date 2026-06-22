package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CustomCommandDefinition {

    private final String name;
    private final String message;
    private final String permission;
    private final List<String> aliases;

    public CustomCommandDefinition(@Nonnull String name,
                                   @Nonnull String message,
                                   @Nonnull String permission,
                                   @Nonnull List<String> aliases) {
        this.name = name;
        this.message = message;
        this.permission = permission;
        this.aliases = new ArrayList<>(aliases);
    }

    @Nonnull
    public String getName() {
        return name;
    }

    @Nonnull
    public String getMessage() {
        return message;
    }

    @Nonnull
    public String getPermission() {
        return permission;
    }

    @Nonnull
    public List<String> getAliases() {
        return Collections.unmodifiableList(aliases);
    }
}
