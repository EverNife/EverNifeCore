package br.com.finalcraft.evernifecore.hytale.eventbus;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.event.EventPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Hytale end of the mirror. Hytale dispatches by EXACT class, so an event mirrored as-is would
 * only ever reach whoever named its concrete type; the audience climbs the chain instead, and these
 * tests are about where that climb starts, where it stops and what the gate answers along the way.
 *
 * <p>The bus is a real {@link EventBus} - it needs no server to exist - so what a consumer hears here
 * is what Hytale's own registry decided, not a double's idea of it.</p>
 */
class HyHytaleAudienceTest {

    private EventBus serverBus;
    private HyHytaleAudience audience;

    @BeforeEach
    void aFreshServerBus() {
        serverBus = new EventBus(false);
        audience = new HyHytaleAudience(() -> serverBus);
    }

    @Test
    void everyLevelOfTheChainHearsTheEvent() {
        List<String> heard = new ArrayList<>();
        listen(LeafEvent.class, event -> heard.add("leaf"));
        listen(MiddleEvent.class, event -> heard.add("middle"));
        listen(BaseEvent.class, event -> heard.add("base"));

        audience.dispatch(new LeafEvent());

        assertEquals(Arrays.asList("leaf", "middle", "base"), heard,
                "the concrete class first, then up the chain - subscribing to the base still hears the family");
    }

    @Test
    void aLevelWithNothingOnItIsWalkedPast() {
        List<String> heard = new ArrayList<>();
        listen(BaseEvent.class, event -> heard.add("base"));

        LeafEvent posted = new LeafEvent();
        audience.dispatch(posted);

        assertEquals(1, heard.size(), "the two levels below the base have no consumer and no dispatch");
        assertTrue(audience.hasListeners(LeafEvent.class), "the gate climbs the same chain the dispatch does");
    }

    @Test
    void theBaseECEventIsNotALevelOfTheChain() {
        List<String> heard = new ArrayList<>();
        listen(ECEvent.class, event -> heard.add("every EC event there is"));

        LeafEvent posted = new LeafEvent();

        assertFalse(audience.hasListeners(LeafEvent.class), "ECEvent is where the family stops being a family");
        audience.dispatch(posted);
        assertTrue(heard.isEmpty(), "a consumer on the platoverride base is never mirrored into");
    }

    @Test
    void theGateIsClosedWhileNoLevelHasAConsumer() {
        //IEventDispatcher defaults hasListener() to true, so a gate reading the interface instead of
        //the dispatcher dispatchFor() hands back would answer true on an empty server
        assertFalse(audience.hasListeners(LeafEvent.class), "nothing is registered with this bus yet");
    }

    @Test
    void everyLevelGetsTheInstanceTheProducerPosted() {
        List<Object> heard = new ArrayList<>();
        listen(LeafEvent.class, heard::add);
        listen(BaseEvent.class, heard::add);

        LeafEvent posted = new LeafEvent();
        audience.dispatch(posted);

        assertEquals(2, heard.size());
        assertSame(posted, heard.get(0));
        assertSame(posted, heard.get(1), "the chain re-delivers the same event, it does not rebuild one per level");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Helpers and fixtures
    // -----------------------------------------------------------------------------------------------------------------

    /** A server-wide consumer of {@code type}, the way a Hytale plugin registers one. */
    private <T extends ECEvent> void listen(Class<T> type, Consumer<T> consumer) {
        serverBus.<Void, T>registerGlobal(EventPriority.NORMAL.getValue(), type, consumer);
    }

    static class BaseEvent extends ECEvent {
    }

    static class MiddleEvent extends BaseEvent {
    }

    static class LeafEvent extends MiddleEvent {
    }

}
