package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerTracker;
import xyz.thelegacyvoyage.hyessentialsx.managers.VanishManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.function.Predicate;

public final class MapVisibilityUtil {

    private static volatile Field markerTrackerField;

    private MapVisibilityUtil() {}

    public static void refreshAll(@Nonnull VanishManager vanishManager) {
        for (PlayerRef viewer : Universe.get().getPlayers()) {
            if (viewer == null) continue;
            applyForViewer(viewer, vanishManager);
        }
    }

    public static void applyForViewer(@Nonnull PlayerRef viewer,
                                      @Nonnull VanishManager vanishManager) {
        World world = resolveWorld(viewer);
        Runnable task = () -> applyForViewerSync(viewer, vanishManager);
        if (world != null && !world.isInThread()) {
            world.execute(task);
        } else {
            task.run();
        }
    }

    private static void applyForViewerSync(@Nonnull PlayerRef viewer,
                                           @Nonnull VanishManager vanishManager) {
        Player player = viewer.getComponent(Player.getComponentType());
        if (player == null) {
            return;
        }
        WorldMapTracker tracker = player.getWorldMapTracker();
        if (tracker == null) {
            return;
        }
        MapMarkerTracker markerTracker = resolveMarkerTracker(tracker);
        Predicate<PlayerRef> baseFilter = null;
        if (markerTracker != null) {
            baseFilter = markerTracker.getPlayerMapFilter();
        }
        if (baseFilter instanceof VanishMapFilter wrapped) {
            baseFilter = wrapped.getBaseFilter();
        }
        tracker.setPlayerMapFilter(new VanishMapFilter(vanishManager, baseFilter));
    }

    @Nullable
    private static MapMarkerTracker resolveMarkerTracker(@Nonnull WorldMapTracker tracker) {
        try {
            Field field = markerTrackerField;
            if (field == null) {
                field = WorldMapTracker.class.getDeclaredField("markerTracker");
                field.setAccessible(true);
                markerTrackerField = field;
            }
            Object value = field.get(tracker);
            if (value instanceof MapMarkerTracker markerTracker) {
                return markerTracker;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    private static World resolveWorld(@Nonnull PlayerRef playerRef) {
        try {
            UUID worldId = playerRef.getWorldUuid();
            if (worldId == null) return null;
            return Universe.get().getWorld(worldId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class VanishMapFilter implements Predicate<PlayerRef> {
        private final VanishManager vanishManager;
        @Nullable
        private final Predicate<PlayerRef> baseFilter;

        private VanishMapFilter(@Nonnull VanishManager vanishManager,
                                @Nullable Predicate<PlayerRef> baseFilter) {
            this.vanishManager = vanishManager;
            this.baseFilter = baseFilter;
        }

        @Override
        public boolean test(PlayerRef target) {
            if (target == null) return true;
            if (vanishManager.isEnabled(target.getUuid())) {
                return true;
            }
            return baseFilter != null && baseFilter.test(target);
        }

        @Nullable
        private Predicate<PlayerRef> getBaseFilter() {
            return baseFilter;
        }
    }
}
