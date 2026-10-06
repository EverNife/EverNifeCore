package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.session.RevertStrategy;
import jakarta.annotation.Nullable;

import java.time.Duration;

/**
 * How a flag that denies movement sends the player back: the strategy, and the way out for a player who keeps
 * being denied - after {@link #getStuckAfter()} denials inside {@link #getStuckWindow()} the player is moved further
 * back along the way they came, and once that runs out, to {@link #getStuckFallback()}.
 */
public class MoveDenySettings extends FlagSettings {

    private RevertStrategy revert = RevertStrategy.lastValid();
    private int stuckAfter = 12;
    private Duration stuckWindow = Duration.ofSeconds(1);
    private @Nullable WorldLocPos stuckFallback;

    public RevertStrategy getRevert() {
        return revert;
    }

    public void setRevert(RevertStrategy revert) {
        this.revert = revert;
    }

    public int getStuckAfter() {
        return stuckAfter;
    }

    public void setStuckAfter(int stuckAfter) {
        this.stuckAfter = stuckAfter;
    }

    public Duration getStuckWindow() {
        return stuckWindow;
    }

    public void setStuckWindow(Duration stuckWindow) {
        this.stuckWindow = stuckWindow;
    }

    /** Where a stuck player goes once the trail runs out; null keeps reverting to the oldest block of the trail. */
    public @Nullable WorldLocPos getStuckFallback() {
        return stuckFallback;
    }

    public void setStuckFallback(@Nullable WorldLocPos stuckFallback) {
        this.stuckFallback = stuckFallback;
    }
}
