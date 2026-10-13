package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.session.RevertStrategy;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.everyconfig.annotation.Comment;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;

import java.time.Duration;

/**
 * How a flag that denies movement sends the player back: the strategy, and the way out for a player who keeps
 * being denied - after {@link #getStuckAfter()} denials inside {@link #getStuckWindow()} the player is moved further
 * back along the way they came, and once that runs out, to {@link #getStuckFallback()}.
 */
public class MoveDenySettings extends FlagSettings {

    @Comment({"Where a denied player is sent back to: last-valid (the last block they were allowed on),",
            "trail:<n> (n blocks further back along their way), allow-after:<n> (deny, but let the n-th denial in a row through),",
            "fixed:<x>,<y>,<z>[,<world>], spawn (the server spawn) or worldspawn (the spawn of their world)."})
    private RevertStrategy revert = RevertStrategy.lastValid();

    @Comment({"After this many denials inside stuckWindow the player counts as stuck and is sent one block further back",
            "along their way for every denial past it - lower it if players get trapped against a border."})
    private int stuckAfter = 12;

    @Comment("The window stuckAfter counts denials in.")
    @JsonProperty
    private FCTimeFrame stuckWindow = FCTimeFrame.of(Duration.ofSeconds(1));

    @Comment({"Where a stuck player goes once the way back runs out, as a position with its world;",
            "null keeps sending them to the oldest block of their way back."})
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

    @JsonIgnore
    public Duration getStuckWindow() {
        return stuckWindow.toDuration();
    }

    @JsonIgnore
    public void setStuckWindow(Duration stuckWindow) {
        this.stuckWindow = FCTimeFrame.of(stuckWindow);
    }

    /** Where a stuck player goes once the trail runs out; null keeps reverting to the oldest block of the trail. */
    public @Nullable WorldLocPos getStuckFallback() {
        return stuckFallback;
    }

    public void setStuckFallback(@Nullable WorldLocPos stuckFallback) {
        this.stuckFallback = stuckFallback;
    }
}
