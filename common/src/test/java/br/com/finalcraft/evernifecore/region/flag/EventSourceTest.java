package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The core's eight sources are constants; a plugin registers its own, and they leave with it. */
@ECoreTest
class EventSourceTest {

    private static final String DUEL = "TheDuel";
    private static final String ARENA = "Arena";

    @TempDir
    Path tempDir;

    private ECPluginData duel;
    private ECPluginData arena;

    @BeforeEach
    void setUp() {
        duel = Plugins.fakePluginData(DUEL, tempDir.resolve("duel").toFile());
        arena = Plugins.fakePluginData(ARENA, tempDir.resolve("arena").toFile());
    }

    @AfterEach
    void tearDown() {
        EventSource.unregisterAll(duel);
        EventSource.unregisterAll(arena);
        Plugins.forget(DUEL);
        Plugins.forget(ARENA);
    }

    private static final List<EventSource> CORE = Arrays.asList(EventSource.MOVE, EventSource.BREAK, EventSource.PLACE,
            EventSource.INTERACT, EventSource.DAMAGE, EventSource.SPAWN, EventSource.TICK, EventSource.NONE);

    @Test
    void theCoreSourcesBelongToTheCore() {
        assertTrue(EventSource.all().containsAll(CORE));
        for (EventSource source : CORE) {
            assertEquals("evernifecore", source.getOwner());
            assertEquals("evernifecore:" + source.getName(), source.toString());
        }
        assertEquals("evernifecore:move", EventSource.MOVE.toString());
    }

    @Test
    void aPluginRegistersItsOwnSourceOnce() {
        EventSource hit = EventSource.register(duel, "duel-hit");

        assertSame(hit, EventSource.register(duel, "Duel-Hit"), "the same name, any case, is the same source");
        assertEquals("theduel:duel-hit", hit.toString());
        assertEquals("theduel", hit.getOwner());
        assertNotEquals(EventSource.MOVE, EventSource.register(duel, "move"), "a plugin's move is not the core's");
        assertTrue(EventSource.all().contains(hit));
    }

    @Test
    void unregisteringAPluginDropsOnlyItsSources() {
        EventSource hit = EventSource.register(duel, "duel-hit");
        EventSource round = EventSource.register(arena, "round");

        EventSource.unregisterAll(duel);

        assertFalse(EventSource.all().contains(hit));
        assertTrue(EventSource.all().contains(round));
        assertTrue(EventSource.all().containsAll(CORE), "the core's never leave");
    }

    @Test
    void equalityIsByText() {
        EventSource duelHit = EventSource.register(duel, "hit");
        EventSource arenaHit = EventSource.register(arena, "hit");

        assertNotEquals(duelHit, arenaHit);
        assertEquals(EventSource.MOVE, EventSource.MOVE);
        assertEquals("evernifecore:move".hashCode(), EventSource.MOVE.hashCode());
        assertEquals("theduel:hit".hashCode(), duelHit.hashCode());
    }
}
