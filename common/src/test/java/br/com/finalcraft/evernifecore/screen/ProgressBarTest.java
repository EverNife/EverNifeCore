package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.testing.RecordingSurface;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A bar a grid renderer can only draw as an icon: its text carries the ratio, and follows it. */
class ProgressBarTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;

    @BeforeEach
    void setup() {
        world = ScreenWorld.open(tempDir);
        Layouts.clear();
    }

    @AfterEach
    void teardown() {
        Layouts.clear();
        world.close();
    }

    @ScreenLayout(title = "Progress", rows = 3)
    public static class ProgressLayout extends Layout {

        @IconData(slot = {13})
        public Icon BAR = Icon.of(ItemSpec.of("minecraft:experience_bottle", 1)).displayName("{progress}%");
    }

    @Test
    void theIconTextReadsTheRatioAsAPercentageAndRedrawsWhenItMoves() {
        MutableState<Double> ratio = new MutableState<>(0.5d);
        Screen<ProgressLayout> screen = Screen.of(ProgressLayout.class);
        screen.progress(l -> l.BAR, ratio);
        ScreenView view = world.open(screen, world.viewer("Steve"));
        RecordingSurface surface = world.getRenderer().surfaceOf(view);

        assertEquals("50%", surface.getSlot(13).getItem().getDisplayName());
        assertEquals(0.5d, surface.getLastPatch(13).getProgress());

        ratio.set(1d);
        world.advanceTicks(1);
        assertEquals("100%", surface.getSlot(13).getItem().getDisplayName());

        view.close();
        world.advanceTicks(2);
    }

}
