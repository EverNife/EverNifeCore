package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.EventSource;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The catalog that ties each core {@link EventSource} to its {@link ActionEvent} pair. Every core source that is an
 * action has one; {@link EventSource#MOVE} (region enter/leave events speak for it), {@link EventSource#TICK} and
 * {@link EventSource#NONE} have none, and neither has a plugin's own source.
 */
public final class ActionEvents {

    private static final Map<EventSource, Pair> CATALOG = new LinkedHashMap<>();

    static {
        add(EventSource.BREAK, BreakActionEvent.Pre.class, BreakActionEvent.Post.class);
        add(EventSource.PLACE, PlaceActionEvent.Pre.class, PlaceActionEvent.Post.class);
        add(EventSource.INTERACT, InteractActionEvent.Pre.class, InteractActionEvent.Post.class);
        add(EventSource.DAMAGE, DamageActionEvent.Pre.class, DamageActionEvent.Post.class);
        add(EventSource.SPAWN, SpawnActionEvent.Pre.class, SpawnActionEvent.Post.class);
        add(EventSource.EXPLOSION, ExplosionActionEvent.Pre.class, ExplosionActionEvent.Post.class);
        add(EventSource.FLUID, FluidActionEvent.Pre.class, FluidActionEvent.Post.class);
        add(EventSource.RESPAWN, RespawnActionEvent.Pre.class, RespawnActionEvent.Post.class);
        add(EventSource.ITEM_PICKUP, PickupActionEvent.Pre.class, PickupActionEvent.Post.class);
        add(EventSource.ITEM_DROP, DropActionEvent.Pre.class, DropActionEvent.Post.class);
        add(EventSource.VEHICLE, VehicleActionEvent.Pre.class, VehicleActionEvent.Post.class);
        add(EventSource.FIRE_SPREAD, FireSpreadActionEvent.Pre.class, FireSpreadActionEvent.Post.class);
        add(EventSource.FLIGHT_TOGGLE, FlightToggleActionEvent.Pre.class, FlightToggleActionEvent.Post.class);
    }

    private ActionEvents() {
    }

    private static void add(EventSource source, Class<? extends ActionEvent<?>> pre, Class<? extends ActionEvent<?>> post) {
        CATALOG.put(source, new Pair(pre, post));
    }

    /** The pair of {@code source}; null for a source with no action event. */
    public static @Nullable Pair of(EventSource source) {
        return CATALOG.get(source);
    }

    /** Every source with an action event, in catalog order. */
    public static Set<EventSource> all() {
        return Collections.unmodifiableSet(CATALOG.keySet());
    }

    /** The two halves of one source's event. */
    public static final class Pair {

        private final Class<? extends ActionEvent<?>> pre;
        private final Class<? extends ActionEvent<?>> post;

        private Pair(Class<? extends ActionEvent<?>> pre, Class<? extends ActionEvent<?>> post) {
            this.pre = pre;
            this.post = post;
        }

        /** The cancellable half, posted before the outcome where the platform has such a point. */
        public Class<? extends ActionEvent<?>> getPre() {
            return pre;
        }

        /** The half posted after the outcome. */
        public Class<? extends ActionEvent<?>> getPost() {
            return post;
        }
    }
}
