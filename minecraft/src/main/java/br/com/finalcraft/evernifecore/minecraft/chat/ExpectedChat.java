package br.com.finalcraft.evernifecore.minecraft.chat;

import org.bukkit.entity.Player;

import jakarta.annotation.Nullable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One outstanding "wait for this player to type something" registration. It settles exactly once,
 * by being consumed, cancelled or expiring, and stays around as a handle the caller can query or
 * call off early.
 */
public class ExpectedChat {

    private final Player player;
    private final IChatAction chatAction;
    private final long expiration;
    private final @Nullable Runnable onExpireAction;
    private final @Nullable Runnable onPlayerQuitAction;
    /** The scheduled expiration task, so settling early can call it off. */
    private final AtomicReference<ScheduledFuture<?>> future;

    private final long creationTime = System.currentTimeMillis();

    private boolean cancelExpirationActionOnPlayerQuit = true;
    private boolean wasConsumed = false;
    private boolean wasCancelled = false;

    public ExpectedChat(Player player, IChatAction chatAction, long expiration, @Nullable Runnable onExpireAction, @Nullable Runnable onPlayerQuitAction, AtomicReference<ScheduledFuture<?>> future) {
        this.player = player;
        this.chatAction = chatAction;
        this.expiration = expiration;
        this.onExpireAction = onExpireAction;
        this.onPlayerQuitAction = onPlayerQuitAction;
        this.future = future;
    }

    public Player getPlayer() {
        return player;
    }

    public IChatAction getChatAction() {
        return chatAction;
    }

    public long getExpiration() {
        return expiration;
    }

    public @Nullable Runnable getOnExpireAction() {
        return onExpireAction;
    }

    public @Nullable Runnable getOnPlayerQuitAction() {
        return onPlayerQuitAction;
    }

    public AtomicReference<ScheduledFuture<?>> getFuture() {
        return future;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public boolean isCancelExpirationActionOnPlayerQuit() {
        return cancelExpirationActionOnPlayerQuit;
    }

    public ExpectedChat setCancelExpirationActionOnPlayerQuit(boolean cancelExpirationActionOnPlayerQuit) {
        this.cancelExpirationActionOnPlayerQuit = cancelExpirationActionOnPlayerQuit;
        return this;
    }

    public boolean wasConsumed() {
        return wasConsumed;
    }

    public boolean wasCancelled() {
        return wasCancelled;
    }

    public ExpectedChat setConsumed(boolean consumed) {
        this.wasConsumed = consumed;
        return this;
    }

    /** @return false if it had already been cancelled. */
    public boolean cancel() {
        if (wasCancelled) {
            return false;
        }

        this.wasCancelled = true;

        if (isCancelExpirationActionOnPlayerQuit() && getFuture().get() != null) {
            getFuture().get().cancel(false);
        }

        return true;
    }

    public boolean hasExpired() {
        return expiration > 0 && System.currentTimeMillis() > (creationTime + expiration);
    }

    public boolean isWaitingForResponse() {
        return !wasCancelled && !wasConsumed && !hasExpired();
    }

}
