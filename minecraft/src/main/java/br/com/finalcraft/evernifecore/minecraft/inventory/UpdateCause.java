package br.com.finalcraft.evernifecore.minecraft.inventory;

/**
 * Who is behind a change to an {@link ItemStore}, as its update handlers are told.
 *
 * <p>It is the one thing a handler cannot work out for itself, and the one it usually decides on: a
 * plugin refilling a shop restocks it, a player taking from the same slots is a sale.</p>
 */
public enum UpdateCause {

    /** A viewer's own gesture in an open screen - a click, a drag, a number key. */
    PLAYER,

    /** Code: the plugin that owns the store changing it. */
    PLUGIN

}
