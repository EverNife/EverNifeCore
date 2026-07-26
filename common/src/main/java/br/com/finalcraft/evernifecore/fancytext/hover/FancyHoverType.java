package br.com.finalcraft.evernifecore.fancytext.hover;

import jakarta.annotation.Nullable;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.function.Function;

/**
 * A hover kind {@link FancyHoverRegistry} knows how to render: the {@link #typeId()} it answers to,
 * how to build the Adventure {@link HoverEvent}, optionally how a value of this type degrades into a
 * different {@link FancyHover} when the platform does not support {@link #typeId()} at all (see
 * {@code br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformChatAdapter#supportsHover(String)}),
 * and optionally how it persists to and from a single on-disk string ({@link #withCodec}).
 */
public final class FancyHoverType<H extends FancyHover> {

    private final String typeId;
    private final Function<H, HoverEvent<?>> render;
    private final Function<H, FancyHover> degrade;
    private final @Nullable Function<H, String> encode;
    private final @Nullable Function<String, H> decode;

    private FancyHoverType(String typeId, Function<H, HoverEvent<?>> render, Function<H, FancyHover> degrade,
                          @Nullable Function<H, String> encode, @Nullable Function<String, H> decode) {
        this.typeId = typeId;
        this.render = render;
        this.degrade = degrade;
        this.encode = encode;
        this.decode = decode;
    }

    public static <H extends FancyHover> FancyHoverType<H> of(String typeId, Function<H, HoverEvent<?>> render) {
        return new FancyHoverType<>(typeId, render, null, null, null);
    }

    /**
     * Returns a copy of this type carrying a degrade function: how a value of this type becomes a
     * different {@link FancyHover} when the platform reports {@code typeId()} unsupported. Omitted
     * (or returning {@code null} for a given value) means the render omits the hover entirely rather
     * than guessing at a fallback.
     */
    public FancyHoverType<H> withDegrade(Function<H, FancyHover> degrade) {
        return new FancyHoverType<>(typeId, render, degrade, encode, decode);
    }

    /**
     * Opts this type into on-disk persistence: the config codec only saves and loads a value of this
     * type once BOTH directions are supplied. A type that never calls this cannot be reconstructed from
     * a file - the codec degrades it to a plain text tooltip when the value has a legacy string form,
     * and otherwise writes the type name alone so that the loss is reported instead of hidden.
     */
    public FancyHoverType<H> withCodec(Function<H, String> encode, Function<String, H> decode) {
        return new FancyHoverType<>(typeId, render, degrade, encode, decode);
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

    boolean isCodecAware() {
        return encode != null && decode != null;
    }

    String encode(H hover) {
        return encode.apply(hover);
    }

    H decode(String payload) {
        return decode.apply(payload);
    }
}
