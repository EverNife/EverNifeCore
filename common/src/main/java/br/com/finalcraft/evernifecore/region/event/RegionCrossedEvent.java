package br.com.finalcraft.evernifecore.region.event;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.session.MoveCause;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A player crossed one or more region borders and the crossing took effect: the session already counts them inside
 * {@link #getEntered()} and outside {@link #getLeft()}. Posted after the cancellable {@link RegionLeaveEvent}s and
 * {@link RegionEnterEvent}s of that crossing were accepted, on the thread of the world the player is in; never
 * cancellable. A cancelled crossing posts nothing here.
 */
public class RegionCrossedEvent extends ECEvent implements IECEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(RegionCrossedEvent.class);
    }

    private final FPlayer player;
    private final @Nullable WorldLocPos from;
    private final @Nullable WorldLocPos to;
    private final MoveCause cause;
    private final List<Region> left;
    private final List<Region> entered;
    private final @Nullable Object nativeEvent;

    /** @param nativeEvent the platform's event behind the crossing; null when none caused it (a join, a quit, a region created) */
    public RegionCrossedEvent(FPlayer player, @Nullable WorldLocPos from, @Nullable WorldLocPos to, MoveCause cause,
                              List<Region> left, List<Region> entered, @Nullable Object nativeEvent) {
        this.player = Objects.requireNonNull(player, "player");
        this.from = from;
        this.to = to;
        this.cause = Objects.requireNonNull(cause, "cause");
        this.left = Collections.unmodifiableList(new ArrayList<>(left));
        this.entered = Collections.unmodifiableList(new ArrayList<>(entered));
        this.nativeEvent = nativeEvent;
    }

    public FPlayer getPlayer() {
        return player;
    }

    /** Where the player was; null on a join. */
    public @Nullable WorldLocPos getFrom() {
        return from;
    }

    /** Where the player is now; null on a quit. */
    public @Nullable WorldLocPos getTo() {
        return to;
    }

    public MoveCause getCause() {
        return cause;
    }

    /** The regions the player left, in the order their leave events were posted. Unmodifiable. */
    public List<Region> getLeft() {
        return left;
    }

    /** The regions the player entered, in the order their enter events were posted. Unmodifiable. */
    public List<Region> getEntered() {
        return entered;
    }

    /** The platform's own event behind the crossing as {@code type}; null when there was none or it is of another class. */
    public <E> @Nullable E getNativeEvent(Class<E> type) {
        return type.isInstance(nativeEvent) ? type.cast(nativeEvent) : null;
    }
}
