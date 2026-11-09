package br.com.finalcraft.evernifecore.player.state;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.UUID;

/**
 * A holder's {@link QuitPolicy} came into play on a player who left. Subscribing to this base hears both halves; the nested
 * types are what is fired.
 */
public abstract class HeldStateQuitEvent extends ECEvent implements IECEvent {

    /** Bukkit-only: the native handler list of the whole family, declared here and here only. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(HeldStateQuitEvent.class);
    }

    private final UUID playerId;
    private final String slot;
    private final String holderId;
    private final String before;

    HeldStateQuitEvent(UUID playerId, String slot, String holderId, String before) {
        this.playerId = playerId;
        this.slot = slot;
        this.holderId = holderId;
        this.before = before;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getSlot() {
        return slot;
    }

    public String getHolderId() {
        return holderId;
    }

    public boolean isHeldBy(StateHolder holder) {
        return holderId.equals(holder.getHolderId());
    }

    /** What the player had before the first holder, as {@link HeldPlayerState} encodes it. */
    public String getBefore() {
        return before;
    }

    /**
     * The player of a {@link QuitPolicy#delegate()} holder is quitting and can still be written to: answer now, on this
     * thread. No answer, or a subscriber that throws before answering, gives back.
     */
    public static final class Delegated extends HeldStateQuitEvent {

        private final String current;
        private final FPlayer player;
        private @Nullable Duration answer;

        Delegated(UUID playerId, String slot, String holderId, String before, String current, FPlayer player) {
            super(playerId, slot, holderId, before);
            this.current = current;
            this.player = player;
        }

        /** What the player has in the slot now, encoded as {@link #getBefore()}. */
        public String getCurrent() {
            return current;
        }

        public FPlayer getPlayer() {
            return player;
        }

        /** Same as {@link QuitPolicy#keepFor}, decided now; a hold in memory lands in the player's data from here on. */
        public void keepFor(Duration window) {
            answer = QuitPolicy.requireWindow(window);
        }

        /** Same as {@link QuitPolicy#giveBack()}. */
        public void giveBack() {
            answer = null;
        }

        @Nullable Duration getAnswer() {
            return answer;
        }
    }

    /**
     * A {@link QuitPolicy#keepFor} window ended with the player still away: this holder no longer holds the slot. When it
     * was the last, the next join gives the player's own back. Posted on the main thread, once per holder.
     */
    public static final class Expired extends HeldStateQuitEvent {

        Expired(UUID playerId, String slot, String holderId, String before) {
            super(playerId, slot, holderId, before);
        }
    }
}
