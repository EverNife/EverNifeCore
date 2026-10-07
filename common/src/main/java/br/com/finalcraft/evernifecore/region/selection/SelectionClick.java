package br.com.finalcraft.evernifecore.region.selection;

/** The three buttons of the selection tool, as the platform's listener read them. */
public enum SelectionClick {
    /** Adds the clicked block as the next point. */
    LEFT,
    /** Removes the last point. */
    RIGHT,
    /** Sneaking right click: confirms the selection. */
    SNEAK_RIGHT
}
