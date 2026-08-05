package br.com.finalcraft.evernifecore.minecraft.gui.view;

/** Why a screen went away. Every one of these runs the gui's {@code onClose}. */
public enum CloseReason {

    /** The player closed the window, or something closed it for them. */
    PLAYER_CLOSED,
    /** The plugin called {@code close()}. */
    REQUESTED,
    /** The player disconnected. */
    DISCONNECTED,
    /** The player changed world, which drops the open container. */
    WORLD_CHANGED,
    /** The framework is shutting down, or reloading. */
    SHUTDOWN

}
