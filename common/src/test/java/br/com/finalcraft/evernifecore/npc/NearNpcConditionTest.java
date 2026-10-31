package br.com.finalcraft.evernifecore.npc;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Conditions;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.FakeNpcProvider;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code near-npc} and the waypoint to an NPC, over a fake provider in a logic world. */
class NearNpcConditionTest {

    private static final NpcRef GUARD = NpcRef.of("citizens", "7");

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld quests;
    private TestFPlayerSender alex;

    @BeforeEach
    void setUp() {
        world = Platforms.lenient().install();
        quests = LogicWorld.open("FakeQuests", tempDir);
        Npcs.register(quests.getOwner(), new FakeNpcProvider("citizens").at(GUARD, WorldLocPos.of(100, 64, 100, "world")).named(GUARD, "Guard"));
        alex = quests.player("Alex");
    }

    @AfterEach
    void tearDown() {
        Npcs.unregisterAll(quests.getOwner());
        quests.close();
        world.close();
    }

    private static JsonNode node(String json) {
        try {
            return new ObjectMapper().readTree(json.replace('\'', '"'));
        } catch (Exception unreadable) {
            throw new IllegalArgumentException(unreadable);
        }
    }

    private Condition nearGuard(int radius) {
        return Conditions.parse(quests.getOwner(), node("{'type': 'near-npc', 'npc': 'citizens:7', 'radius': " + radius + "}"), ZoneOffset.UTC);
    }

    private ConditionResult test(Condition condition) {
        return Conditions.test(condition, quests.context(alex).build());
    }

    @Test
    void withinTheRadiusItPasses() {
        alex.at("world", 103, 64, 100);
        assertTrue(test(nearGuard(5)).isPassed());
    }

    @Test
    void outsideTheRadiusItFailsWithTheNameAndTheDistance() {
        alex.at("world", 123, 64, 100);
        ConditionResult result = test(nearGuard(5));
        assertFalse(result.isPassed());
        String why = result.getWhy().toPlainText();
        assertTrue(why.contains("Guard") && why.contains("23 blocks"), why);
    }

    @Test
    void anotherWorldFailsWhateverTheDistance() {
        alex.at("world_nether", 100, 64, 100);
        ConditionResult result = test(nearGuard(5));
        assertFalse(result.isPassed());
        assertTrue(result.getWhy().toPlainText().contains("world"), result.getWhy().toPlainText());
    }

    @Test
    void itIsAskedAgainOnMovement() {
        assertTrue(nearGuard(5).getNeeds().contains(EventSource.MOVE));
    }

    @Test
    void anNpcOfAnUnregisteredProviderFailsTheLoadListingTheRegisteredOnes() {
        TypeRegistryException refused = assertThrows(TypeRegistryException.class, () -> Conditions.parse(quests.getOwner(),
                node("{'type': 'near-npc', 'npc': 'pixelmon:4f2a', 'radius': 5}"), ZoneOffset.UTC));
        assertTrue(refused.getMessage().contains("'pixelmon'") && refused.getMessage().contains("registered: citizens"), refused.getMessage());
    }

    @Test
    void theWaypointTargetFollowsTheStoredLocationAndRefusesAnUnregisteredProvider() {
        NpcWaypointTarget target = new NpcWaypointTarget(GUARD);
        assertEquals(WorldLocPos.of(100, 64, 100, "world"), target.locate());
        assertNull(new NpcWaypointTarget(NpcRef.of("citizens", "8")).locate(), "an NPC its provider does not know is gone");
        assertNotNull(assertThrows(NpcProviderException.class, () -> new NpcWaypointTarget(NpcRef.parse("pixelmon:1"))).getMessage());
    }
}
