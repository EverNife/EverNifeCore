package br.com.finalcraft.evernifecore.region.overlap;

import jakarta.annotation.Nullable;

import java.util.Objects;

/** Something a new shape would share blocks with: one of our regions, or one an {@link OverlapProvider} reports. */
public final class Overlap {

    private final OverlapSource source;
    private final String id;
    private final @Nullable String owner;
    private final int priority;
    private final boolean above;

    /**
     * @param id    the overlapped thing's id as its source names it ({@code owner:name} for ours)
     * @param owner who owns it, when the source knows
     * @param above whether it outranks the shape being checked
     */
    public Overlap(OverlapSource source, String id, @Nullable String owner, int priority, boolean above) {
        this.source = Objects.requireNonNull(source, "source");
        this.id = Objects.requireNonNull(id, "id");
        this.owner = owner;
        this.priority = priority;
        this.above = above;
    }

    public OverlapSource getSource() {
        return source;
    }

    public String getId() {
        return id;
    }

    public @Nullable String getOwner() {
        return owner;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isAbove() {
        return above;
    }

    @Override
    public String toString() {
        return source + " " + id + " (priority " + priority + (above ? ", above" : "") + ")";
    }
}
