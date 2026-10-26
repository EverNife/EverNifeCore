package br.com.finalcraft.evernifecore.flag;

import br.com.finalcraft.evernifecore.region.Region;
import jakarta.annotation.Nullable;

import java.util.Objects;

/** One defined value the resolution saw: where it came from, where it sorted, and what the resolver did with it. */
public final class FlagLayer<T> {

    /** What the resolution did with a layer. */
    public enum Use {
        /** The resolver joined it into the value. */
        COUNTED,
        /** A {@link FlagMode#SET} layer before it replaced it. */
        CUT_BY_SET,
        /** The resolver did not need it: the first value won, or it lost among equals. */
        NOT_COUNTED,
        /** A lock before it holds another value: the first lock wins. */
        LOCK_OVERRULED
    }

    private final Flag<T> flag;
    private final FlagHolder holder;
    private final @Nullable Region region;
    private final FlagValue<T> flagValue;
    private final Resolution.Reason reason;
    private final LayerRank rank;
    private final Use use;

    /** Internal: built by the region lookup. */
    public FlagLayer(Flag<T> flag, FlagHolder holder, @Nullable Region region, FlagValue<T> flagValue, Resolution.Reason reason,
                     LayerRank rank, Use use) {
        this.flag = Objects.requireNonNull(flag, "flag");
        this.holder = Objects.requireNonNull(holder, "holder");
        this.region = region;
        this.flagValue = Objects.requireNonNull(flagValue, "flagValue");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.rank = Objects.requireNonNull(rank, "rank");
        this.use = Objects.requireNonNull(use, "use");
    }

    /** Internal: the same layer, marked with what the resolution did with it. */
    public FlagLayer<T> withUse(Use use) {
        return use == this.use ? this : new FlagLayer<>(flag, holder, region, flagValue, reason, rank, use);
    }

    /** The asked flag, or the ancestor a refinement climbed to. */
    public Flag<T> getFlag() {
        return flag;
    }

    /** Never null: the default is not a layer. */
    public FlagHolder getHolder() {
        return holder;
    }

    /** The region the layer belongs to (its own value or one of its groups'); null for a holder that follows the player. */
    public @Nullable Region getRegion() {
        return region;
    }

    public FlagValue<T> getFlagValue() {
        return flagValue;
    }

    public T getValue() {
        return flagValue.getValue();
    }

    /** {@link Resolution.Reason#GROUP_VALUE}, {@link Resolution.Reason#LOCAL_VALUE}, {@link Resolution.Reason#INHERITED} or {@link Resolution.Reason#ACTOR_VALUE}. */
    public Resolution.Reason getReason() {
        return reason;
    }

    public LayerRank getRank() {
        return rank;
    }

    public Use getUse() {
        return use;
    }

    @Override
    public String toString() {
        String envelope = flagValue.describeEnvelope();
        return "[" + use + "] " + holder.getHolderId() + " " + rank + " (" + reason + ") value " + flag.getType().format(getValue())
                + (envelope.isEmpty() ? "" : " " + envelope);
    }
}
