package br.com.finalcraft.evernifecore.fancytext.hover;

import net.kyori.adventure.text.event.HoverEvent;

import java.util.function.Function;

/**
 * A hover kind {@link FancyHoverRegistry} knows how to render: the {@link #typeId()} it answers to,
 * how to build the Adventure {@link HoverEvent}, and optionally how a value of this type degrades
 * into a different {@link FancyHover} when the platform does not support {@link #typeId()} at all
 * (see {@code br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformChatAdapter#supportsHover(String)}).
 */
public final class FancyHoverType<H extends FancyHover> {

    private final String typeId;
    private final Function<H, HoverEvent<?>> render;
    private final Function<H, FancyHover> degrade;

    private FancyHoverType(String typeId, Function<H, HoverEvent<?>> render, Function<H, FancyHover> degrade) {
        this.typeId = typeId;
        this.render = render;
        this.degrade = degrade;
    }

    public static <H extends FancyHover> FancyHoverType<H> of(String typeId, Function<H, HoverEvent<?>> render) {
        return new FancyHoverType<>(typeId, render, null);
    }

    /**
     * Returns a copy of this type carrying a degrade function: how a value of this type becomes a
     * different {@link FancyHover} when the platform reports {@code typeId()} unsupported. Omitted
     * (or returning {@code null} for a given value) means the render omits the hover entirely rather
     * than guessing at a fallback.
     */
    public FancyHoverType<H> withDegrade(Function<H, FancyHover> degrade) {
        return new FancyHoverType<>(typeId, render, degrade);
    }

    public String typeId() {
        return typeId;
    }

    HoverEvent<?> render(H hover) {
        return render.apply(hover);
    }

    FancyHover degrade(H hover) {
        return degrade == null ? null : degrade.apply(hover);
    }
}
