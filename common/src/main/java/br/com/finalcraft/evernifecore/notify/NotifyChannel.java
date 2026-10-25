package br.com.finalcraft.evernifecore.notify;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;

import java.util.UUID;

/**
 * One way the {@link Notifier} can deliver: chat, action bar, title... A plugin adds its own through
 * {@link Notifier#register}; a chain names it by its bare name or its {@code owner:name} key.
 *
 * <p>A channel that keeps per-player state (a queue, a shown bar) captures the platform's {@link IHudAccess} when it
 * delivers and does every later write - a tick, a timed hide - against that one, never against whatever platform is
 * installed by then.</p>
 */
public interface NotifyChannel {

    /** {@code owner:name}, e.g. {@code evernifecore:action-bar}; the name part is what a chain writes. */
    String getKey();

    /** Whether this platform can deliver through this channel now. Never throws. */
    boolean isSupported();

    /** Delivers {@code notification} to {@code player}. Thread: any; the channel hops when it must. */
    void deliver(FPlayer player, Notification notification);

    /** Drops what this channel shows {@code player} under {@code id} (a boss bar, a tracker, a queued action bar). Thread: any. */
    default void clear(FPlayer player, String id) {
    }

    /** The player left: forget whatever this channel kept for them. Thread: any. */
    default void onQuit(UUID player) {
    }

    /**
     * Forgets every player's state and stops every tick, waiting for one already running to finish - after it
     * returns, this channel writes nothing more until the next {@link #deliver}. Thread: any.
     */
    default void clearAll() {
    }
}
