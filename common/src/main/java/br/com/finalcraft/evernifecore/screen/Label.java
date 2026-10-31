package br.com.finalcraft.evernifecore.screen;

import jakarta.annotation.Nonnull;

/**
 * Text and nothing to click: a renderer that draws by element shows it as text; the chest draws it on the layout's
 * item. It redraws like a {@link Button} does ({@code dependsOn}, {@code every}, states).
 */
public class Label extends Button {

    public Label(@Nonnull Screen<?> screen, @Nonnull String id, @Nonnull SlotSet slots, @Nonnull Icon template) {
        super(screen, id, slots, template);
        setKind(ElementKind.LABEL);
    }

}
