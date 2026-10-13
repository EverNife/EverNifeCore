package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.Resolution.Step;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.session.RevertStrategy;
import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * One outcome a {@link FlagHandler} hears. On a {@link FlagOutcome#DENIED} movement the handler may pick the way
 * back - {@link #revertTo}, {@link #revertWith} or {@link #allow} - and calling none leaves it to the region's and
 * then the server's settings of the flag.
 */
public final class FlagOutcomeContext {

    private final FlagOutcome outcome;
    private final @Nullable FPlayer player;
    private final Region region;
    private final RegionFlag<?> flag;
    private final @Nullable Object value;
    private final EventSource source;
    private final @Nullable ActionContext action;
    private final Step.Reason reason;
    private final int consecutiveDenials;
    private @Nullable WorldLocPos revertTo;
    private @Nullable RevertStrategy revertWith;
    private boolean allowed;

    /** Internal: built by the region system for each outcome. */
    public FlagOutcomeContext(FlagOutcome outcome, @Nullable FPlayer player, Region region, RegionFlag<?> flag, @Nullable Object value,
                              EventSource source, @Nullable ActionContext action, Step.Reason reason, int consecutiveDenials) {
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        this.player = player;
        this.region = Objects.requireNonNull(region, "region");
        this.flag = Objects.requireNonNull(flag, "flag");
        this.value = value;
        this.source = Objects.requireNonNull(source, "source");
        this.action = action;
        this.reason = Objects.requireNonNull(reason, "reason");
        this.consecutiveDenials = consecutiveDenials;
    }

    public FlagOutcome getOutcome() {
        return outcome;
    }

    /** Null when no player is behind the action (an explosion, a spawn). */
    public @Nullable FPlayer getPlayer() {
        return player;
    }

    /** The region whose value decided - a parent, when the value was inherited. */
    public Region getRegion() {
        return region;
    }

    public RegionFlag<?> getFlag() {
        return flag;
    }

    /** The value the player got; for {@link FlagOutcome#REMOVED} the one that stopped applying. */
    public @Nullable Object getValue() {
        return value;
    }

    public EventSource getSource() {
        return source;
    }

    /** The action asked about; null for a session outcome. */
    public @Nullable ActionContext getAction() {
        return action;
    }

    /** How the value was reached: by a group, the region's own value, a parent's, or the bypass. */
    public Step.Reason getReason() {
        return reason;
    }

    /** How many times in a row this flag denied this player's movement inside the stuck window; 0 outside movement. */
    public int getConsecutiveDenials() {
        return consecutiveDenials;
    }

    /** Sends the denied player to {@code point}. */
    public void revertTo(WorldLocPos point) {
        requireMovementDenial("revertTo");
        this.revertTo = Objects.requireNonNull(point, "point");
    }

    /** Sends the denied player back as {@code strategy} says. */
    public void revertWith(RevertStrategy strategy) {
        requireMovementDenial("revertWith");
        this.revertWith = Objects.requireNonNull(strategy, "strategy");
    }

    /** Lets the denied movement through after all. */
    public void allow() {
        requireMovementDenial("allow");
        this.allowed = true;
    }

    public @Nullable WorldLocPos getRevertTo() {
        return revertTo;
    }

    public @Nullable RevertStrategy getRevertWith() {
        return revertWith;
    }

    public boolean isAllowed() {
        return allowed;
    }

    private void requireMovementDenial(String action) {
        if (outcome != FlagOutcome.DENIED || source != EventSource.MOVE) {
            throw new IllegalStateException(action + "() only steers a denied movement, and this is " + outcome + " on " + source
                    + ": outside MOVE a handler only reacts - the denial stands; give the player a group to lift it.");
        }
    }
}
