package br.com.finalcraft.evernifecore.region.visual;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.RegionAgenda;
import br.com.finalcraft.evernifecore.region.RegionTime;
import br.com.finalcraft.evernifecore.region.selection.SelectionSession;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Draws outlines for one player at a time, every 10 ticks, through the platform's {@link IOutlineRenderer}. Off unless
 * {@code Regions.visualizer.enabled} is true - while off every call is a no-op. Each frame is bounded by
 * {@code Regions.visualizer.step}, {@code radius} and {@code points-per-tick}. A player sees one view at a time: a new
 * one replaces the old. Call it from the player's world thread; frames are drawn there too.
 */
public final class RegionVisualizer {

    static final long FRAME_MILLIS = 500;   //10 ticks

    private static final RegionVisualizer INSTANCE = new RegionVisualizer();

    private final Map<UUID, View> views = new ConcurrentHashMap<>();

    private RegionVisualizer() {
    }

    public static RegionVisualizer get() {
        return INSTANCE;
    }

    public boolean isEnabled() {
        return ECSettings.REGIONS_VISUALIZER_ENABLED;
    }

    /** Draws {@code shape} for {@code player} until {@code duration} passes or {@link #hide} is called. */
    public void show(FPlayer player, String world, Shape shape, VisualStyle style, Duration duration) {
        if (!isEnabled()) {
            return;
        }
        Instant until = RegionTime.now().plus(duration);
        start(new View(player, world, style, () -> RegionTime.now().isBefore(until), () -> shape, null));
    }

    /** Draws the selection while it is open: its shape once it has one, its loose points before that. */
    public void showLive(SelectionSession session) {
        if (!isEnabled()) {
            return;
        }
        start(new View(session.getPlayer(), session.getWorld(), VisualStyle.SELECTION, session::isActive, session::toShape, session));
    }

    /** Stops whatever {@code player} is being shown. */
    public void hide(FPlayer player) {
        views.remove(player.getUniqueId());
    }

    /** Whether {@code player} is being shown something now. */
    public boolean isShowing(FPlayer player) {
        return views.containsKey(player.getUniqueId());
    }

    private void start(View view) {
        views.put(view.player.getUniqueId(), view);
        RegionAgenda.scheduleAsync(() -> frame(view), 0);
    }

    private void frame(View view) {
        if (views.get(view.player.getUniqueId()) != view) {
            return;   //hidden, or replaced by a newer view
        }
        if (!view.alive.get() || !view.player.isOnline()) {
            views.remove(view.player.getUniqueId(), view);
            return;
        }
        IPlatform platform = EverNifeCore.getPlatform();
        platform.runOnWorldThread(view.world, () -> draw(platform, view));
        RegionAgenda.scheduleAsync(() -> frame(view), FRAME_MILLIS);
    }

    private static void draw(IPlatform platform, View view) {
        FLocation location = view.player.getLocation();
        if (location == null) {
            return;
        }
        WorldLocPos viewer = location.getWorldLocPos();
        Shape shape = view.shape.get();
        List<WorldLocPos> points;
        if (shape != null) {
            points = OutlineSampler.sample(shape, view.world, platform.getWorldHeight(view.world), viewer,
                    ECSettings.REGIONS_VISUALIZER_STEP, ECSettings.REGIONS_VISUALIZER_RADIUS, ECSettings.REGIONS_VISUALIZER_POINTS_PER_TICK);
        } else {
            points = new ArrayList<>();
            for (BlockPos point : view.selection.getPoints()) {
                points.add(WorldLocPos.of(point.getX() + 0.5, point.getY() + 0.5, point.getZ() + 0.5, view.world));
            }
        }
        platform.getOutlineRenderer().render(view.player, points, view.style);
    }

    private static final class View {
        final FPlayer player;
        final String world;
        final VisualStyle style;
        final Supplier<Boolean> alive;
        final Supplier<Shape> shape;
        final @Nullable SelectionSession selection;

        View(FPlayer player, String world, VisualStyle style, Supplier<Boolean> alive, Supplier<Shape> shape,
             @Nullable SelectionSession selection) {
            this.player = player;
            this.world = world;
            this.style = style;
            this.alive = alive;
            this.shape = shape;
            this.selection = selection;
        }
    }
}
