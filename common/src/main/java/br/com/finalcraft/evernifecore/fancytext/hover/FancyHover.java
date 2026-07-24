package br.com.finalcraft.evernifecore.fancytext.hover;

/**
 * A hover value pluggable into {@link FancyHoverRegistry}: {@link #typeId()} says which
 * {@link FancyHoverType} knows how to render it. Built-ins are {@link TextHover} and
 * {@link ItemHover}; a plugin integrator implements its own to carry whatever payload its custom
 * tooltip needs.
 */
public interface FancyHover {

    /** The id under which this value's {@link FancyHoverType} is registered in {@link FancyHoverRegistry}. */
    String typeId();

    /**
     * The single-string form the legacy {@code getHoverText()} accessor understands, or {@code null}
     * if this value has none - true of any custom type, since it predates the registry and has no
     * idea how to collapse an arbitrary payload into one string. Built-ins override this to reproduce
     * their historical on-the-wire string exactly.
     */
    default String toLegacyPayload() {
        return null;
    }
}
