package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.screen.prompt.PromptParser;
import br.com.finalcraft.evernifecore.screen.prompt.PromptSpec;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * One click, as the handler of a button sees it: who clicked, which gesture, what it would do with items, on which
 * element, and the moves a handler may make from here - close, refresh, open another screen, go back, ask for a value.
 *
 * <p>A context ages: once the viewer clicks again, or the view closes, it is no longer {@link #isAlive() alive} and its
 * moves do nothing - a handler still waiting on a future cannot act on a screen that moved on.</p>
 */
public final class ClickContext {

    private final ScreenView view;
    private final long token;
    private final int slot;
    private final ClickGesture gesture;
    private final ClickKind kind;
    private final ItemSpec cursor;
    private final Paint paint;
    private final Icon icon;
    private boolean moveAllowed = false;

    ClickContext(ScreenView view, long token, int slot, ClickGesture gesture, ClickKind kind, @Nullable ItemSpec cursor,
                 @Nullable Paint paint, @Nullable Icon icon) {
        this.view = view;
        this.token = token;
        this.slot = slot;
        this.gesture = gesture;
        this.kind = kind;
        this.cursor = cursor;
        this.paint = paint;
        this.icon = icon;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Reading
    // -----------------------------------------------------------------------------------------------------------------

    /** The player who clicked, or {@code null} once the view has been released. */
    @Nullable
    public FPlayer getViewer() {
        return view.getViewer();
    }

    @Nonnull
    public Screen<?> getScreen() {
        return view.getScreen();
    }

    @Nonnull
    public ScreenView getView() {
        return view;
    }

    public int getSlot() {
        return slot;
    }

    @Nonnull
    public ClickGesture getGesture() {
        return gesture;
    }

    @Nonnull
    public ClickKind getKind() {
        return kind;
    }

    /** What the viewer was holding on the cursor, or {@code null}. */
    @Nullable
    public ItemSpec getCursor() {
        return cursor;
    }

    /** The element clicked, or {@code null} for a decoration. */
    @Nullable
    public Element getElement() {
        return paint == null ? null : paint.getElement();
    }

    /** The id of what was clicked: the element's, or the decoration's. */
    @Nullable
    public String getElementId() {
        return paint == null ? null : paint.getElementId();
    }

    /** The icon drawn where the click landed. */
    @Nullable
    public Icon getIcon() {
        return icon;
    }

    public boolean isAlive() {
        return view.isTokenAlive(token);
    }

    /** A context for the logic this click runs: the screen's owner as the caller, the viewer, this view as attribute. */
    @Nonnull
    public ExecutionContext.Builder newExecutionContext() {
        return view.newExecutionContext().attribute(ClickContext.class.getName(), this);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Acting
    // -----------------------------------------------------------------------------------------------------------------

    /** Lets the item move this gesture would make go through, on a slot whose policy denies it. */
    public void allowMove() {
        if (isAlive()) {
            this.moveAllowed = true;
        }
    }

    boolean isMoveAllowed() {
        return moveAllowed;
    }

    /** Closes the screen on the next tick. The close handler can tell it was this element that asked. */
    public void close() {
        if (isAlive()) {
            view.markClosedBy(icon);
            view.closeNextTick();
        }
    }

    public void refresh() {
        if (isAlive()) {
            view.refresh();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Navigating
    // -----------------------------------------------------------------------------------------------------------------

    /** Opens {@code screen} on top of this one, which waits with its state; completes with what it hands back. */
    @Nonnull
    public CompletableFuture<Object> open(@Nonnull Screen<?> screen) {
        return ScreenNavigation.open(view, screen);
    }

    /** {@link #open(Screen)} with the answer typed: a value of another type completes the future exceptionally. */
    @Nonnull
    public <R> CompletableFuture<R> open(@Nonnull Screen<?> screen, @Nonnull Class<R> answer) {
        return Screens.typed(open(screen), answer, "the click on " + view.getScreen().describe());
    }

    /** Drops this screen and gives the viewer back the one underneath; at the bottom, closes. */
    public void back() {
        back(null);
    }

    /** {@link #back()} handing {@code value} to whoever opened this screen. */
    public void back(@Nullable Object value) {
        view.markClosedBy(icon);
        ScreenNavigation.back(view, value);
    }

    /** Puts {@code screen} where this one was; a later back() goes to the screen underneath. */
    public void replace(@Nonnull Screen<?> screen) {
        ScreenNavigation.replace(view, screen);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Asking for a value no button can express
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Asks through the channel {@code PromptRegistry} picks ({@link PromptSpec#prefer} steers it): the screen is set aside
     * and comes back with its state. Completes with the value, or is cancelled when no value came (the viewer cancelled,
     * it timed out, they left). The whole {@code PromptResult} comes from {@code PromptRegistry.get().ask(view, spec)}.
     */
    @Nonnull
    public <T> CompletableFuture<T> ask(@Nonnull PromptSpec<T> spec) {
        return view.ask(spec);
    }

    /** {@link #ask(PromptSpec)} of a plain question, with its answer handed to {@code onAnswer}. */
    @Nonnull
    public <T> CompletableFuture<T> ask(@Nonnull String question, @Nonnull PromptParser<T> parser, @Nonnull Consumer<T> onAnswer) {
        CompletableFuture<T> answered = ask(PromptSpec.of(question).parse(parser));
        answered.thenAccept(onAnswer).exceptionally(failure -> {
            //the stage this handler hangs off is thrown away, so a handler that throws would otherwise fail in silence
            EverNifeCore.getLog().severe("The handler of an answer failed for [{}]", view.getViewerName(), failure);
            return null;
        });
        return answered;
    }

}
