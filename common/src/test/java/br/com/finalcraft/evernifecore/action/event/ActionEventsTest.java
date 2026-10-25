package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.BlockContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.TypeKey;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.EventBuses;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.testing.Actors;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The catalog of action events: one pair per core source that is an action, a cancellable Pre and a plain Post. */
class ActionEventsTest {

    private static final WorldLocPos AT = WorldLocPos.of(1, 64, 1, "world");

    private static BlockContext breaking() {
        return new BlockContext(EventSource.BREAK, AT, Actors.environment("explosion"), null, TypeKey.parse("minecraft:stone"), null);
    }

    @Test
    void theCatalogCoversEveryCoreSourceButMoveTickAndNone() {
        Set<EventSource> expected = new HashSet<>();
        for (EventSource source : EventSource.all()) {
            if (source.getOwner().equals(CoreFlags.OWNER)) {
                expected.add(source);
            }
        }
        expected.removeAll(Arrays.asList(EventSource.MOVE, EventSource.TICK, EventSource.NONE));

        assertEquals(expected, new HashSet<>(ActionEvents.all()));
        assertNull(ActionEvents.of(EventSource.MOVE));
        assertNull(ActionEvents.of(EventSource.TICK));
        assertNull(ActionEvents.of(EventSource.NONE));
    }

    @Test
    void everyPreIsCancellableAndNoPostIs() {
        for (EventSource source : ActionEvents.all()) {
            ActionEvents.Pair pair = ActionEvents.of(source);
            assertNotNull(pair, source.toString());
            assertTrue(ECCancellable.class.isAssignableFrom(pair.getPre()), pair.getPre() + " must be cancellable");
            assertFalse(ECCancellable.class.isAssignableFrom(pair.getPost()), pair.getPost() + " must not be cancellable");
            assertEquals(pair.getPre().getSuperclass(), pair.getPost().getSuperclass(), "both halves share the outer class of " + source);
        }
    }

    @Test
    void subscribingToTheOuterClassHearsPreAndPost() {
        ECEventBus bus = EventBuses.mirroring();
        List<Class<?>> heard = new ArrayList<>();
        bus.subscribe(BreakActionEvent.class, event -> heard.add(event.getClass()));

        bus.post(new BreakActionEvent.Pre(breaking()));
        bus.post(new BreakActionEvent.Post(breaking()));
        bus.post(new PlaceActionEvent.Post(new BlockContext(EventSource.PLACE, AT, Actors.environment("fire"), null,
                TypeKey.parse("minecraft:fire"), null)));

        assertEquals(Arrays.<Class<?>>asList(BreakActionEvent.Pre.class, BreakActionEvent.Post.class), heard);
    }

    @Test
    void anEventRefusesTheContextOfAnotherSource() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> new PlaceActionEvent.Post(breaking()));
        assertTrue(refused.getMessage().contains("evernifecore:place") && refused.getMessage().contains("evernifecore:break"),
                refused.getMessage());
    }
}
