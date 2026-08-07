package br.com.finalcraft.evernifecore.minecraft.gui;

import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutBase;
import br.com.finalcraft.evernifecore.minecraft.gui.model.GuiType;
import br.com.finalcraft.evernifecore.minecraft.gui.view.ClickContext;
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

    /**
     * Leaves this screen answering {@code value}. The same as {@code ctx.back(value)}, except that
     * {@code back} takes an {@code Object} - here the compiler checks the answer against {@code R},
     * which is the whole reason this screen declared one.
     */
    protected void answer(@Nonnull ClickContext ctx, @Nullable R value) {
        ctx.back(value);
    }

}
