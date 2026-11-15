package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Logic run from a screen - a delivery it leaves among it - is emitted by the screen's id, never its coloured title. */
class ScreenEmitterTest {

    @ScreenLayout(title = "&2Sawmill", rows = 1)
    public static class SawmillLayout extends Layout {
    }

    @TempDir
    Path tempDir;

    private ScreenWorld world;
    private TestFPlayerSender alex;

    @BeforeEach
    void open() {
        world = ScreenWorld.open(tempDir);
        alex = world.viewer("Alex");
    }

    @AfterEach
    void close() {
        world.close();
        Layouts.clear();
    }

    private String emitterOf(Screen<?> screen) {
        ScreenView view = world.open(screen, alex);
        String emitter = view.newExecutionContext().build().getCaller().getOrigin();
        view.close();
        world.advanceTicks(1);
        return emitter;
    }

    @Test
    void aLayoutScreenEmitsByTheLayoutsName() {
        assertEquals("screen:SawmillLayout", emitterOf(Screen.of(SawmillLayout.class)));
    }

    @Test
    void aKeyedScreenEmitsByItsKey() {
        assertEquals("screen:test:sawmill", emitterOf(Screen.of(SawmillLayout.class).key(ScreenKey.of("test", "sawmill"))));
    }

    @Test
    void anAdHocScreenEmitsByItsTitleWithoutColourCodes() {
        assertEquals("screen:Sawmill", emitterOf(Screen.of(1).title("§2Sawmill")));
    }
}
