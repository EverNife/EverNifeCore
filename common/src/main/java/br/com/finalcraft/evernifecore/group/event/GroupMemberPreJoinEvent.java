package br.com.finalcraft.evernifecore.group.event;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.JoinCause;
import jakarta.annotation.Nullable;

import java.util.UUID;

/**
 * A player is about to become a member. Cancelling it keeps them out: {@code add} answers false and {@code join}
 * answers a denied result whose explanation is {@link #setDenyReason the reason} set here.
 */
public class GroupMemberPreJoinEvent extends GroupEvent implements ECCancellable {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(GroupMemberPreJoinEvent.class);
    }

    private final UUID player;
    private final @Nullable FPlayer fPlayer;
    private final JoinCause cause;
    private volatile boolean cancelled;
    private volatile @Nullable FancyText denyReason;

    public GroupMemberPreJoinEvent(Group group, UUID player, @Nullable FPlayer fPlayer, JoinCause cause) {
        super(group);
        this.player = player;
        this.fPlayer = fPlayer;
        this.cause = cause;
    }

    public UUID getPlayer() {
        return player;
    }

    /** The player when the join came through them (join, accept, a roster); null for the owner's {@code add}. */
    public @Nullable FPlayer getFPlayer() {
        return fPlayer;
    }

    public JoinCause getCause() {
        return cause;
    }

    /** What the player reads when this cancellation denies their join; null for the default text. */
    public @Nullable FancyText getDenyReason() {
        return denyReason;
    }

    /** Cancels and says why. */
    public void setDenyReason(@Nullable FancyText reason) {
        this.denyReason = reason;
        this.cancelled = true;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
