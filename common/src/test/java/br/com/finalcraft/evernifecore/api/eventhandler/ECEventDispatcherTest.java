package br.com.finalcraft.evernifecore.api.eventhandler;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ECEventDispatcherTest {

    static class SampleEvent implements IECEvent {
    }

    static class OtherEvent implements IECEvent {
    }

    static class RecordingListener {
        final List<IECEvent> received = new ArrayList<>();

        @ECEventHandler
        public void onSample(SampleEvent event) {
            received.add(event);
        }
    }

    static class OrderedListener {
        final List<String> order = new ArrayList<>();

        @ECEventHandler(priority = ECEventPriority.LAST)
        public void late(SampleEvent event) {
            order.add("late");
        }

        @ECEventHandler(priority = ECEventPriority.FIRST)
        public void early(SampleEvent event) {
            order.add("early");
        }
    }

    @Test
    void deliversEventToRegisteredHandler() {
        ECEventDispatcher dispatcher = new ECEventDispatcher();
        RecordingListener listener = new RecordingListener();
        dispatcher.register(listener);

        SampleEvent event = new SampleEvent();
        dispatcher.post(event);

        assertEquals(1, listener.received.size());
        assertTrue(listener.received.get(0) == event);
    }

    @Test
    void doesNotDeliverUnrelatedEventType() {
        ECEventDispatcher dispatcher = new ECEventDispatcher();
        RecordingListener listener = new RecordingListener();
        dispatcher.register(listener);

        dispatcher.post(new OtherEvent());

        assertTrue(listener.received.isEmpty());
    }

    @Test
    void invokesHandlersInPriorityOrder() {
        ECEventDispatcher dispatcher = new ECEventDispatcher();
        OrderedListener listener = new OrderedListener();
        dispatcher.register(listener);

        dispatcher.post(new SampleEvent());

        assertEquals(List.of("early", "late"), listener.order);
    }

    @Test
    void unregisterStopsDelivery() {
        ECEventDispatcher dispatcher = new ECEventDispatcher();
        RecordingListener listener = new RecordingListener();
        dispatcher.register(listener);
        dispatcher.unregister(listener);

        dispatcher.post(new SampleEvent());

        assertTrue(listener.received.isEmpty());
    }
}
