package br.com.finalcraft.evernifecore.flag;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Joins the layers of a flag that count into one value. {@link FlagResolvers} has the stock strategies; a flag picks its
 * own ({@link Flag#getResolver()}) and lists the ones its settings may name instead ({@link Flag#getAcceptedResolvers()}).
 */
public interface FlagResolver<T> {

    /** The id the settings name: {@code first-defined}, {@code prefer-among-equals}, {@code sum-by-interval}, ... */
    String getId();

    /**
     * Joins {@code layers} - most specific first, never empty, already cut at the first {@link FlagMode#SET}.
     * Any thread, no I/O, no side effect: it is asked on every event.
     */
    Resolved<T> resolve(List<FlagLayer<T>> layers);

    /** The value, and the layers it came from; the others read {@link FlagLayer.Use#NOT_COUNTED} in the explanation. */
    final class Resolved<T> {

        private final T value;
        private final List<FlagLayer<T>> counted;

        private Resolved(T value, List<FlagLayer<T>> counted) {
            this.value = Objects.requireNonNull(value, "A resolver answers a value: the layers it gets are never empty.");
            this.counted = counted;
        }

        /** The first layer's value. */
        public static <T> Resolved<T> first(List<FlagLayer<T>> layers) {
            return new Resolved<>(layers.get(0).getValue(), Collections.singletonList(layers.get(0)));
        }

        /** {@code value}, joined from every layer. */
        public static <T> Resolved<T> all(T value, List<FlagLayer<T>> layers) {
            return new Resolved<>(value, layers);
        }

        /** {@code value}, from exactly the {@code counted} layers. */
        public static <T> Resolved<T> of(T value, List<FlagLayer<T>> counted) {
            return new Resolved<>(value, counted);
        }

        public T getValue() {
            return value;
        }

        public List<FlagLayer<T>> getCounted() {
            return counted;
        }
    }
}
