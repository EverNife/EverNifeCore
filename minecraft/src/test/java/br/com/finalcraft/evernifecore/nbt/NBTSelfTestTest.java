package br.com.finalcraft.evernifecore.nbt;

import br.com.finalcraft.evernifecore.minecraft.nbt.NBTSelfTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the NBT-API self-test as part of the suite.
 *
 * NBT-API reflects into NMS, which is absent from a headless unit-test JVM
 * (craftbukkit is a compile-only dependency, not on the test runtime classpath).
 * When those classes cannot be linked the run reports {@code environmentUnavailable};
 * we translate that into a JUnit assumption so the test is reported as SKIPPED
 * rather than failing. On an environment that does provide NBT-API (a real server,
 * or a future NMS-backed harness) the same test asserts that every check passes.
 */
public class NBTSelfTestTest {

    @Test
    void nbtIntegrationSelfTest() {
        NBTSelfTest.Result result = NBTSelfTest.run();

        Assumptions.assumeFalse(
                result.isEnvironmentUnavailable(),
                "NBT-API needs a live server (NMS); skipped in the headless unit-test JVM"
        );

        assertTrue(
                result.isSuccess(),
                () -> "NBT-API self-test failed: " + result.getSummary()
                        + System.lineSeparator() + String.join(System.lineSeparator(), result.getSteps())
        );
    }
}
