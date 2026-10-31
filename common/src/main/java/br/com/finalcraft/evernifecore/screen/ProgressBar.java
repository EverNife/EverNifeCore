package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.placeholder.replacer.Closures;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import jakarta.annotation.Nonnull;

/**
 * A ratio, 0 to 1, drawn as a bar. The patch carries the ratio ({@link ElementPatch#getProgress()}) for a renderer
 * that draws a real bar; the chest draws the icon, whose text reads {@code {progress}} as a whole percentage.
 */
public class ProgressBar extends Button {

    private static final RegexReplacer<ProgressBar> PERCENT = new RegexReplacer<ProgressBar>(Closures.BRACKET)
            .addParser("progress", bar -> Math.round(bar.getValue() * 100));

    private final State<Double> ratio;

    public ProgressBar(@Nonnull Screen<?> screen, @Nonnull String id, @Nonnull SlotSet slots, @Nonnull Icon template,
                       @Nonnull State<Double> ratio) {
        super(screen, id, slots, template);
        setKind(ElementKind.PROGRESS);
        this.ratio = ratio;
        dependsOn(ratio);
        getIcon().addScope(PERCENT, this);
    }

    @Nonnull
    public State<Double> getRatio() {
        return ratio;
    }

    /** The ratio as it reads now, clamped to 0..1; {@code null} reads as 0. */
    public double getValue() {
        Double value = ratio.get();
        return value == null ? 0D : Math.max(0D, Math.min(1D, value));
    }

    @Nonnull
    @Override
    Paint decorate(@Nonnull Paint paint, @Nonnull ScreenView view) {
        return paint.progress(getValue());
    }

}
