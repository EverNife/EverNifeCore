package br.com.finalcraft.evernifecore.flag;

import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * How {@link RegionLookup} reached a flag's value: the value, the layers it was joined from - every defined value it saw,
 * in resolution order, each with what the resolver did with it - and the resolver that joined them.
 */
public final class Resolution<T> {

    private final Flag<T> flag;
    private final @Nullable T value;
    private final Reason reason;
    private final FlagResolver<T> resolver;
    private final List<FlagLayer<T>> layers;
    private final @Nullable FlagLayer<T> first;
    private final @Nullable Region region;

    /** Internal: built by {@link RegionLookup}. */
    public Resolution(Flag<T> flag, @Nullable T value, Reason reason, FlagResolver<T> resolver, List<FlagLayer<T>> layers) {
        this.flag = flag;
        this.value = value;
        this.reason = reason;
        this.resolver = resolver;
        this.layers = layers.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(layers);
        FlagLayer<T> firstCounted = null;
        Region firstRegion = null;
        for (FlagLayer<T> layer : layers) {
            if (layer.getUse() != FlagLayer.Use.COUNTED) {
                continue;
            }
            if (firstCounted == null) {
                firstCounted = layer;
            }
            if (firstRegion == null && layer.getRegion() != null) {
                firstRegion = layer.getRegion();
            }
        }
        this.first = firstCounted;
        this.region = firstRegion;
    }

    /** Internal: the flag's default, for a bypass or where nothing defines it. */
    public static <T> Resolution<T> ofDefault(Flag<T> flag, Reason reason) {
        return new Resolution<>(flag, flag.getDefault(), reason, flag.getResolver(), Collections.emptyList());
    }

    /**
     * The flag of the asked flag's chain whose value this is: the asked flag itself, or the ancestor an undefined
     * refinement read its value from (see {@link Flag#getParent()}).
     */
    public Flag<T> getFlag() {
        return flag;
    }

    /** What {@code resolve} answers for the same question. */
    public @Nullable T getValue() {
        return value;
    }

    /** The holder of the first counted layer; null for the default and a bypass. */
    public @Nullable FlagHolder getSource() {
        return first == null ? null : first.getHolder();
    }

    /** The region of the first counted layer that has one; null when the value came only from holders that follow the player. */
    public @Nullable Region getRegion() {
        return region;
    }

    /** The reason of the first counted layer; {@link Reason#BYPASS} or {@link Reason#DEFAULT} for the flag's default. */
    public Reason getReason() {
        return first == null ? reason : first.getReason();
    }

    /** The strategy that joined the layers: the one the settings in effect name, or the flag's own. */
    public FlagResolver<T> getResolver() {
        return resolver;
    }

    /** Every defined layer in resolution order, each with its {@link FlagLayer#getUse()}; empty for the default. */
    public List<FlagLayer<T>> getLayers() {
        return layers;
    }

    public List<FlagLayer<T>> getCountedLayers() {
        List<FlagLayer<T>> counted = new ArrayList<>(layers.size());
        for (FlagLayer<T> layer : layers) {
            if (layer.getUse() == FlagLayer.Use.COUNTED) {
                counted.add(layer);
            }
        }
        return counted;
    }

    @Override
    public String toString() {
        return value + " of " + flag.getKey() + " (" + getReason() + ", " + resolver.getId() + ") " + layers;
    }

    public enum Reason {
        /** The subject holds a bypass permission: the flag reads as its default. */
        BYPASS,
        /** A group of a region that contains the subject defines the flag. */
        GROUP_VALUE,
        /** The region defines the flag itself. */
        LOCAL_VALUE,
        /** The region does not define it; an ancestor does. */
        INHERITED,
        /** A holder that follows the subject wherever they stand ({@link ActorHolders}) defines it. */
        ACTOR_VALUE,
        /** Nothing defines it: the flag's default. */
        DEFAULT
    }
}
