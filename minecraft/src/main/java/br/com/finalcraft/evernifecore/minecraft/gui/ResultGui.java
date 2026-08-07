package br.com.finalcraft.evernifecore.minecraft.gui;

import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutBase;
import br.com.finalcraft.evernifecore.minecraft.gui.model.GuiType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * A screen that answers a question: it declares the type of the value its {@code ctx.back(value)}
 * hands to whoever opened it.
 *
 * <p>That declaration is what types the future - {@code ctx.open(screen)} answers a
 * {@code CompletableFuture<R>} here and a {@code CompletableFuture<Object>} for a plain
 * {@link Gui} - so the answer arrives already cast:</p>
 *
 * <pre>{@code
 * public class SellGui extends ResultGui<Confirmation, SellLayout> { ... }
 *
 * ctx.open(new SellGui(item))
 *    .thenAccept(answer -> { if (answer == Confirmation.YES) sell(); });
 * }</pre>
 *
 * @param <R> what this screen hands back
 * @param <L> the layout it was built from, exactly as on {@link Gui}
 */
public abstract class ResultGui<R, L extends LayoutBase> extends Gui<L> {

    protected ResultGui(@Nonnull GuiType type, int rows, @Nullable L layout) {
        super(type, rows, layout);
    }

    /** Sized, titled and decorated by {@code layout} - the {@link Gui#of(LayoutBase)} of this base. */
    protected ResultGui(@Nonnull L layout) {
        super(layout.getType(), layout.getRows(), layout);
    }

}
