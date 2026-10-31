package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.testing.RecordingRenderer;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenEventsTest {

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

    @Test
    void anOpenPostsTheViewItsKeyAndTheRendererThatDrewIt() {
        List<ScreenOpenEvent> opened = world.recorded(ScreenOpenEvent.class);
        Screen<?> screen = Screen.of(1).key(ScreenKey.of("test", "shop"));

        ScreenView view = world.open(screen, alex);

        assertEquals(1, opened.size());
        assertSame(view, opened.get(0).getView());
        assertEquals(ScreenKey.of("test", "shop"), opened.get(0).getKey());
        assertEquals(RecordingRenderer.ID, opened.get(0).getRenderer().getId());
        view.close();
        world.advanceTicks(2);
    }

    @Test
    void aCloseIsPostedOnceAfterTheCloseHandlerWithItsReason() {
        List<ScreenCloseEvent> closed = world.recorded(ScreenCloseEvent.class);
        StringBuilder order = new StringBuilder();
        closed.clear();
        Screen<?> screen = Screen.of(1).onClose(context -> order.append("handler;"));
        ScreenView view = world.open(screen, alex);
        world.getRenderer().surfaceOf(view).playerCloses();
        order.append(closed.size()).append(";");
        world.getRenderer().surfaceOf(view).playerCloses();

        assertEquals(1, closed.size());
        assertEquals(CloseReason.PLAYER_CLOSED, closed.get(0).getReason());
        assertSame(alex, closed.get(0).getViewer(), "the event still names who was looking");
        assertEquals("handler;1;", order.toString(), "the handler runs before the event goes out");
    }

    @Test
    void aRefusedOpenPostsNothing() {
        List<ScreenOpenEvent> opened = world.recorded(ScreenOpenEvent.class);
        world.getRenderer().refuseOpens(true);

        assertTrue(world.tryOpen(Screen.of(1), alex).isCompletedExceptionally());
        assertTrue(opened.isEmpty());
        assertEquals(0, Screens.getOpenCount(), "a refused open leaves nothing registered");
    }

}
