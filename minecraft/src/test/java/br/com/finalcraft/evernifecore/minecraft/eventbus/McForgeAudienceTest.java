package br.com.finalcraft.evernifecore.minecraft.eventbus;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECNativeAudience;
import br.com.finalcraft.evernifecore.minecraft.testkit.BukkitEventWorld;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Forge audience on a server that has no Forge on it, which is every server this suite can run:
 * it takes its place on the bus, it answers for one name only, it leaves when the plugin does, and
 * while it is there it stays completely quiet.
 */
class McForgeAudienceTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void enablingThePluginPutsTheForgeAudienceOnTheGlobalBus() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            assertEquals(1, forgeAudiences().size(), "the plugin registers exactly one forge audience: "
                    + audienceNames());
            assertTrue(forgeAudiences().contains(world.getForgeAudience()),
                    "and it is the instance the world installed, not a copy");
        }
    }

    @Test
    void registeringASecondForgeAudienceReplacesTheFirstInsteadOfStacking() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            McForgeAudience second = new McForgeAudience();
            ECEventBus.global().addNativeAudience(second);

            List<ECNativeAudience> registered = forgeAudiences();
            assertEquals(1, registered.size(), "name() is the idempotency key, so a re-enable replaces: "
                    + audienceNames());
            assertTrue(registered.contains(second), "and what stays is the audience registered last");
        }
    }

    @Test
    void shuttingThePluginDownTakesTheForgeAudienceOffTheBus() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            assertFalse(forgeAudiences().isEmpty(), "the audience has to be there before removing it means anything");
        }

        assertTrue(forgeAudiences().isEmpty(), "nothing answering to the forge name is left behind: "
                + audienceNames());
    }

    @Test
    void onAServerWithNoForgeTheAudienceNeitherOpensItsGateNorSaysAnything() {
        List<String> logged = Logs.capture(() -> {
            try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
                assertFalse(world.getForgeAudience().hasListeners(SampleEvent.class),
                        "no hybrid platform is on this classpath, so the gate stays shut");

                //a post is what would reach dispatch() if the gate had opened
                ECEventBus.global().post(new SampleEvent());
            }
        });

        assertTrue(logged.stream().noneMatch(line -> line.toLowerCase().contains("forge")),
                "the audience is silent where there is no Forge - it is the case that runs everywhere: " + logged);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  helpers
    // -----------------------------------------------------------------------------------------------------------------

    private static List<ECNativeAudience> forgeAudiences() {
        return ECEventBus.global().getNativeAudiences().stream()
                .filter(audience -> McForgeAudience.NAME.equals(audience.name()))
                .collect(Collectors.toList());
    }

    /** What the bus actually holds, so a failure names the audiences instead of just a count. */
    private static String audienceNames() {
        return ECEventBus.global().getNativeAudiences().stream()
                .map(ECNativeAudience::name)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /** Platform-visible, which is what the bus requires before it mirrors anything at all. */
    static class SampleEvent extends ECEvent {
    }

}
