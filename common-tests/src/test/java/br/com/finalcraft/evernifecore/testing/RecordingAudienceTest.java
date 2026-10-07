package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.EventBuses;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The gate of a recording audience keeps a record of every question, not only a count. */
class RecordingAudienceTest {

    @Test
    void aPostLeavesOneGateCheckNamingTheTypeAndTheThread() {
        ECEventBus bus = EventBuses.mirroring();
        RecordingAudience audience = new RecordingAudience();
        bus.addNativeAudience(audience);

        bus.post(new ProbeEvent());

        List<RecordingAudience.GateCheck> log = audience.getGateCheckLog();
        assertEquals(1, log.size(), log::toString);
        assertEquals(1, audience.getGateChecks(), "the count is the size of the log");
        assertSame(ProbeEvent.class, log.get(0).eventType);
        assertEquals(Thread.currentThread().getName(), log.get(0).thread);
        assertTrue(log.get(0).toString().contains("ECEventBus.post"), "the frames say it was a post: " + log);
    }

    @Test
    void resetForgetsTheLog() {
        ECEventBus bus = EventBuses.mirroring();
        RecordingAudience audience = new RecordingAudience();
        bus.addNativeAudience(audience);
        bus.post(new ProbeEvent());

        audience.reset();

        assertEquals(0, audience.getGateChecks());
        assertTrue(audience.getGateCheckLog().isEmpty());
    }

    static class ProbeEvent extends ECEvent implements IECEvent {
    }
}
