package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.minecraft.region.visual.McParticleOutlineRenderer;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.region.visual.IOutlineRenderer;
import br.com.finalcraft.evernifecore.region.visual.VisualStyle;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The region outline drawn for a real player: the platform hands out the particle renderer, and every style draws
 * the corners of a box around the player without throwing - smoke below 1.9 (unless the server backports the 1.9
 * particle API, as Crucible does), {@code spawnParticle} from there on.
 */
public final class ParticlesSuite implements ConformanceSuite {

    /** The particle API the renderer prefers; below it, a server without the backport gets smoke. */
    private static final MCDetailedVersion PARTICLE_API_SINCE = MCDetailedVersion.v1_9_R1;

    @Override
    public String getId() {
        return "particles";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                ConformanceCase.of("renderer", () -> {
                    IOutlineRenderer renderer = EverNifeCore.getPlatform().getOutlineRenderer();
                    return Verdict.of(renderer instanceof McParticleOutlineRenderer, "renderer=" + renderer.getClass().getName());
                }),
                PlayerCases.of("render-every-style", ctx, player -> {
                    Location at = player.getLocation();
                    List<WorldLocPos> corners = new ArrayList<>();
                    for (int dx = -2; dx <= 2; dx += 4) {
                        for (int dz = -2; dz <= 2; dz += 4) {
                            corners.add(WorldLocPos.of(at.getX() + dx, at.getY() + 1, at.getZ() + dz, at.getWorld().getName()));
                        }
                    }
                    IOutlineRenderer renderer = EverNifeCore.getPlatform().getOutlineRenderer();
                    for (VisualStyle style : VisualStyle.values()) {
                        renderer.render(FCBukkitUtil.adapt(player), corners, style);
                    }
                    return Verdict.pass("styles=" + Arrays.toString(VisualStyle.values()) + " points=" + corners.size()
                            + " particleApi=" + (MCVersion.isHigherEquals(PARTICLE_API_SINCE) ? "spawnParticle" : "smoke unless backported")
                            + " since=" + PARTICLE_API_SINCE.getLowestRelease());
                }));
    }
}
