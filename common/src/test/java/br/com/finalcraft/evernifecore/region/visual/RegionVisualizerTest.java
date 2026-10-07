package br.com.finalcraft.evernifecore.region.visual;

import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.region.selection.SelectionClick;
import br.com.finalcraft.evernifecore.region.selection.SelectionSession;
import br.com.finalcraft.evernifecore.region.selection.SelectionSpec;
import br.com.finalcraft.evernifecore.region.selection.SelectionTool;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.RegionWorld;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Frames every 10 ticks while a view lasts, and none at all while the visualizer is off. */
@ECoreTest
class RegionVisualizerTest {

    private static final CuboidShape BOX = CuboidShape.of(BlockPos.of(0, 60, 0), BlockPos.of(9, 69, 9));

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private TestPlatform platform;
    private RegionWorld regions;
    private TestFPlayerSender steve;

    @BeforeEach
    void open() {
        world = Platforms.lenient().recordingSelectionItems().recordingOutlines().install();
        platform = world.platform();
        regions = RegionWorld.open("VisualizerOwner", tempDir);
        FCLocaleManager.loadLocale(regions.getOwner(), SelectionTool.class, SelectionSession.class);
        steve = Senders.player("Steve").at("world", 5, 64, 5);
    }

    @AfterEach
    void close() {
        ECSettings.REGIONS_VISUALIZER_ENABLED = false;
        RegionVisualizer.get().hide(steve);
        SelectionTool.cancelAll();
        regions.close();
        world.close();
    }

    private void tick() {
        regions.advance(Duration.ofMillis(RegionVisualizer.FRAME_MILLIS));
        regions.runDue();
    }

    @Test
    void visualizerIsOffByDefaultAndRendersNothing() {
        assertFalse(RegionVisualizer.get().isEnabled());

        RegionVisualizer.get().show(steve, "world", BOX, VisualStyle.OWN, Duration.ofSeconds(10));
        regions.runDue();
        tick();

        assertTrue(platform.getRenderedOutlines().isEmpty());
        assertFalse(RegionVisualizer.get().isShowing(steve));
    }

    @Test
    void showRendersEveryTenTicksUntilTheDurationEnds() {
        ECSettings.REGIONS_VISUALIZER_ENABLED = true;

        RegionVisualizer.get().show(steve, "world", BOX, VisualStyle.OTHER_OWNER, Duration.ofMillis(1500));
        regions.runDue();
        tick();
        tick();

        assertEquals(3, platform.getRenderedOutlines().size());
        assertTrue(platform.getRenderedOutlines().stream().allMatch(frame -> frame.style == VisualStyle.OTHER_OWNER));
        assertFalse(platform.getRenderedOutlines().get(0).points.isEmpty());

        tick();
        tick();
        assertEquals(3, platform.getRenderedOutlines().size(), "the duration is over");
        assertFalse(RegionVisualizer.get().isShowing(steve));
    }

    @Test
    void showLiveFollowsTheSessionAndStopsOnConfirm() {
        ECSettings.REGIONS_VISUALIZER_ENABLED = true;
        SelectionTool.start(steve, SelectionSpec.cuboid().withLivePreview(true), shape -> { });
        SelectionTool.handleClick(steve, WorldBlockPos.of(0, 60, 0, "world"), SelectionClick.LEFT);
        regions.runDue();

        assertEquals(1, platform.getRenderedOutlines().size());
        assertEquals(VisualStyle.SELECTION, platform.getRenderedOutlines().get(0).style);
        assertEquals(1, platform.getRenderedOutlines().get(0).points.size(), "one loose point before there is a shape");

        SelectionTool.handleClick(steve, WorldBlockPos.of(9, 69, 9, "world"), SelectionClick.LEFT);
        tick();
        assertEquals(2, platform.getRenderedOutlines().size());
        assertTrue(platform.getRenderedOutlines().get(1).points.size() > 2, "the box outline once two corners exist");

        SelectionTool.handleClick(steve, WorldBlockPos.of(9, 69, 9, "world"), SelectionClick.SNEAK_RIGHT);
        tick();
        tick();
        assertEquals(2, platform.getRenderedOutlines().size(), "a confirmed selection is no longer drawn");
    }

    @Test
    void hideStopsTheFrames() {
        ECSettings.REGIONS_VISUALIZER_ENABLED = true;
        RegionVisualizer.get().show(steve, "world", BOX, VisualStyle.OWN, Duration.ofSeconds(10));
        regions.runDue();
        assertEquals(1, platform.getRenderedOutlines().size());

        RegionVisualizer.get().hide(steve);
        tick();
        tick();

        assertEquals(1, platform.getRenderedOutlines().size());
    }
}
