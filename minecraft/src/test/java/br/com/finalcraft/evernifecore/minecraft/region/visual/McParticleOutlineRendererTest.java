package br.com.finalcraft.evernifecore.minecraft.region.visual;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.region.visual.VisualStyle;
import br.com.finalcraft.evernifecore.testing.minecraft.Doubles;
import br.com.finalcraft.evernifecore.testing.minecraft.ItemWorld;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Outlines drawn on Bukkit: one particle per point where the particle API exists, a smoke puff where it does not. */
class McParticleOutlineRendererTest {

    private final List<Object[]> particles = new ArrayList<>();
    private final List<Object[]> effects = new ArrayList<>();

    /** A player in {@code world} that records every particle and effect shown to them. */
    private Player viewer() {
        AtomicReference<Player> self = new AtomicReference<>();
        Player player = Doubles.of(Player.class)
                .returning("getName", "Viewer")
                .returning("isOnline", true)
                .returning("getWorld", ItemWorld.world("world"))
                .on("getPlayer", args -> self.get())
                .on("spawnParticle", args -> particles.add(args))
                .on("playEffect", args -> effects.add(args))
                .build();
        self.set(player);
        return player;
    }

    private static List<WorldLocPos> threePointsIn(String world) {
        return Arrays.asList(WorldLocPos.of(0.5, 64, 0.5, world), WorldLocPos.of(1.5, 64, 0.5, world), WorldLocPos.of(2.5, 64, 0.5, world));
    }

    @Test
    void oneParticlePerPointOnModernServers() {
        new McParticleOutlineRenderer(false).render(FCBukkitUtil.adapt(viewer()), threePointsIn("world"), VisualStyle.SELECTION);

        assertEquals(3, particles.size());
        for (Object[] call : particles) {
            assertEquals(Particle.NOTE, call[0]);
            assertEquals(1, call[2], "one particle per point");
        }
        assertEquals(new Location(ItemWorld.world("world"), 1.5, 64, 0.5), particles.get(1)[1]);
        assertTrue(effects.isEmpty());
    }

    @Test
    void smokeOnlyWithoutTheParticleApi() {
        new McParticleOutlineRenderer(true).render(FCBukkitUtil.adapt(viewer()), threePointsIn("world"), VisualStyle.OWN);

        assertEquals(3, effects.size());
        for (Object[] call : effects) {
            assertEquals(Effect.SMOKE, call[1]);
            assertEquals(4, call[2], "4 is the smoke that does not drift");
        }
        assertTrue(particles.isEmpty());
    }

    @Test
    void crucibleOnSevenTenTakesTheParticlePath() {
        assertFalse(McParticleOutlineRenderer.isLegacy(true, name -> true), "1.7.10 Crucible with the backported Particle API");
        assertTrue(McParticleOutlineRenderer.isLegacy(true, name -> name.equals("io.github.crucible.api.CrucibleEventBus")),
                "a Crucible build from before the backport");
        assertTrue(McParticleOutlineRenderer.isLegacy(true, name -> false), "1.8, no Crucible");
        assertFalse(McParticleOutlineRenderer.isLegacy(false, name -> false), "1.9 and up never need the smoke");
    }

    @Test
    void eachStyleHasItsOwnParticle() {
        HashSet<Particle> distinct = new HashSet<>();
        for (VisualStyle style : EnumSet.allOf(VisualStyle.class)) {
            distinct.add(McParticleOutlineRenderer.particleOf(style));
        }

        assertEquals(VisualStyle.values().length, distinct.size());
    }

    @Test
    void aPointInAnotherWorldIsSkipped() {
        List<WorldLocPos> points = new ArrayList<>(threePointsIn("world"));
        points.add(WorldLocPos.of(0.5, 64, 0.5, "nether"));

        new McParticleOutlineRenderer(false).render(FCBukkitUtil.adapt(viewer()), points, VisualStyle.OWN);

        assertEquals(3, particles.size(), "the viewer stands in world; a nether point is not theirs to see");
    }
}
