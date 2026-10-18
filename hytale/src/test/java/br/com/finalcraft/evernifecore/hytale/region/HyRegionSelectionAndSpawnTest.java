package br.com.finalcraft.evernifecore.hytale.region;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.context.SpawnContext;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.selection.ItemSpec;
import br.com.finalcraft.evernifecore.region.selection.SelectionClick;
import br.com.finalcraft.evernifecore.region.selection.SelectionSession;
import br.com.finalcraft.evernifecore.region.selection.SelectionSpec;
import br.com.finalcraft.evernifecore.region.selection.SelectionTool;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.region.visual.VisualStyle;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.RegionWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The selection tool on Hytale - marked by metadata, its clicks taken before any region - and the creature spawn a region denies. */
@ECoreTest
class HyRegionSelectionAndSpawnTest {

    private static final String WORLD = "world";

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld server;
    private RegionWorld regions;
    private ECPluginData core;
    private final TestFPlayerSender builder = new TestFPlayerSender("builder").at(WORLD, 5, 5, 5);
    private final List<Shape> confirmed = new ArrayList<>();

    @BeforeEach
    void open() {
        server = Platforms.lenient().recordingSelectionItems().install();
        regions = RegionWorld.open("HySelectOwner", tempDir.resolve("regions"));
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        FlagRegistry.get().register(core, CoreFlags.DENY_SPAWN);
        FlagRegistry.get().register(core, CoreFlags.DENY_SPAWN_MONSTER);
        FCLocaleManager.loadLocale(regions.getOwner(), SelectionTool.class, SelectionSession.class);
    }

    @AfterEach
    void close() {
        try {
            SelectionTool.cancelAll();
            regions.close();
            FlagRegistry.get().unregisterAll(regions.getOwner());
            FlagRegistry.get().unregisterAll(core);
            Plugins.forget(Plugins.CORE);
        } finally {
            server.close();
        }
    }

    private boolean click(ItemStack hand, DamageBlockEvent event, int x, int y, int z, SelectionClick kind) {
        return HyRegionSystems.select(hand, event, () -> builder, WORLD, new Vector3i(x, y, z), () -> kind);
    }

    private static DamageBlockEvent hit(ItemStack hand, int x, int y, int z) {
        return new DamageBlockEvent(hand, new Vector3i(x, y, z), null, 0f, 1f);
    }

    /** An item as the server would hold it; building one through its public constructors needs the game's assets loaded. */
    private static ItemStack stack(String id, boolean marked) {
        return new ItemStack() {
            {
                itemId = id;
                quantity = 1;
                metadata = marked ? new BsonDocument(HySelectionItems.MARK, BsonBoolean.TRUE) : null;
            }
        };
    }

    @Test
    void theToolIsKnownByItsMarkAndAMinecraftIdBecomesAHytaleItem() {
        assertTrue(HySelectionItems.isSelectionTool(stack(HySelectionItems.DEFAULT_ITEM, true)));
        assertFalse(HySelectionItems.isSelectionTool(stack(HySelectionItems.DEFAULT_ITEM, false)), "a hoe of the game is no tool");
        assertFalse(HySelectionItems.isSelectionTool(null));
        assertEquals(HySelectionItems.DEFAULT_ITEM, HySelectionItems.itemIdOf(ItemSpec.DEFAULT_TOOL.getMaterialId()), "a minecraft: id has no Hytale item");
        assertEquals("Rock_Stone", HySelectionItems.itemIdOf("hytale:Rock_Stone"));
    }

    @Test
    void wandClicksMakeTheShapeAndAreCancelledWhileAPlainHoeIsLeftToTheRegions() {
        ItemStack tool = stack(HySelectionItems.DEFAULT_ITEM, true);
        SelectionSession session = SelectionTool.start(builder, SelectionSpec.cuboid(), confirmed::add);

        DamageBlockEvent first = hit(tool, 0, 0, 0);
        assertTrue(click(tool, first, 0, 0, 0, SelectionClick.LEFT));
        assertTrue(first.isCancelled(), "the tool broke the block it selected");
        assertTrue(click(tool, hit(tool, 0, 0, 0), 0, 0, 0, SelectionClick.LEFT), "a held click is still the tool's");
        assertEquals(1, session.getPoints().size(), "hitting the last point again added it twice");
        assertTrue(click(tool, hit(tool, 4, 4, 4), 4, 4, 4, SelectionClick.LEFT));
        assertEquals(2, session.getPoints().size());

        ItemStack hoe = stack(HySelectionItems.DEFAULT_ITEM, false);
        DamageBlockEvent plain = hit(hoe, 8, 8, 8);
        assertFalse(click(hoe, plain, 8, 8, 8, SelectionClick.LEFT));
        assertFalse(plain.isCancelled());

        assertTrue(click(tool, hit(tool, 4, 4, 4), 4, 4, 4, SelectionClick.SNEAK_RIGHT));
        assertEquals(1, confirmed.size(), "sneak + right click did not confirm");
    }

    @Test
    void withNoSelectionOpenTheToolIsAPlainItem() {
        ItemStack tool = stack(HySelectionItems.DEFAULT_ITEM, true);
        DamageBlockEvent event = hit(tool, 0, 0, 0);

        assertFalse(click(tool, event, 0, 0, 0, SelectionClick.LEFT));
        assertFalse(event.isCancelled());
    }

    @Test
    void aMonsterSpawningInDenySpawnMonsterIsDeniedAndAnAnimalIsNot() {
        regions.cuboid("den", WORLD, 0, 0, 0, 10, 10, 10).setFlag(CoreFlags.DENY_SPAWN_MONSTER, Tristate.TRUE);

        assertTrue(spawn("Trork_Brute", Attitude.HOSTILE, 5));
        assertFalse(spawn("Sheep", Attitude.NEUTRAL, 5));
        assertFalse(spawn("Trork_Brute", Attitude.HOSTILE, 50), "outside the region it spawns");
    }

    @Test
    void withNoRegionNeedingSpawnNoContextIsBuilt() {
        AtomicInteger built = new AtomicInteger();

        assertFalse(HyRegionSystems.spawnDenied(WORLD, () -> {
            built.incrementAndGet();
            return null;
        }));
        assertEquals(0, built.get());
    }

    @Test
    void everyStyleHasItsOwnParticle() {
        Set<String> particles = new HashSet<>();
        for (VisualStyle style : EnumSet.allOf(VisualStyle.class)) {
            assertNotNull(HyParticleOutlineRenderer.particleOf(style));
            particles.add(HyParticleOutlineRenderer.particleOf(style));
        }
        assertEquals(VisualStyle.values().length, particles.size());
    }

    private boolean spawn(String npc, Attitude attitude, double x) {
        Actor creature = HyRegionContexts.actor(null, npc, attitude, null);
        return HyRegionSystems.spawnDenied(WORLD, () -> new SpawnContext(HyRegionContexts.position(WORLD, new Vector3d(x, 5, 5)),
                Actor.environment(HyRegionSystems.NATURAL_SPAWN), null, creature, "SPAWN"));
    }
}
