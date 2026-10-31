package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.action.EventSource;
import jakarta.annotation.Nonnull;

import java.util.Set;

/**
 * Something a screen draws and a renderer addresses by id: a {@link Button}, a {@link Label}, a {@link PagedList}, an
 * {@link InputField}, a {@link ProgressBar}, a dialogue {@link Choice}. The id is stable - the layout field name, or
 * {@code Button.id("...")} - and unique per screen; a renderer that draws by element patches by it.
 */
public interface Element {

    /** Stable and unique within the screen. */
    @Nonnull
    String getId();

    @Nonnull
    ElementKind getKind();

    /** Where a grid renderer puts it. */
    @Nonnull
    SlotSet getSlots();

    /**
     * The event sources after which what it shows may change - the union of what it {@code dependsOn}. A view holds
     * each of them open ({@code UsageHold}) for as long as it is open, and redraws the element when one fires for the
     * viewer.
     */
    @Nonnull
    Set<EventSource> getNeeds();

}
