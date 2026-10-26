package br.com.finalcraft.evernifecore.waypoint.channel;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The path's geometry: one block apart, from the eyes, never past the target, nothing across worlds. */
class ParticlePathWaypointChannelTest {

    private static final WorldLocPos FEET = WorldLocPos.of(0, 64, 0, "world");

    @Test
    void aFarTargetGetsTheConfiguredLengthOneBlockApart() {
        List<WorldLocPos> points = ParticlePathWaypointChannel.path(FEET, WorldLocPos.of(100, 64 + ParticlePathWaypointChannel.EYE_HEIGHT, 0, "world"), 16);

        assertEquals(16, points.size());
        assertEquals(1.0, points.get(0).getX(), 1e-9);
        assertEquals(16.0, points.get(15).getX(), 1e-9);
        assertEquals(64 + ParticlePathWaypointChannel.EYE_HEIGHT, points.get(0).getY(), 1e-9, "level with the eyes");
    }

    @Test
    void aNearTargetIsNeverOvershot() {
        List<WorldLocPos> points = ParticlePathWaypointChannel.path(FEET, WorldLocPos.of(3.5, 64 + ParticlePathWaypointChannel.EYE_HEIGHT, 0, "world"), 16);

        assertEquals(3, points.size());
        assertTrue(points.get(2).getX() <= 3.5);
    }

    @Test
    void withinABlockTheTargetItselfIsThePath() {
        WorldLocPos target = WorldLocPos.of(0.2, 64 + ParticlePathWaypointChannel.EYE_HEIGHT, 0, "world");

        assertEquals(1, ParticlePathWaypointChannel.path(FEET, target, 16).size());
        assertEquals(target, ParticlePathWaypointChannel.path(FEET, target, 16).get(0));
    }

    @Test
    void anotherWorldDrawsNothing() {
        assertTrue(ParticlePathWaypointChannel.path(FEET, WorldLocPos.of(5, 64, 0, "world_nether"), 16).isEmpty());
    }
}
