package br.com.finalcraft.evernifecore.region.selection;

import br.com.finalcraft.evernifecore.config.plugin.RegionSettings;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.region.shape.ShapeType;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.Objects;

/**
 * What a selection collects: the shape type (and so how many points it needs), the tool handed out, whether it is
 * drawn while being made, and how long it may sit untouched. Immutable; every {@code with} returns a copy. What is not
 * set comes from the settings of the {@code RegionManager} whose node starts the selection ({@link #withDefaults}), else
 * from the built-in {@link RegionSettings.Selection}.
 *
 * <pre>{@code
 * SelectionSpec.polygon().withTool(MY_WAND).withTimeout(Duration.ofMinutes(10))
 * }</pre>
 */
public final class SelectionSpec {

    private static final RegionSettings.Selection BUILT_IN = new RegionSettings.Selection();

    private final ShapeType shapeType;
    private final ItemSpec tool;
    private final @Nullable Boolean livePreview;
    private final @Nullable Duration timeout;

    private SelectionSpec(ShapeType shapeType, ItemSpec tool, @Nullable Boolean livePreview, @Nullable Duration timeout) {
        this.shapeType = shapeType;
        this.tool = tool;
        this.livePreview = livePreview;
        this.timeout = timeout;
    }

    /** Two corners of a box. */
    public static SelectionSpec cuboid() {
        return new SelectionSpec(ShapeType.CUBOID, ItemSpec.DEFAULT_TOOL, null, null);
    }

    /** Three or more outline points. */
    public static SelectionSpec polygon() {
        return new SelectionSpec(ShapeType.POLYGON, ItemSpec.DEFAULT_TOOL, null, null);
    }

    public SelectionSpec withTool(ItemSpec tool) {
        return new SelectionSpec(shapeType, Objects.requireNonNull(tool, "tool"), livePreview, timeout);
    }

    public SelectionSpec withLivePreview(boolean livePreview) {
        return new SelectionSpec(shapeType, tool, livePreview, timeout);
    }

    public SelectionSpec withTimeout(Duration timeout) {
        return new SelectionSpec(shapeType, tool, livePreview, Objects.requireNonNull(timeout, "timeout"));
    }

    /** This spec collecting another shape type, everything else as set. */
    public SelectionSpec withShapeType(ShapeType shapeType) {
        return new SelectionSpec(Objects.requireNonNull(shapeType, "shapeType"), tool, livePreview, timeout);
    }

    /** This spec with what it does not set taken from {@code defaults}; what it sets stays. */
    public SelectionSpec withDefaults(RegionSettings.Selection defaults) {
        return new SelectionSpec(shapeType, tool, livePreview != null ? livePreview : defaults.isLivePreview(),
                timeout != null ? timeout : defaults.getIdleTime());
    }

    public ShapeType getShapeType() {
        return shapeType;
    }

    /** 2 for a cuboid, 3 for a polygon. */
    public int getMinPoints() {
        return shapeType == ShapeType.CUBOID ? 2 : 3;
    }

    public ItemSpec getTool() {
        return tool;
    }

    /** As set, or the built-in {@code selection.live-preview} of {@link RegionSettings}. */
    public boolean isLivePreview() {
        return livePreview != null ? livePreview : BUILT_IN.isLivePreview();
    }

    /** As set, or the built-in {@code selection.idle-time} of {@link RegionSettings}. */
    public Duration getTimeout() {
        return timeout != null ? timeout : BUILT_IN.getIdleTime();
    }

    @Override
    public String toString() {
        return "SelectionSpec{" + shapeType + ", " + tool + "}";
    }
}
