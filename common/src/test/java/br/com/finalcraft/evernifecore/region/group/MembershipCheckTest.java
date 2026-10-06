package br.com.finalcraft.evernifecore.region.group;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The text form the storage keeps, and the refusals. */
class MembershipCheckTest {

    @Test
    void textFormRoundTripsForTheThreeKinds() {
        for (MembershipCheck check : new MembershipCheck[]{MembershipCheck.exact(), MembershipCheck.onBoundary(),
                MembershipCheck.interval(Duration.ofSeconds(30))}) {
            assertEquals(check, MembershipCheck.parse(check.toText()), check.toText());
        }
        assertEquals("exact", MembershipCheck.exact().toText());
        assertEquals("on-boundary", MembershipCheck.onBoundary().toText());
        assertEquals("interval:PT30S", MembershipCheck.interval(Duration.ofSeconds(30)).toText());
        assertEquals(Duration.ofSeconds(30), MembershipCheck.parse("interval:PT30S").getInterval());
        assertNull(MembershipCheck.exact().getInterval());
    }

    @Test
    void intervalRefusesZeroAndNegativeAndTeachesTheWayOut() {
        IllegalArgumentException zero = assertThrows(IllegalArgumentException.class, () -> MembershipCheck.interval(Duration.ZERO));
        assertTrue(zero.getMessage().contains("MembershipCheck.exact()"), zero.getMessage());
        assertThrows(IllegalArgumentException.class, () -> MembershipCheck.interval(Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> MembershipCheck.parse("interval:PT0S"));
    }

    @Test
    void unknownTextNamesTheAcceptedForms() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> MembershipCheck.parse("sometimes"));
        assertTrue(refused.getMessage().contains("'sometimes'") && refused.getMessage().contains("interval:PT30S"), refused.getMessage());
        assertThrows(IllegalArgumentException.class, () -> MembershipCheck.parse("interval:soon"));
    }
}
