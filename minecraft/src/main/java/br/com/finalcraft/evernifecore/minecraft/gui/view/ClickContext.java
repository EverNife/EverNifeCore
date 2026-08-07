package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.ResultGui;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.CompletableFuture;

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

    /** Closes the screen, on the next tick - closing a container from inside its own click is not safe. */
    public void close() {
        if (isAlive()) {
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

}
