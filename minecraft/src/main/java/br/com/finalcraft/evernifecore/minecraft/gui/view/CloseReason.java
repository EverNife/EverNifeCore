package br.com.finalcraft.evernifecore.minecraft.gui.view;

/**
 * Why a screen went away. Every one of these runs the gui's {@code onClose}.
 *
 * <p>A screen goes away once, so the first event to reach the framework names the reason and the
 * rest find nothing left to close. On every server build measured - 1.7.10 Crucible through 1.21.1
 * Paper - the platform fires the inventory close BEFORE the quit and before the world change, so a
 * player who leaves or changes world with a screen open is reported as {@link #PLAYER_CLOSED}.
 * <b>Do not branch on {@link #DISCONNECTED} or {@link #WORLD_CHANGED} to run teardown</b>: put the
 * work under {@code PLAYER_CLOSED}, or off the close handler entirely. The two remain because they
 * are what a build that does NOT fire that close would deliver, and that is not something the
 * framework can decide for a server it has never run on.</p>
 */
public enum CloseReason {

    /** The player closed the window, or something closed it for them. */
    PLAYER_CLOSED,
    /** The plugin called {@code close()}. */
    REQUESTED,
    /** The player disconnected, on a build where that outran the inventory close. */
    DISCONNECTED,
    /** The player changed world, on a build where that outran the inventory close. */
    WORLD_CHANGED,
    /** The framework is shutting down, or reloading. */
    SHUTDOWN

}
