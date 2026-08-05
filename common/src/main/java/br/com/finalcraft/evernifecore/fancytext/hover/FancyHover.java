package br.com.finalcraft.evernifecore.fancytext.hover;

import java.util.function.UnaryOperator;

/**
 * A hover value pluggable into {@link FancyHoverRegistry}: {@link #typeId()} says which
 * {@link FancyHoverType} knows how to render it. Built-ins are {@link TextHover} and
 * {@link ItemHover}; a plugin integrator implements its own to carry whatever payload its custom
 * tooltip needs. {@link #serialize()}/{@link #deserialize(String)} are mandatory so that every
 * implementation - including one with mutable internal state - can be copied without aliasing.
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

    /** This hover's payload collapsed into a single string, undone by {@link #deserialize(String)}. */
    String serialize();

    /** Rebuilds a hover of this same kind from a string previously produced by {@link #serialize()}. */
    FancyHover deserialize(String payload);

    /** An independent hover equal to this one, obtained by round-tripping through serialize/deserialize. */
    default FancyHover copy() {
        return deserialize(serialize());
    }

    /**
     * Applies a placeholder-substitution {@code transform} to this hover's payload, returning the
     * result. Default is an opaque passthrough - a custom type's payload isn't necessarily
     * user-facing text, so a type opts in by overriding this (typically via
     * {@code deserialize(transform.apply(serialize()))}) instead of having it assumed for it.
     */
    default FancyHover replacePayload(UnaryOperator<String> transform) {
        return this;
    }
}
