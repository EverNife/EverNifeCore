package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.ResultGui;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.view.prompt.ChatPrompt;
import br.com.finalcraft.evernifecore.minecraft.gui.view.prompt.ChatPromptChannel;
import br.com.finalcraft.evernifecore.minecraft.gui.view.prompt.PromptParser;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * One click, as the handler sees it: who clicked, where, how, and what it may do about it.
 *
 * <p>It never exposes the {@code InventoryClickEvent}. Everything a handler legitimately wants is a
 * method here, which is what makes a handler assertable without a server.</p>
 *
 * <p>A handler may finish long after the click - it may be waiting on player data. A later click
 * makes an earlier one stale, and every action below then does nothing: a screen the player has
 * already moved on from must not be closed, redrawn or made to play a sound by an answer that
 * arrived too late. {@link #isAlive()} is how a handler can tell before doing work of its own.</p>
 */
public final class ClickContext {

    private final GuiView view;
    private final long token;
    private final int slot;
    private final ClickType clickType;
    private final ItemStack cursor;
    private final Icon icon;

    private boolean moveAllowed = false;

    ClickContext(GuiView view, long token, int slot, ClickType clickType, ItemStack cursor, Icon icon) {
        this.view = view;
        this.token = token;
        this.slot = slot;
        this.clickType = clickType;
        this.cursor = cursor == null ? null : cursor.clone();
        this.icon = icon;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Reading
    // -----------------------------------------------------------------------------------------------------------------

    @Nullable
    public Player getViewer() {
        return view.getViewer();
    }

    @Nonnull
    public Gui<?> getGui() {
        return view.getGui();
    }

    @Nonnull
    public GuiView getView() {
        return view;
    }

    /** The raw, 0-based slot inside the gui. */
    public int getSlot() {
        return slot;
    }

    @Nonnull
    public ClickType getClickType() {
        return clickType;
    }

    /** A snapshot of what was on the cursor when the click happened, or {@code null}. */
    @Nullable
    public ItemStack getCursor() {
        return cursor;
    }

    /** The icon that was clicked, or {@code null} when the slot carried none. */
    @Nullable
    public Icon getIcon() {
        return icon;
    }

    /** Whether this is still the click the player is waiting on, and the screen is still open. */
    public boolean isAlive() {
        return view.isTokenAlive(token);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Acting
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Lets this one click move items, overriding the screen's policy.
     *
     * <p>Only meaningful while the handler is still running: once the click has been answered there
     * is no event left to un-cancel.</p>
     */
    public void allowMove() {
        if (isAlive()) {
            this.moveAllowed = true;
        }
    }

    boolean isMoveAllowed() {
        return moveAllowed;
    }

    /**
     * Closes the screen, on the next tick - closing a container from inside its own click is not safe.
     *
     * <p>Which icon asked for it is remembered, so the close handler can tell a button from the escape
     * key: see {@link CloseContext#wasClosedBy(java.util.function.Function)}.</p>
     */
    public void close() {
        if (isAlive()) {
            view.markClosedBy(icon);
            view.closeNextTick();
        }
    }

    /** Renders every icon again. The commit still writes only the slots whose output changed. */
    public void refresh() {
        if (isAlive()) {
            view.refresh();
        }
    }

    public void sound(@Nonnull Sound sound) {
        sound(sound, 1.0F, 1.0F);
    }

    public void sound(@Nonnull Sound sound, float volume, float pitch) {
        Player viewer = getViewer();
        if (isAlive() && viewer != null && sound != null) {
            viewer.playSound(viewer.getLocation(), sound, volume, pitch);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Navigating
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Opens {@code gui} on top of this screen. This one is set aside whole - its page, its filter and
     * everything its components remembered - and {@link #back()} gives it back exactly as it was.
     *
     * @return the value the opened screen hands back through {@code back(value)}. It is cancelled
     *         instead when the player walks away without answering, so a {@code thenAccept} only ever
     *         runs on an answer somebody actually gave.
     */
    @Nonnull
    public CompletableFuture<Object> open(@Nonnull Gui<?> gui) {
        return GuiNavigation.open(view, gui);
    }

    /** {@link #open(Gui)} onto a screen that says what it answers with - see {@link ResultGui}. */
    @Nonnull
    @SuppressWarnings("unchecked")
    public <R> CompletableFuture<R> open(@Nonnull ResultGui<R, ?> gui) {
        return (CompletableFuture<R>) GuiNavigation.open(view, gui);
    }

    /** Leaves this screen for the one underneath it, with nothing to say to it. */
    public void back() {
        back(null);
    }

    /**
     * Leaves this screen for the one underneath it, handing {@code value} to whoever opened this one.
     * At the bottom of a chain there is nothing underneath, so the screen simply closes.
     */
    public void back(@Nullable Object value) {
        view.markClosedBy(icon);
        GuiNavigation.back(view, value);
    }

    /**
     * Puts {@code gui} where this screen is: the one underneath is untouched, so a later
     * {@link #back()} goes there and not to the screen just replaced. Whoever was waiting on this step
     * of the chain now waits on {@code gui}.
     */
    public void replace(@Nonnull Gui<?> gui) {
        GuiNavigation.replace(view, gui);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Asking for a value no button can express
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Asks the player to type the answer in chat. The screen goes away while the question is out and
     * comes back with its state intact once it is answered, called off or timed out.
     *
     * <pre>{@code
     * icon(l -> l.EDIT_PRICE).onClick(ctx -> ctx.askOnChat(
     *         ChatPrompt.of("§eType the new price, or 'cancel':")
     *                 .parse(Double::parseDouble)
     *                 .cancelWord("cancel"))
     *         .thenAccept(price -> auction.setPrice(price)));
     * }</pre>
     *
     * @return the answer, completed on the main thread. Cancelled when there was none - the player
     *         said the cancel word, ran out of time or left - so {@code thenAccept} runs on answers
     *         only. The prompt's own {@code onTimeout} and {@code onQuit} are how those are handled.
     */
    @Nonnull
    public <T> CompletableFuture<T> askOnChat(@Nonnull ChatPrompt<T> prompt) {
        CompletableFuture<T> answered = new CompletableFuture<>();
        ChatPromptChannel.get().ask(view, prompt).thenAccept(result -> {
            if (result.hasValue()) {
                answered.complete(result.getValue());
            } else {
                answered.cancel(false);
            }
        });
        return answered;
    }

    /** {@link #askOnChat(ChatPrompt)} with the defaults - the short form of the same question. */
    @Nonnull
    public <T> CompletableFuture<T> askOnChat(@Nonnull String question, @Nonnull PromptParser<T> parser,
                                              @Nonnull Consumer<T> onAnswer) {
        CompletableFuture<T> answered = askOnChat(ChatPrompt.of(question).parse(parser));
        answered.thenAccept(onAnswer);
        return answered;
    }

}
