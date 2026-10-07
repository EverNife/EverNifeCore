package br.com.finalcraft.evernifecore.minecraft.region.visual;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFPlayer;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.region.visual.IOutlineRenderer;
import br.com.finalcraft.evernifecore.region.visual.VisualStyle;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Predicate;

/**
 * Draws region outlines with one particle per point, a different particle per {@link VisualStyle}. A server without the
 * particle API (1.8, or a Crucible older than its 1.9 backport) gets a plain smoke puff per point instead, blind to the
 * style. Called on the world's thread once per visualizer tick, so it schedules nothing of its own.
 */
public final class McParticleOutlineRenderer implements IOutlineRenderer {

    private static final String CRUCIBLE = "io.github.crucible.api.CrucibleEventBus";

    private final boolean legacy;

    public McParticleOutlineRenderer() {
        this(isLegacy(MCVersion.isLower(MCDetailedVersion.v1_9_R1), FCReflectionUtil.getClasses()::isClassLoaded));
    }

    /** @param legacy no particle API on this server: every style is the same smoke puff */
    McParticleOutlineRenderer(boolean legacy) {
        this.legacy = legacy;
    }

    /** Crucible backports the 1.9 particle API to 1.7.10; a Crucible build older than the backport lacks the class and gets the smoke. */
    static boolean isLegacy(boolean belowOnePointNine, Predicate<String> loaded) {
        return belowOnePointNine && !(loaded.test(CRUCIBLE) && loaded.test("org.bukkit.Particle"));
    }

    @Override
    public void render(FPlayer player, List<WorldLocPos> points, VisualStyle style) {
        if (!(player instanceof MinecraftFPlayer)) {
            return;
        }
        Player bukkit = ((MinecraftFPlayer<?>) player).getPlayer();
        World world = bukkit.getWorld();
        Particle particle = legacy ? null : particleOf(style);
        for (WorldLocPos point : points) {
            if (!world.getName().equals(point.getWorldName())) {
                continue;
            }
            Location at = new Location(world, point.getX(), point.getY(), point.getZ());
            if (legacy) {
                bukkit.playEffect(at, Effect.SMOKE, 4);   //4 = no drift: the smoke sits on the point
            } else {
                bukkit.spawnParticle(particle, at, 1);
            }
        }
    }

    /** One particle per style, each named the same from 1.9 to today. */
    static Particle particleOf(VisualStyle style) {
        switch (style) {
            case OTHER_OWNER:
                return Particle.FLAME;
            case INTERSECTION:
                return Particle.CRIT;
            case SELECTION:
                return Particle.NOTE;
            case OWN:
            default:
                return Particle.END_ROD;
        }
    }
}
