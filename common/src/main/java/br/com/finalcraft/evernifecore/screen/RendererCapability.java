package br.com.finalcraft.evernifecore.screen;

/**
 * What a renderer can do beyond drawing a grid of icons. A screen derives the ones it needs from its elements
 * ({@link Screen#getRequiredCapabilities()}); a renderer that lacks one is never chosen for that screen.
 */
public enum RendererCapability {
    /** Slots the player really puts items into and takes items out of - a {@link StorageBinding}. */
    STORAGE_SLOTS,
    /** Elements placed freely by the layout's regions instead of on a slot grid. */
    FREE_LAYOUT,
    /** A text field drawn inside the window, instead of asking through a {@code PromptChannel}. */
    TEXT_INPUT,
    /** Custom textures for icons and backgrounds. */
    TEXTURES,
    /** Renaming the open window. */
    TITLE_CHANGE
}
