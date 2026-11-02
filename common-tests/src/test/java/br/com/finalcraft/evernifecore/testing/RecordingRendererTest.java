package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The recording renderer refuses the opens a test asks it to, and only those. */
class RecordingRendererTest {

    @TempDir
    Path tempDir;

    @Test
    void refuseNextOpenRefusesOneOpenAndTheNextOneOpens() {
        try (ScreenWorld screens = ScreenWorld.open(tempDir)) {
            TestFPlayerSender alex = screens.viewer("Alex");
            Screen<?> hub = Screen.of(3).title("Hub");
            screens.getRenderer().refuseNextOpen();

            CompletableFuture<ScreenView> refused = screens.tryOpen(hub, alex);
            screens.advanceTicks(2);
            ScreenView opened = screens.open(hub, alex);

            assertTrue(refused.isCompletedExceptionally());
            assertSame(opened, Screens.getOpenView(alex));
            assertEquals(1, screens.getRenderer().getSurfaces().size());
            opened.close();
            screens.advanceTicks(2);
        }
    }
}
