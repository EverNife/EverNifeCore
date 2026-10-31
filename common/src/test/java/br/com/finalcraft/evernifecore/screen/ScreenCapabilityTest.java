package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.testing.RecordingRenderer;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenCapabilityTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;
    private TestFPlayerSender alex;

    @BeforeEach
    void setup() {
        world = ScreenWorld.open(tempDir);
        alex = world.viewer("Alex");
    }

    @AfterEach
    void teardown() {
        world.close();
    }

    private static Screen<?> withStorage() {
        Screen<?> screen = Screen.of(3).key(ScreenKey.of("test", "backpack"));
        screen.storage(Slots.of(0));
        return screen;
    }

    private static String refusal(CompletableFuture<ScreenView> opening) {
        CompletionException failed = assertThrows(CompletionException.class, opening::join);
        return failed.getCause().getMessage();
    }

    @Test
    void aMissingCapabilityIsRefusedNamingItAndTheRendererThatServesThePlayer() {
        String message = refusal(world.tryOpen(withStorage(), alex));

        assertTrue(message.contains("STORAGE_SLOTS"), message);
        assertTrue(message.contains(RecordingRenderer.ID), message);
        assertTrue(message.contains("test:backpack"), "it names the screen: " + message);
        assertTrue(message.contains("ScreenFallback.open"), "and it teaches the way out: " + message);
    }

    @Test
    void aDeclaredFallbackOpensTheAlternativeInstead() {
        Screen<?> plain = Screen.of(3).key(ScreenKey.of("test", "plain"));
        Screen<?> screen = withStorage().onMissingCapability(ScreenFallback.open(plain));

        ScreenView view = world.open(screen, alex);

        assertSame(plain, view.getScreen());
        view.close();
        world.advanceTicks(2);
    }

    @Test
    void theHighestPriorityRendererThatCoversTheScreenDrawsIt() {
        RecordingRenderer storage = new RecordingRenderer("test:storage", -5, world.getTicks(), RendererCapability.STORAGE_SLOTS);
        RecordingRenderer fancy = new RecordingRenderer("test:fancy", 10, world.getTicks());
        world.withRenderer(storage).withRenderer(fancy);

        ScreenView chest = world.open(withStorage(), alex);
        assertEquals("test:storage", chest.getRenderer().getId(), "only it has STORAGE_SLOTS");
        chest.close();
        world.advanceTicks(2);

        ScreenView plain = world.open(Screen.of(1), alex);
        assertEquals("test:fancy", plain.getRenderer().getId(), "the highest priority wins a screen anyone can draw");
        plain.close();
        world.advanceTicks(2);
    }

    @Test
    void aPlatformWithNoRendererRefusesNamingThePlatform() {
        Screens.unregisterRenderers(world.getOwner());

        String message = refusal(world.tryOpen(Screen.of(1), alex));

        assertTrue(message.contains("none is registered on this platform"), message);
        assertTrue(message.contains("Screens.registerRenderer"), "it says how a platform gets one: " + message);
    }

    @Test
    void aSecondRendererUnderTheSameIdIsRefusedNamingBothOwners() {
        IllegalStateException clash = assertThrows(IllegalStateException.class,
                () -> world.withRenderer(new RecordingRenderer(world.getTicks())));

        assertTrue(clash.getMessage().contains(RecordingRenderer.ID), clash.getMessage());
    }

}
