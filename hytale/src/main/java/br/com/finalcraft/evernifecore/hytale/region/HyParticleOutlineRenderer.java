package br.com.finalcraft.evernifecore.hytale.region;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.visual.IOutlineRenderer;
import br.com.finalcraft.evernifecore.region.visual.VisualStyle;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.List;

/**
 * Draws region outlines with one of the game's particle systems per point, a different one per {@link VisualStyle},
 * sent to the viewer alone. Called on the viewer's world thread once per visualizer tick.
 */
public final class HyParticleOutlineRenderer implements IOutlineRenderer {

    @Override
    public void render(FPlayer player, List<WorldLocPos> points, VisualStyle style) {
        if (!(player instanceof HytaleFPlayer hytale)) {
            return;
        }
        PlayerRef playerRef = hytale.getPlayerRef();
        Ref<EntityStore> viewer = playerRef == null ? null : playerRef.getReference();
        if (viewer == null || !viewer.isValid()) {
            return;   //he left between the visualizer's tick and this frame
        }
        Store<EntityStore> store = viewer.getStore();
        String world = store.getExternalData().getWorld().getName();
        List<Ref<EntityStore>> only = Collections.singletonList(viewer);
        String particle = particleOf(style);
        for (WorldLocPos point : points) {
            if (world.equals(point.getWorldName())) {
                ParticleUtil.spawnParticleEffect(particle, new Vector3d(point.getX(), point.getY(), point.getZ()), only, store);
            }
        }
    }

    /** One particle system of the 0.7.1 assets per style. */
    static String particleOf(VisualStyle style) {
        return switch (style) {
            case OTHER_OWNER -> "Torch_Fire";
            case INTERSECTION -> "Torch_Fire_Green";
            case SELECTION -> "Block_Gem_Sparks";
            case OWN -> "Dust_Sparkles_Fine";
        };
    }
}
