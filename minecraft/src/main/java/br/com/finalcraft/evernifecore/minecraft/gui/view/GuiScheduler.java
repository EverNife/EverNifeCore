package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.minecraft.gui.model.Cancellable;

/**
 * The scheduling seam of the gui framework. Nothing in the gui packages talks to the server
 * scheduler directly, which is what lets a test drive a screen tick by tick and what leaves room for
 * a region-aware scheduler behind the same two methods.
 *
 * <p>Every task handed here runs on the main thread, because everything a gui does with it ends in a
 * write to a {@link GuiSurface}.</p>
 */
public interface GuiScheduler {

    /** Runs {@code task} once, {@code ticks} from now. */
    Cancellable later(long ticks, Runnable task);

    /** Runs {@code task} every {@code ticks}, starting {@code ticks} from now. */
    Cancellable repeat(long ticks, Runnable task);

}
