package br.com.finalcraft.evernifecore.region.selection;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.locale.ILocaleMessageBase;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.region.RegionTime;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.region.shape.PolygonShape;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.region.shape.ShapeType;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * One player's selection in progress, opened by {@link SelectionTool#start}. The points are blocks of the world the
 * player stood in when it started; their real Y is kept - a plugin that wants a column of every height converts in its
 * {@code onConfirm} ({@code CuboidShape.ofUnboundedY}). Touched only on the player's world thread.
 */
public final class SelectionSession {

    @FCLocale(lang = LocaleType.EN_US, text = "§a§l ▶ §aPoint §e${number}§a set at §e${x} ${y} ${z}§a.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§a§l ▶ §aPonto §e${number}§a marcado em §e${x} ${y} ${z}§a.")
    public static LocaleMessage POINT_SET;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §ePoint §6${number}§e removed.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §ePonto §6${number}§e removido.")
    public static LocaleMessage POINT_REMOVED;

    @FCLocale(lang = LocaleType.EN_US, text = "§c§l ▶ §cThere is no point to remove.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§c§l ▶ §cNão há ponto para remover.")
    public static LocaleMessage NO_POINT_TO_REMOVE;

    @FCLocale(lang = LocaleType.EN_US, text = "§c§l ▶ §cThis selection needs §e${missing}§c more point(s) - left click to add them.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§c§l ▶ §cEsta seleção precisa de mais §e${missing}§c ponto(s) - clique esquerdo para marcar.")
    public static LocaleMessage NEEDS_MORE_POINTS;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §eSelection cancelled.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §eSeleção cancelada.")
    public static LocaleMessage SELECTION_CANCELLED;

    private final FPlayer player;
    private final SelectionSpec spec;
    private final String world;
    private final Consumer<Shape> onConfirm;
    private final List<BlockPos> points = new ArrayList<>();
    private volatile boolean active = true;
    private volatile Instant lastTouch;

    SelectionSession(FPlayer player, SelectionSpec spec, String world, Consumer<Shape> onConfirm) {
        this.player = player;
        this.spec = spec;
        this.world = world;
        this.onConfirm = onConfirm;
        this.lastTouch = RegionTime.now();
    }

    public FPlayer getPlayer() {
        return player;
    }

    public SelectionSpec getSpec() {
        return spec;
    }

    /** The world the player stood in at {@link SelectionTool#start}; every point belongs to it. */
    public String getWorld() {
        return world;
    }

    /** The points so far, in click order. */
    public List<BlockPos> getPoints() {
        return Collections.unmodifiableList(new ArrayList<>(points));
    }

    public boolean isActive() {
        return active;
    }

    /** When a point was last added or removed (or the session opened) - the idle timeout counts from here. */
    public Instant getLastTouch() {
        return lastTouch;
    }

    public void addPoint(BlockPos block) {
        points.add(block);
        touch();
        POINT_SET.addPlaceholder("number", points.size())
                .addPlaceholder("x", block.getX())
                .addPlaceholder("y", block.getY())
                .addPlaceholder("z", block.getZ())
                .send(player);
    }

    /** @return false when there was no point to remove */
    public boolean removeLastPoint() {
        if (points.isEmpty()) {
            NO_POINT_TO_REMOVE.send(player);
            return false;
        }
        points.remove(points.size() - 1);
        touch();
        POINT_REMOVED.addPlaceholder("number", points.size() + 1).send(player);
        return true;
    }

    public void clear() {
        points.clear();
        touch();
    }

    /**
     * The shape the points describe, or null while there are too few: a box between the first two points, or a
     * polygon over every point spanning the lowest to the highest Y clicked.
     */
    public @Nullable Shape toShape() {
        if (points.size() < spec.getMinPoints()) {
            return null;
        }
        if (spec.getShapeType() == ShapeType.CUBOID) {
            return CuboidShape.of(points.get(0), points.get(1));
        }
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (BlockPos point : points) {
            minY = Math.min(minY, point.getY());
            maxY = Math.max(maxY, point.getY());
        }
        return PolygonShape.of(points, minY, maxY);
    }

    /**
     * Hands the shape to the {@code onConfirm} of {@link SelectionTool#start} and closes the session; with too few
     * points it says how many are missing and the session stays open.
     *
     * @return whether the selection was confirmed
     */
    public boolean confirm() {
        Shape shape = toShape();
        if (shape == null) {
            NEEDS_MORE_POINTS.addPlaceholder("missing", spec.getMinPoints() - points.size()).send(player);
            return false;
        }
        close(null);
        onConfirm.accept(shape);
        return true;
    }

    /** Drops the selection and tells the player. */
    public void cancel() {
        close(SELECTION_CANCELLED);
    }

    /** Ends the session once; {@code reason} is what the player is told, null for nothing. */
    void close(@Nullable ILocaleMessageBase reason) {
        if (!active) {
            return;
        }
        active = false;
        SelectionTool.closed(this);
        if (reason != null) {
            reason.send(player);
        }
    }

    private void touch() {
        lastTouch = RegionTime.now();
    }
}
