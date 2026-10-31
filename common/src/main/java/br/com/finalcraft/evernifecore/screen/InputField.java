package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.screen.prompt.PromptSpec;
import jakarta.annotation.Nonnull;

import java.util.function.BiConsumer;

/**
 * A field the viewer types a value into. A renderer with {@link RendererCapability#TEXT_INPUT} draws it inside the
 * window; every other renderer turns the click into {@link ClickContext#ask(PromptSpec)} - the screen is set aside, the
 * {@code PromptRegistry} asks through a channel, and the screen comes back with its state.
 */
public class InputField extends Button {

    public InputField(@Nonnull Screen<?> screen, @Nonnull String id, @Nonnull SlotSet slots, @Nonnull Icon template) {
        super(screen, id, slots, template);
        setKind(ElementKind.INPUT);
    }

    /** What to ask and what to do with the answer; no answer (cancelled, timed out, the viewer left) runs nothing. */
    @Nonnull
    public <T> InputField onInput(@Nonnull PromptSpec<T> spec, @Nonnull BiConsumer<ScreenView, T> onAnswer) {
        onClick(context -> context.ask(spec).thenAccept(value -> onAnswer.accept(context.getView(), value)));
        return this;
    }

}
