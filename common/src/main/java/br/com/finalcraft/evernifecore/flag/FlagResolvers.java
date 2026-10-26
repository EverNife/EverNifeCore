package br.com.finalcraft.evernifecore.flag;

import br.com.finalcraft.evernifecore.player.state.EffectLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;

/** The stock {@link FlagResolver} strategies. Every one is stateless: any thread, no I/O. */
public final class FlagResolvers {

    private FlagResolvers() {
    }

    /** The first layer: the most specific value wins outright. */
    public static <T> FlagResolver<T> firstDefined() {
        return of("first-defined", FlagResolver.Resolved::first);
    }

    /** Among the leading layers of EQUAL rank, {@code preferred} wins when present; else the first. */
    public static <T> FlagResolver<T> preferAmongEquals(T preferred) {
        Objects.requireNonNull(preferred, "preferred");
        return preferAmongEquals(preferred::equals);
    }

    /** As {@link #preferAmongEquals(Object)}, preferring the first value of the leading equals that {@code preferred} accepts. */
    public static <T> FlagResolver<T> preferAmongEquals(Predicate<? super T> preferred) {
        return of("prefer-among-equals", layers -> {
            LayerRank leading = layers.get(0).getRank();
            for (FlagLayer<T> layer : layers) {
                if (!layer.getRank().equals(leading)) {
                    break;
                }
                if (preferred.test(layer.getValue())) {
                    return FlagResolver.Resolved.of(layer.getValue(), Collections.singletonList(layer));
                }
            }
            return FlagResolver.Resolved.first(layers);
        });
    }

    /** Entries of the same interval add up into one; distinct intervals stay one entry each, in the order they first appear. */
    public static FlagResolver<List<AmountEvery>> sumByInterval() {
        return mergeBy("sum-by-interval", AmountEvery::getInterval,
                (a, b) -> AmountEvery.of(a.getAmount() + b.getAmount(), a.getInterval()));
    }

    /** One entry per effect, the highest level among the layers. */
    public static FlagResolver<List<EffectLevel>> unionMaxLevel() {
        return mergeBy("union-max-level", EffectLevel::getEffect, (a, b) -> b.getLevel() > a.getLevel() ? b : a);
    }

    /** Every layer's list, in layer order, duplicates kept. */
    public static <E> FlagResolver<List<E>> concat() {
        return of("concat", layers -> {
            List<E> joined = new ArrayList<>();
            for (FlagLayer<List<E>> layer : layers) {
                joined.addAll(layer.getValue());
            }
            return FlagResolver.Resolved.all(Collections.unmodifiableList(joined), layers);
        });
    }

    /** The product of every layer, kept within {@code [floor, cap]}. */
    public static FlagResolver<Double> productCapped(double floor, double cap) {
        if (floor > cap) {
            throw new IllegalArgumentException("The floor " + floor + " is above the cap " + cap + ": pass the smaller bound first.");
        }
        return of("product-capped", layers -> {
            double product = 1.0;
            for (FlagLayer<Double> layer : layers) {
                product *= layer.getValue();
            }
            return FlagResolver.Resolved.all(Math.max(floor, Math.min(cap, product)), layers);
        });
    }

    public static <N extends Comparable<N>> FlagResolver<N> max() {
        return extreme("max", 1);
    }

    public static <N extends Comparable<N>> FlagResolver<N> min() {
        return extreme("min", -1);
    }

    private static <N extends Comparable<N>> FlagResolver<N> extreme(String id, int sign) {
        return of(id, layers -> {
            FlagLayer<N> best = layers.get(0);
            for (FlagLayer<N> layer : layers) {
                if (Integer.signum(layer.getValue().compareTo(best.getValue())) == sign) {
                    best = layer;
                }
            }
            return FlagResolver.Resolved.of(best.getValue(), Collections.singletonList(best));
        });
    }

    private static <E, K> FlagResolver<List<E>> mergeBy(String id, Function<E, K> key, BinaryOperator<E> merge) {
        return of(id, layers -> {
            Map<K, E> merged = new LinkedHashMap<>();
            for (FlagLayer<List<E>> layer : layers) {
                for (E entry : layer.getValue()) {
                    merged.merge(key.apply(entry), entry, merge);
                }
            }
            return FlagResolver.Resolved.all(Collections.unmodifiableList(new ArrayList<>(merged.values())), layers);
        });
    }

    private static <T> FlagResolver<T> of(String id, Function<List<FlagLayer<T>>, FlagResolver.Resolved<T>> body) {
        return new FlagResolver<T>() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public Resolved<T> resolve(List<FlagLayer<T>> layers) {
                return body.apply(layers);
            }

            @Override
            public String toString() {
                return id;
            }
        };
    }
}
