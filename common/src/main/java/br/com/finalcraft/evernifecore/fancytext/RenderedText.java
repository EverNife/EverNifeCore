package br.com.finalcraft.evernifecore.fancytext;

import net.kyori.adventure.text.Component;

import java.util.Objects;

/**
 * What one render produced: the component, and the colour the text ended on - which is what the
 * next piece of a chain has to start with, because Adventure siblings do not inherit colour.
 *
 * <p>Carrying the trailing colour out as a value is what keeps rendering pure: the same instance can
 * be rendered from two threads at once, for two different recipients, without either render seeing
 * the other's colour.</p>
 */
public final class RenderedText {

    private final Component component;
    private final String trailingColor;

    public RenderedText(Component component, String trailingColor) {
        this.component = component;
        this.trailingColor = trailingColor == null ? "" : trailingColor;
    }

    public Component getComponent() {
        return component;
    }

    /** The legacy colour/format codes still active where this render ended, {@code ""} if none. */
    public String getTrailingColor() {
        return trailingColor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RenderedText other = (RenderedText) o;
        return Objects.equals(component, other.component) && trailingColor.equals(other.trailingColor);
    }

    @Override
    public int hashCode() {
        return 31 * (component == null ? 0 : component.hashCode()) + trailingColor.hashCode();
    }
}
