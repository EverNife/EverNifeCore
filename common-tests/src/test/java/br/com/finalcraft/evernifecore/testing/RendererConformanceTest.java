package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.screen.RenderedIcon;
import br.com.finalcraft.evernifecore.screen.RendererCapability;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RendererConformanceTest {

    @TempDir
    Path tempDir;

    @Test
    void theRecordingRendererConforms() {
        try (ScreenWorld world = ScreenWorld.open(tempDir)) {
            List<String> failures = RendererConformance.check(world.getRenderer(), world);

            assertTrue(failures.isEmpty(), String.valueOf(failures));
        }
    }

    @Test
    void theRecordingRendererWithTitlesConformsTooAndRunsTheTitleScenario() {
        try (ScreenWorld world = ScreenWorld.open(tempDir, RendererCapability.TITLE_CHANGE)) {
            List<String> failures = RendererConformance.check(world.getRenderer(), world);

            assertTrue(failures.isEmpty(), String.valueOf(failures));
        }
    }

    @Test
    void aRendererThatDropsItsPatchesFailsNamingTheBatchScenario() {
        try (ScreenWorld world = ScreenWorld.open(tempDir)) {
            Sabotaged sabotaged = new Sabotaged(world.getTicks());
            world.withRenderer(sabotaged);

            List<String> failures = RendererConformance.check(sabotaged, world);

            assertFalse(failures.isEmpty(), "the guard is worth something only once it goes red");
            assertTrue(failures.stream().anyMatch(line -> line.startsWith("patch-batches-per-tick")), String.valueOf(failures));
        }
    }

    @Test
    void aRendererNotRegisteredInTheWorldIsASetupFailure() {
        try (ScreenWorld world = ScreenWorld.open(tempDir)) {
            List<String> failures = RendererConformance.check(new Sabotaged(world.getTicks()), world);

            assertEquals(1, failures.size());
            assertTrue(failures.get(0).startsWith("setup"), failures.get(0));
        }
    }

    /** Opens windows that swallow every write: the view thinks it drew, the window shows nothing. */
    private static final class Sabotaged extends RecordingRenderer {

        private Sabotaged(TickScheduler ticks) {
            super("test:sabotaged", 50, ticks);
        }

        @Nonnull
        @Override
        protected RecordingSurface newSurface(@Nonnull ScreenView view) {
            return new RecordingSurface(view) {
                @Override
                protected void writeSlot(int slot, @Nullable RenderedIcon icon) {
                    //dropped on purpose
                }
            };
        }

    }

}
