package br.com.finalcraft.evernifecore.region.selection;

import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.region.shape.ShapeType;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.Objects;

/**
 * What a selection collects: the shape type (and so how many points it needs), the tool handed out, whether it is
 * drawn while being made, and how long it may sit untouched. Immutable; every {@code with} returns a copy.
 *
 * <pre>{@code
 * SelectionSpec.polygon().withTool(MY_WAND).withTimeout(Duration.ofMinutes(10))
 * }</pre>
 */
public final class SelectionSpec {

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

    /** As set, or {@code Regions.selection.live-preview} from the core config. */
    public boolean isLivePreview() {
        return livePreview != null ? livePreview : ECSettings.REGIONS_SELECTION_LIVE_PREVIEW;
    }

    /** As set, or {@code Regions.selection.idle-minutes} from the core config. */
    public Duration getTimeout() {
        return timeout != null ? timeout : Duration.ofMinutes(ECSettings.REGIONS_SELECTION_IDLE_MINUTES);
    }

    @Override
    public String toString() {
        return "SelectionSpec{" + shapeType + ", " + tool + "}";
    }
}
