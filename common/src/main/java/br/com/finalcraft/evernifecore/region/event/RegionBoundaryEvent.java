package br.com.finalcraft.evernifecore.region.event;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.session.MoveCause;
import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * A player is about to cross the border of a region. Posted before the crossing takes effect; the crossing
 * happens only if nobody cancels - and only a {@link MoveCause#isCancellable() cancellable} cause can be cancelled.
 */
public abstract class RegionBoundaryEvent extends RegionEvent implements ECCancellable {

    private final FPlayer player;
    private final @Nullable WorldLocPos from;
    private final @Nullable WorldLocPos to;
    private final MoveCause cause;
    private final @Nullable Object nativeEvent;
    private @Nullable String message;
    private boolean cancelled;

    /** @param nativeEvent the platform's event behind the crossing; null when none caused it (a region created, a quit) */
    protected RegionBoundaryEvent(Region region, FPlayer player, @Nullable WorldLocPos from, @Nullable WorldLocPos to, MoveCause cause,
                                  @Nullable Object nativeEvent) {
        super(region);
        this.nativeEvent = nativeEvent;
        this.player = Objects.requireNonNull(player, "player");
        this.from = from;
        this.to = to;
        this.cause = Objects.requireNonNull(cause, "cause");
    }

    public FPlayer getPlayer() {
        return player;
    }

    /** Where the player was; null when there is no "before" (a join). */
    public @Nullable WorldLocPos getFrom() {
        return from;
    }

    /** Where the player goes; null when there is no "after" (a quit). */
    public @Nullable WorldLocPos getTo() {
        return to;
    }

    public MoveCause getCause() {
        return cause;
    }

    /** The platform's own event behind the crossing as {@code type}; null when there was none or it is of another class. */
    public <E> @Nullable E getNativeEvent(Class<E> type) {
        return type.isInstance(nativeEvent) ? type.cast(nativeEvent) : null;
    }

    /** The message the player sees for this crossing; null for none. */
    public @Nullable String getMessage() {
        return message;
    }

    public void setMessage(@Nullable String message) {
        this.message = message;
    }

    /** Whether {@link #setCancelled(boolean) setCancelled(true)} is allowed: only for MOVE, TELEPORT and VEHICLE. */
    public boolean isCancellable() {
        return cause.isCancellable();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * @throws IllegalStateException when cancelling a crossing that already happened (see {@link #isCancellable()})
     */
    @Override
    public void setCancelled(boolean cancelled) {
        if (cancelled && !isCancellable()) {
            throw new IllegalStateException(getClass().getSimpleName() + " with cause " + cause + " cannot be cancelled: the crossing"
                    + " already happened - ask isCancellable() first, and deny the crossing with deny-entry or deny-exit on MOVE instead.");
        }
        this.cancelled = cancelled;
    }
}
