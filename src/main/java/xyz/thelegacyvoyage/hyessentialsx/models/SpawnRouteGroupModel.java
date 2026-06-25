package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public final class SpawnRouteGroupModel {

    private String id = "";
    private String permission = "";
    private int priority = 0;
    private List<String> spawns = new ArrayList<>();

    public SpawnRouteGroupModel() {}

    public SpawnRouteGroupModel(@Nonnull String id,
                                @Nonnull String permission,
                                int priority,
                                @Nonnull List<String> spawns) {
        this.id = id;
        this.permission = permission;
        this.priority = priority;
        this.spawns = new ArrayList<>(spawns);
    }

    @Nonnull
    public String getId() {
        return id == null ? "" : id;
    }

    public void setId(@Nonnull String id) {
        this.id = id;
    }

    @Nonnull
    public String getPermission() {
        return permission == null ? "" : permission;
    }

    public void setPermission(@Nonnull String permission) {
        this.permission = permission;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Nonnull
    public List<String> getSpawns() {
        return spawns == null ? List.of() : List.copyOf(spawns);
    }

    public void setSpawns(@Nonnull List<String> spawns) {
        this.spawns = new ArrayList<>(spawns);
    }
}
