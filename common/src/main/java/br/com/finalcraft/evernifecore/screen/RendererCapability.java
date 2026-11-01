package br.com.finalcraft.evernifecore.screen;

/**
 * What a renderer can do beyond drawing a grid of icons. A screen derives the ones it needs from its elements
 * ({@link Screen#getRequiredCapabilities()}); a renderer that lacks one is never chosen for that screen.
 *
 * <p>Only what a renderer can really serve is here: a capability arrives together with the API that uses it - a text
 * field drawn in the window would bring the way its value comes back, never the flag alone.</p>
 */
public enum RendererCapability {
    /** Slots the player really puts items into and takes items out of - a {@link StorageBinding}. */
    STORAGE_SLOTS,
    /** Custom textures for icons and backgrounds. */
    TEXTURES,
    /** Renaming the open window. */
    TITLE_CHANGE
}
