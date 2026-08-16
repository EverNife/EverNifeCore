package br.com.finalcraft.evernifecore.eventbus;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How the plan of a concrete event class is assembled from the subscriptions of its ancestors: each
 * subscriber once however many paths lead to it, priority first, and a tie in the order the
 * subscriptions were taken - whatever class each one named.
 */
class ECEventBusDispatchPlanTest {

    @Test
    void aSubscriberOnAnInterfaceReachedThroughTwoPathsHearsTheEventOnce() {
        ECEventBus bus = ECEventBus.create();
        List<String> received = new ArrayList<>();
        bus.subscribe(Marked.class, event -> received.add("marked"));

        bus.post(new DoublyMarkedEvent());

        assertEquals(Arrays.asList("marked"), received, "the base and the leaf both carry Marked; the delivery is still one");
        assertEquals(1, bus.getSubscriptions(DoublyMarkedEvent.class).size());
    }

    @Test
    void aTieBetweenABaseAndASubtypeSubscriberKeepsTheOrderTheySubscribedIn() {
        ECEventBus subFirst = ECEventBus.create();
        List<String> order = new ArrayList<>();
        subFirst.subscribe(SubSampleEvent.class, event -> order.add("sub"));
        subFirst.subscribe(SampleEvent.class, event -> order.add("base"));
        subFirst.post(new SubSampleEvent());
        assertEquals(Arrays.asList("sub", "base"), order);

        ECEventBus baseFirst = ECEventBus.create();
        order.clear();
        baseFirst.subscribe(SampleEvent.class, event -> order.add("base"));
        baseFirst.subscribe(SubSampleEvent.class, event -> order.add("sub"));
        baseFirst.post(new SubSampleEvent());
        assertEquals(Arrays.asList("base", "sub"), order, "same priority: whoever subscribed first hears first, the class named does not matter");
    }

    @Test
    void priorityStillWinsOverSubscriptionOrderAcrossTheClassesNamed() {
        ECEventBus bus = ECEventBus.create();
        List<String> order = new ArrayList<>();
        bus.subscribe(SubSampleEvent.class, ECSubscribeOptions.defaults().withPriority(ECEventPriority.LAST), event -> order.add("sub-last"));
        bus.subscribe(Marked.class, ECSubscribeOptions.defaults().withPriority(ECEventPriority.FIRST), event -> order.add("marked-first"));
        bus.subscribe(SampleEvent.class, event -> order.add("base-normal"));

        bus.post(new SubSampleEvent());

        assertEquals(Arrays.asList("marked-first", "base-normal", "sub-last"), order);
    }

    @Test
    void aSubscriptionOnIECEventItselfHearsEveryEvent() {
        ECEventBus bus = ECEventBus.create();
        List<String> received = new ArrayList<>();
        bus.subscribe(IECEvent.class, event -> received.add(event.getClass().getSimpleName()));

        bus.post(new SampleEvent());
        bus.post(new DoublyMarkedEvent());

        assertEquals(Arrays.asList("SampleEvent", "DoublyMarkedEvent"), received, "IECEvent is an ancestor of every event, through a class or through an interface");
    }

    interface Marked extends IECEvent {
    }

    static class SampleEvent implements Marked {
    }

    static class SubSampleEvent extends SampleEvent {
    }

    /** Carries Marked twice: through its base and by its own declaration. */
    static class DoublyMarkedEvent extends SampleEvent implements Marked {
    }

}
