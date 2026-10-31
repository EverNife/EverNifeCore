package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.screen.ClickGesture;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Which gesture on an offer button trades what: a gesture bound to BUY only answers on a button that has a BUY offer,
 * and so on. Immutable - {@link #on} returns a new map.
 */
public final class ClickMap {

    /** What one gesture does. */
    public static final class Binding {
        private final Offer.Type type;
        private final Quantity quantity;

        private Binding(Offer.Type type, Quantity quantity) {
            this.type = type;
            this.quantity = quantity;
        }

        public Offer.Type getType() {
            return type;
        }

        public Quantity getQuantity() {
            return quantity;
        }

        @Override
        public String toString() {
            return type + " " + quantity;
        }
    }

    private static final ClickMap MARKET = new ClickMap(new EnumMap<ClickGesture, Binding>(ClickGesture.class))
            .on(ClickGesture.PRIMARY, Offer.Type.BUY, Quantity.ONE)
            .on(ClickGesture.SHIFT_PRIMARY, Offer.Type.BUY, Quantity.STACK)
            .on(ClickGesture.SECONDARY, Offer.Type.SELL, Quantity.ONE)
            .on(ClickGesture.SHIFT_SECONDARY, Offer.Type.SELL, Quantity.ALL_HELD)
            .on(ClickGesture.MIDDLE, Offer.Type.BUY, Quantity.ASK);

    private final Map<ClickGesture, Binding> bindings;

    private ClickMap(Map<ClickGesture, Binding> bindings) {
        this.bindings = bindings;
    }

    /** Left buys one, shift-left a stack, right sells one, shift-right everything held, middle asks how many to buy. */
    public static ClickMap market() {
        return MARKET;
    }

    /** No gesture trades; add them with {@link #on}. */
    public static ClickMap none() {
        return new ClickMap(new EnumMap<ClickGesture, Binding>(ClickGesture.class));
    }

    /** This map with {@code gesture} trading {@code quantity} of the button's {@code type} offer. */
    public ClickMap on(ClickGesture gesture, Offer.Type type, Quantity quantity) {
        Map<ClickGesture, Binding> copy = new EnumMap<>(ClickGesture.class);
        copy.putAll(bindings);
        copy.put(Objects.requireNonNull(gesture, "gesture"), new Binding(Objects.requireNonNull(type, "type"), Objects.requireNonNull(quantity, "quantity")));
        return new ClickMap(copy);
    }

    public Map<ClickGesture, Binding> getBindings() {
        return Collections.unmodifiableMap(bindings);
    }

    @Override
    public String toString() {
        return "ClickMap" + bindings;
    }
}
