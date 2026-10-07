package br.com.finalcraft.evernifecore.region.visual;

/** What an outline means to the viewer; a renderer picks its colour or particle from it. */
public enum VisualStyle {
    /** A region of the plugin that asked to show it. */
    OWN,
    /** A region of some other owner. */
    OTHER_OWNER,
    /** Where two regions overlap. */
    INTERSECTION,
    /** A selection still being made. */
    SELECTION
}
