package br.com.finalcraft.evernifecore.testing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The suite every platform has to pass. The interesting case is the last one: it is the shape of
 * the defect that shipped for months because the classpath probe and the registered platform were
 * never asked to agree.
 */
class PlatformConformanceTest {

    @Test
    void aLenientDoubleConforms() {
        assertEquals(0, PlatformConformance.check(Platforms.lenient().build()).size(),
                PlatformConformance.summarize(Platforms.lenient().build()));
    }

    @Test
    void aStrictDoubleReportsEveryQuestionItRefuses() {
        List<String> failures = PlatformConformance.check(Platforms.strict().build());

        assertTrue(failures.size() >= 3, "a platform that answers nothing cannot conform: " + failures);
        assertTrue(failures.get(0).contains("getPlatformProviderId"), failures.get(0));
    }

    @Test
    void claimingAPlatformTheClasspathContradictsIsAViolation() {
        //the test JVM has no Bukkit on the classpath, so a double claiming to be one is lying.
        //Spelled out rather than taken from PlatformId on purpose: the checker reads that constant, so
        //feeding it back would pass whatever the constant said. This literal is what pins the wire
        //value - the one that must never change, because account rows carry it.
        List<String> failures = PlatformConformance.check(
                Platforms.lenient().platformProviderId("minecraft").build());

        assertEquals(1, failures.size(), failures.toString());
        assertTrue(failures.get(0).contains("classpath probe"), failures.get(0));
    }

    @Test
    void aBlankProviderIdIsAViolationBecauseItIsPersisted() {
        List<String> failures = PlatformConformance.check(
                Platforms.lenient().platformProviderId("  ").build());

        assertEquals(1, failures.size(), failures.toString());
        assertTrue(failures.get(0).contains("blank"), failures.get(0));
    }
}
