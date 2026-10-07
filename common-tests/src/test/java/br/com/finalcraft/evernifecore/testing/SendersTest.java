package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A fake player stands somewhere only once told to, and a teleport moves it. */
class SendersTest {

    @Test
    void atGivesTheWorldAndTheCoordinates() {
        FLocation location = Senders.player("Steve").at("world", 1.5, 64, -3).getLocation();

        assertEquals("world", location.getWorldLocPos().getWorldName());
        assertEquals(1.5, location.getWorldLocPos().getX());
        assertEquals(64, location.getWorldLocPos().getY());
        assertEquals(-3, location.getWorldLocPos().getZ());
    }

    @Test
    void withoutAtTheLocationIsNull() {
        assertNull(Senders.player("Steve").getLocation());
    }

    @Test
    void teleportToMovesThePlayerAndRecordsIt() {
        TestFPlayerSender steve = Senders.player("Steve").at("world", 0, 64, 0);
        FLocation target = new FLocation(WorldLocPos.of(10, 70, 10, "nether"), null);

        assertTrue(steve.teleportTo(target));

        assertSame(target, steve.getLocation());
        assertEquals(1, steve.getTeleports().size());
        assertSame(target, steve.getTeleports().get(0));
    }
}
