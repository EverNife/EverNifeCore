package br.com.finalcraft.evernifecore.region.selection;

import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.region.shape.PolygonShape;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The selection tool's buttons, and every way a selection ends. */
@ECoreTest
class SelectionSessionTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private TestPlatform platform;
    private RegionWorld regions;
    private TestFPlayerSender steve;
    private final List<Shape> confirmed = new ArrayList<>();

    @BeforeEach
    void open() {
        world = Platforms.lenient().recordingSelectionItems().recordingOutlines().install();
        platform = world.platform();
        regions = RegionWorld.open("SelectionOwner", tempDir);
        FCLocaleManager.loadLocale(regions.getOwner(), SelectionTool.class, SelectionSession.class);
        steve = Senders.player("Steve").at("world", 0, 64, 0);
    }

    @AfterEach
    void close() {
        SelectionTool.cancelAll();
        regions.close();
        world.close();
    }

    private SelectionSession start(SelectionSpec spec) {
        return SelectionTool.start(steve, spec, confirmed::add);
    }

    private boolean click(int x, int y, int z, SelectionClick click) {
        return SelectionTool.handleClick(steve, WorldBlockPos.of(x, y, z, "world"), click);
    }

    @Test
    void leftAddsRightRemovesSneakRightConfirms() {
        SelectionSession session = start(SelectionSpec.cuboid());

        assertTrue(click(10, 70, 10, SelectionClick.LEFT));
        assertTrue(click(0, 60, 5, SelectionClick.LEFT));
        assertTrue(click(99, 99, 99, SelectionClick.LEFT));
        assertEquals(3, session.getPoints().size());
        assertTrue(click(0, 0, 0, SelectionClick.RIGHT));
        assertEquals(2, session.getPoints().size());
        assertEquals(CuboidShape.of(BlockPos.of(0, 60, 5), BlockPos.of(10, 70, 10)), session.toShape());
        assertEquals(BlockPos.of(0, 60, 5), ((CuboidShape) session.toShape()).getMin());

        assertTrue(click(0, 0, 0, SelectionClick.SNEAK_RIGHT));

        assertEquals(1, confirmed.size());
        assertEquals(CuboidShape.of(BlockPos.of(0, 60, 5), BlockPos.of(10, 70, 10)), confirmed.get(0));
        assertNull(SelectionTool.getSession(steve));
        assertFalse(session.isActive());
    }

    @Test
    void confirmWithTooFewPointsKeepsTheSession() {
        SelectionSession session = start(SelectionSpec.cuboid());
        click(1, 1, 1, SelectionClick.LEFT);
        steve.clearMessages();

        click(0, 0, 0, SelectionClick.SNEAK_RIGHT);

        assertTrue(steve.getCaptured().anyContains("§e1§c more point"), steve.getMessages().toString());
        assertTrue(confirmed.isEmpty());
        assertSame(session, SelectionTool.getSession(steve));
    }

    @Test
    void polygonNeedsThreePointsAndKeepsTheirYRange() {
        SelectionSession session = start(SelectionSpec.polygon());
        click(0, 64, 0, SelectionClick.LEFT);
        click(10, 70, 0, SelectionClick.LEFT);
        assertNull(session.toShape(), "two points are not a polygon");

        click(5, 50, 10, SelectionClick.LEFT);
        click(0, 0, 0, SelectionClick.SNEAK_RIGHT);

        PolygonShape polygon = assertInstanceOf(PolygonShape.class, confirmed.get(0));
        assertEquals(3, polygon.getPoints().size());
        assertEquals(50, polygon.getMinY());
        assertEquals(70, polygon.getMaxY());
    }

    @Test
    void secondStartReplacesTheFirstSession() {
        List<Shape> firstConfirmed = new ArrayList<>();
        SelectionSession first = SelectionTool.start(steve, SelectionSpec.cuboid(), firstConfirmed::add);
        click(1, 1, 1, SelectionClick.LEFT);
        click(2, 2, 2, SelectionClick.LEFT);
        steve.clearMessages();

        SelectionSession second = start(SelectionSpec.cuboid());

        assertFalse(first.isActive());
        assertSame(second, SelectionTool.getSession(steve));
        assertTrue(steve.getCaptured().anyContains("replaced"), steve.getMessages().toString());
        click(1, 1, 1, SelectionClick.LEFT);
        click(2, 2, 2, SelectionClick.LEFT);
        click(0, 0, 0, SelectionClick.SNEAK_RIGHT);
        assertTrue(firstConfirmed.isEmpty(), "the replaced selection never confirms");
        assertEquals(1, confirmed.size());
    }

    @Test
    void sessionDiesOnAClickInAnotherWorld() {
        SelectionSession session = start(SelectionSpec.cuboid());
        click(1, 1, 1, SelectionClick.LEFT);

        assertTrue(SelectionTool.handleClick(steve, WorldBlockPos.of(1, 1, 1, "nether"), SelectionClick.LEFT));

        assertFalse(session.isActive());
        assertNull(SelectionTool.getSession(steve));
        assertTrue(steve.getCaptured().anyContains("changed world"), steve.getMessages().toString());
    }

    @Test
    void sessionDiesWhenThePlayerQuits() {
        SelectionSession session = start(SelectionSpec.cuboid());

        ECEventBus.global().post(new ECPlayerQuitEvent(new PlayerData(steve.getUniqueId(), steve.getName())));

        assertFalse(session.isActive());
        assertNull(SelectionTool.getSession(steve));
    }

    @Test
    void idleSessionExpiresAfterTheTimeout() {
        SelectionSession session = start(SelectionSpec.cuboid());

        regions.advance(Duration.ofMinutes(3));
        click(1, 1, 1, SelectionClick.LEFT);   //a touch moves the deadline to 3 + 5 minutes
        regions.advance(Duration.ofMinutes(3));
        regions.runDue();
        assertTrue(session.isActive(), "touched 3 minutes ago: not idle yet");

        regions.advance(Duration.ofMinutes(3));
        regions.runDue();

        assertFalse(session.isActive());
        assertNull(SelectionTool.getSession(steve));
        assertTrue(steve.getCaptured().anyContains("untouched"), steve.getMessages().toString());
        assertTrue(platform.getWorldThreadRuns().contains("world"), "the expiry speaks to the player on the world's thread");
    }

    @Test
    void startGivesTheToolThroughThePlatform() {
        ItemSpec wand = ItemSpec.of("MINECRAFT:STICK", "Wand", new ArrayList<>());
        start(SelectionSpec.cuboid().withTool(wand));
        start(SelectionSpec.polygon());

        assertEquals(2, platform.getSelectionItemsGiven().size());
        assertSame(steve, platform.getSelectionItemsGiven().get(0).player);
        assertEquals("minecraft:stick", platform.getSelectionItemsGiven().get(0).spec.getMaterialId());
        assertSame(ItemSpec.DEFAULT_TOOL, platform.getSelectionItemsGiven().get(1).spec);
        assertEquals("minecraft:golden_hoe", ItemSpec.DEFAULT_TOOL.getMaterialId());
    }

    @Test
    void clickWithoutSessionIsNotConsumed() {
        assertFalse(click(1, 1, 1, SelectionClick.LEFT));
    }

    @Test
    void interactIsNeededOnlyWhileASessionIsOpen() {
        TestFPlayerSender alex = Senders.player("Alex").at("world", 0, 64, 0);
        assertFalse(EventSourceUsage.get().isNeeded(EventSource.INTERACT));

        SelectionSession first = start(SelectionSpec.cuboid());
        assertTrue(EventSourceUsage.get().isNeeded(EventSource.INTERACT));
        SelectionSession second = SelectionTool.start(alex, SelectionSpec.cuboid(), shape -> { });

        first.cancel();
        assertTrue(EventSourceUsage.get().isNeeded(EventSource.INTERACT), "one selection is still open");

        SelectionTool.handleClick(alex, WorldBlockPos.of(0, 0, 0, "world"), SelectionClick.LEFT);
        SelectionTool.handleClick(alex, WorldBlockPos.of(5, 5, 5, "world"), SelectionClick.LEFT);
        SelectionTool.handleClick(alex, WorldBlockPos.of(5, 5, 5, "world"), SelectionClick.SNEAK_RIGHT);
        assertFalse(second.isActive());
        assertFalse(EventSourceUsage.get().isNeeded(EventSource.INTERACT), "the last selection closed the hold");
    }

    @Test
    void specDefaultsComeFromTheCoreSettings() {
        assertFalse(SelectionSpec.cuboid().isLivePreview());
        assertEquals(Duration.ofMinutes(5), SelectionSpec.cuboid().getTimeout());
        assertSame(ItemSpec.DEFAULT_TOOL, SelectionSpec.polygon().getTool());
        assertEquals(2, SelectionSpec.cuboid().getMinPoints());
        assertEquals(3, SelectionSpec.polygon().getMinPoints());
    }
}
