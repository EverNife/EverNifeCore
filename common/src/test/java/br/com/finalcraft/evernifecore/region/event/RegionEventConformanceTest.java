package br.com.finalcraft.evernifecore.region.event;

import br.com.finalcraft.evernifecore.testing.ECEventConformance;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The region events declare nothing that would break them on a platform's native bus. */
class RegionEventConformanceTest {

    private static final List<Class<?>> EVENTS = Arrays.asList(
            RegionEnterEvent.class, RegionLeaveEvent.class, RegionCreatedEvent.class, RegionRemovedEvent.class,
            RegionRedefinedEvent.class, RegionFlagChangedEvent.class, RegionGroupChangedEvent.class,
            RegionChangedEvent.class, RegionLoadFailedEvent.class);

    @Test
    void everyRegionEventConforms() {
        assertTrue(EVENTS.size() >= 9, "the list must name every region event, it names " + EVENTS.size());
        assertEquals(Arrays.asList(), ECEventConformance.checkAll(EVENTS.toArray(new Class<?>[0])));
    }
}
