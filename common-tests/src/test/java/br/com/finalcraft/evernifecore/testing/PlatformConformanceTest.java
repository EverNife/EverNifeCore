package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.region.shape.WorldHeight;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.notify.IHudAccess;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void theWorldHeightIsAnsweredOnlyWhenTaught() {
        assertEquals(WorldHeight.of(0, 255), Platforms.lenient().build().getWorldHeight("world"));
        assertEquals(WorldHeight.of(-64, 319), Platforms.strict().worldHeight(-64, 319).build().getWorldHeight("nether"));

        UnsupportedOperationException refused = assertThrows(UnsupportedOperationException.class,
                () -> Platforms.strict().build().getWorldHeight("world"));
        assertTrue(refused.getMessage().contains("Platforms.strict().worldHeight(...)"), refused.getMessage());
        assertTrue(PlatformConformance.check(Platforms.strict().build()).stream().anyMatch(f -> f.startsWith("getWorldHeight")));
    }

    @Test
    void aNullOutlineRendererIsAViolationNamingTheMethod() {
        List<String> failures = PlatformConformance.check(Platforms.lenient().outlineRenderer(null).build());

        assertEquals(1, failures.size(), failures.toString());
        assertTrue(failures.get(0).startsWith("getOutlineRenderer"), failures.get(0));
    }

    @Test
    void aNullHudIsAViolationNamingTheMethod() {
        List<String> failures = PlatformConformance.check(Platforms.lenient().hudAccess(null).build());

        assertEquals(1, failures.size(), failures.toString());
        assertTrue(failures.get(0).startsWith("getHud"), failures.get(0));
    }

    @Test
    void anUnsupportedHudCapabilityThatDoesNotThrowIsAViolation() {
        //claims no sound, yet a sound call returns normally: a notify chain would think it delivered
        IHudAccess quiet = new IHudAccess() {
            @Override
            public boolean supports(Capability capability) {
                return capability != Capability.SOUND;
            }

            @Override
            public void playSound(FPlayer player, String soundId, float volume, float pitch) {
            }
        };

        List<String> failures = PlatformConformance.check(Platforms.lenient().hudAccess(quiet).build());

        assertEquals(1, failures.size(), failures.toString());
        assertTrue(failures.get(0).contains("SOUND") && failures.get(0).contains("returned normally"), failures.get(0));
    }

    @Test
    void theUnsupportedHudConforms() {
        assertTrue(PlatformConformance.check(Platforms.lenient().hudAccess(IHudAccess.UNSUPPORTED).build()).isEmpty());
    }

    @Test
    void aWorldThreadHopWhoseFutureNeverCompletesIsAViolation() {
        IPlatform stuck = overriding(Platforms.lenient().build(), "runOnWorldThread", args -> new CompletableFuture<Void>());

        List<String> failures = PlatformConformance.check(stuck);

        assertEquals(1, failures.size(), failures.toString());
        assertTrue(failures.get(0).startsWith("runOnWorldThread") && failures.get(0).contains("did not complete"), failures.get(0));
    }

    @Test
    void aWorldThreadHopOffTheWorldThreadIsAViolation() {
        //the task runs in place, yet isWorldThread answers false inside it: it ran on a thread that does not own the world
        List<String> failures = PlatformConformance.check(Platforms.lenient().offWorldThread().build());

        //"runOnWorldThreadNextTick" also starts with "runOnWorldThread": each probe is named by its whole method
        assertEquals(2, failures.size(), failures.toString());
        assertTrue(failures.get(0).startsWith("runOnWorldThread threw") && failures.get(0).contains("not the world's"), failures.get(0));
        assertTrue(failures.get(1).startsWith("runOnWorldThreadNextTick threw") && failures.get(1).contains("not the world's"), failures.get(1));
    }

    @Test
    void onlyARunOnWorldThreadOffTheWorldThreadNamesThatHop() {
        List<String> failures = PlatformConformance.check(offTheWorldThreadIn("runOnWorldThread"));

        assertEquals(1, failures.size(), failures.toString());
        assertTrue(failures.get(0).startsWith("runOnWorldThread threw") && failures.get(0).contains("not the world's"), failures.get(0));
    }

    @Test
    void onlyANextTickHopOffTheWorldThreadNamesThatHop() {
        List<String> failures = PlatformConformance.check(offTheWorldThreadIn("runOnWorldThreadNextTick"));

        assertEquals(1, failures.size(), failures.toString());
        assertTrue(failures.get(0).startsWith("runOnWorldThreadNextTick threw") && failures.get(0).contains("not the world's"), failures.get(0));
    }

    @Test
    void aNullWorldListenerAcceptedIsAViolation() {
        IPlatform keepsAnything = overriding(Platforms.lenient().build(), "onWorldLoaded", args -> null);

        List<String> failures = PlatformConformance.check(keepsAnything);

        assertEquals(1, failures.size(), failures.toString());
        assertTrue(failures.get(0).startsWith("onWorldLoaded") && failures.get(0).contains("null listener"), failures.get(0));
    }

    /** A lenient double whose {@code hop} runs its task in place, on a thread that does not own the world. */
    private static IPlatform offTheWorldThreadIn(String hop) {
        boolean[] inHop = new boolean[1];
        IPlatform answersFromInside = overriding(Platforms.lenient().build(), "isWorldThread", args -> !inHop[0]);
        return overriding(answersFromInside, hop, args -> {
            inHop[0] = true;
            try {
                ((Runnable) args[1]).run();
            } finally {
                inHop[0] = false;
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    /** {@code platform}, except that {@code method} answers {@code answer} instead. */
    private static IPlatform overriding(IPlatform platform, String method, Function<Object[], Object> answer) {
        return (IPlatform) Proxy.newProxyInstance(IPlatform.class.getClassLoader(), new Class<?>[]{IPlatform.class}, (proxy, called, args) -> {
            if (called.getName().equals(method)) {
                return answer.apply(args);
            }
            try {
                return called.invoke(platform, args);
            } catch (InvocationTargetException thrown) {
                throw thrown.getCause();
            }
        });
    }
}
