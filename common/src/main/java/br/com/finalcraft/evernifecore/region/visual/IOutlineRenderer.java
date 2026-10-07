package br.com.finalcraft.evernifecore.region.visual;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;

import java.util.List;

/**
 * How a platform draws one frame of an outline for one player. {@link RegionVisualizer} decides what and when;
 * this only puts the points on screen.
 */
public interface IOutlineRenderer {

    /** The renderer of a platform that cannot draw: every call is a no-op, and nothing upstream has to ask. */
    IOutlineRenderer NONE = (player, points, style) -> {};

    /** Draws one frame of {@code points} for {@code player}; called on the player's world thread, once per visualizer tick. */
    void render(FPlayer player, List<WorldLocPos> points, VisualStyle style);
}
