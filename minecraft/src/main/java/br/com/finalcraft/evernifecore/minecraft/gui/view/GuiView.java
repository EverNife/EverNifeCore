package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Cancellable;
import br.com.finalcraft.evernifecore.minecraft.gui.model.ClickPolicy;
import br.com.finalcraft.evernifecore.minecraft.gui.model.GuiGeometry;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Region;
import br.com.finalcraft.evernifecore.minecraft.gui.model.SlotSet;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One viewer's copy of a {@link Gui}: the container, the buffer that decides what to write into it,
 * the tasks that belong to it and the click bookkeeping.
 *
 * <p>A view exists only while the window is open. Closing it cancels every task it scheduled, drops
 * every state subscription it took and releases the {@code Player}, in that order and without
 * waiting for a garbage collector.</p>
 */
public final class GuiView {

    private final Gui gui;
    private final UUID viewerId;
    private final String viewerName;
    private final GuiSurface surface;
    private final GuiGeometry geometry;
    private final GuiBuffer buffer;
    private final GuiScheduler scheduler;

    private final List<Cancellable> tasks = new ArrayList<>();
    private final List<Cancellable> subscriptions = new ArrayList<>();

    private Player viewer;
    private Icon[] slotIcons;
    private int[] slotIconLayers;
    private Region[] slotRegions;

    private Cancellable pendingCommit;
    private long clickToken = 0L;
    private long lastAcceptedClickAt = 0L;
    private boolean closed = false;

    GuiView(Gui gui, Player viewer, GuiSurface surface, GuiScheduler scheduler) {
        this.gui = gui;
        this.viewer = viewer;
        this.viewerId = viewer.getUniqueId();
        this.viewerName = viewer.getName();
        this.surface = surface;
        this.geometry = new GuiGeometry(gui.getType(), surface.getSize());
        this.buffer = new GuiBuffer(surface.getSize());
        this.scheduler = scheduler;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Reading
    // -----------------------------------------------------------------------------------------------------------------

    @Nonnull
    public Gui getGui() {
        return gui;
    }

    /** The player, or {@code null} once the view has been released. */
    @Nullable
    public Player getViewer() {
        return viewer;
    }

    @Nonnull
    public UUID getViewerId() {
        return viewerId;
    }

    @Nonnull
    public String getViewerName() {
        return viewerName;
    }

    @Nonnull
    public GuiSurface getSurface() {
        return surface;
    }

    @Nonnull
    public GuiGeometry getGeometry() {
        return geometry;
    }

    @Nonnull
    public GuiBuffer getBuffer() {
        return buffer;
    }

    @Nonnull
    public GuiScheduler getScheduler() {
        return scheduler;
    }

    public boolean isClosed() {
        return closed;
    }

    /** Whether this view is the one drawn on {@code inventory}. */
    public boolean isSurface(@Nullable Inventory inventory) {
        return inventory != null
                && surface instanceof BukkitGuiSurface
                && ((BukkitGuiSurface) surface).getInventory() == inventory;
    }

    /** The icon painted at {@code slot}, or {@code null}. */
    @Nullable
    public Icon getIconAt(int slot) {
        return slotIcons != null && slot >= 0 && slot < slotIcons.length ? slotIcons[slot] : null;
    }

    /** The policy ruling {@code slot}: the region's when one claims it, the gui's otherwise. */
    @Nonnull
    public ClickPolicy getPolicyAt(int slot) {
        if (slotRegions != null && slot >= 0 && slot < slotRegions.length && slotRegions[slot] != null) {
            return slotRegions[slot].getPolicy();
        }
        return gui.getPolicy();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------------------------------------------------

    /** Renders every icon into the buffer. Does not touch the container - {@link #commitNow()} does. */
    public void render() {
        slotIcons = new Icon[geometry.getSize()];
        slotIconLayers = new int[geometry.getSize()];
        slotRegions = new Region[geometry.getSize()];

        buffer.clearLayer(Region.LAYER_BACKGROUND);
        buffer.clearLayer(Region.LAYER_CONTENT);

        for (Region region : gui.getRegions().values()) {
            for (Integer slot : region.getSlots().resolve(geometry)) {
                if (geometry.isInside(slot)) {
                    slotRegions[slot] = region;
                }
            }
        }

        for (Gui.IconBinding binding : gui.getIconBindings()) {
            Icon icon = binding.getIcon();
            if (!icon.isVisibleTo(viewer)) {
                continue;
            }
            int layer = icon.isBackground() ? Region.LAYER_BACKGROUND : Region.LAYER_CONTENT;
            ItemStack rendered = icon.getItemStack();
            for (Integer slot : binding.getSlots().resolve(geometry)) {
                if (!geometry.isInside(slot)) {
                    EverNifeCore.getLog().warning("Gui icon bound to slot " + slot + ", outside a "
                            + geometry + ". The icon was skipped.");
                    continue;
                }
                buffer.write(layer, slot, rendered);
                if (slotIcons[slot] == null || layer >= slotIconLayers[slot]) {
                    slotIcons[slot] = icon;
                    slotIconLayers[slot] = layer;
                }
            }
        }
    }

    /** Renders again and schedules the commit. The commit still writes only what changed. */
    public void refresh() {
        if (closed) {
            return;
        }
        render();
        scheduleCommit();
    }

    /**
     * Asks for a commit on the next tick, once. Every change made in the same tick lands in the same
     * write, and a screen nobody is changing schedules nothing.
     */
    public void scheduleCommit() {
        if (closed || pendingCommit != null) {
            return;
        }
        pendingCommit = scheduler.later(1L, () -> {
            pendingCommit = null;
            commitNow();
        });
    }

    /** Writes the slots whose rendered output changed, right now. Main thread only. */
    public void commitNow() {
        if (closed) {
            return;
        }
        buffer.commit(surface);
    }

    /** Re-reads the whole container after something outside the framework touched it. */
    public void resync() {
        buffer.markAllDirty();
        commitNow();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Scheduling and subscriptions owned by this view
    // -----------------------------------------------------------------------------------------------------------------

    /** Schedules a repeating task that dies with this view. */
    @Nonnull
    public Cancellable repeat(long ticks, @Nonnull Runnable task) {
        if (closed) {
            return Cancellable.NONE;
        }
        Cancellable handle = scheduler.repeat(ticks, task);
        tasks.add(handle);
        return handle;
    }

    /** Keeps a subscription (to a state, typically) alive only while this view is. */
    public void own(@Nullable Cancellable subscription) {
        if (subscription == null) {
            return;
        }
        if (closed) {
            subscription.cancel();
            return;
        }
        subscriptions.add(subscription);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Clicks
    // -----------------------------------------------------------------------------------------------------------------

    /** Opens a new click, making every click still in flight stale. */
    long nextClickToken() {
        return ++clickToken;
    }

    /** Whether {@code token} is still the current click and the view is still open. */
    public boolean isTokenAlive(long token) {
        return !closed && token == clickToken;
    }

    /**
     * Whether a click at {@code nowMillis} is inside the debounce window of the last accepted one.
     * A rejected attempt is not recorded, so holding the button cannot extend the window forever.
     */
    boolean isWithinDebounce(long nowMillis) {
        long debounce = gui.getDebounceMillis();
        return debounce > 0 && (nowMillis - lastAcceptedClickAt) < debounce;
    }

    void markClickAccepted(long nowMillis) {
        this.lastAcceptedClickAt = nowMillis;
    }

    ClickContext newClickContext(long token, int slot, ClickType clickType, ItemStack cursor, Icon icon) {
        return new ClickContext(this, token, slot, clickType, cursor, icon);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Closing
    // -----------------------------------------------------------------------------------------------------------------

    /** Closes the window now. The close event releases the view. */
    public void close() {
        Player player = this.viewer;
        if (player != null && !closed) {
            player.closeInventory();
        }
    }

    /** Closes on the next tick - the safe form from inside a click handler. */
    public void closeNextTick() {
        if (closed) {
            return;
        }
        scheduler.later(1L, this::close);
    }

    /**
     * Tears the view down: tasks cancelled, subscriptions dropped, {@code onClose} run, {@code Player}
     * released. Idempotent.
     */
    void release(CloseReason reason) {
        if (closed) {
            return;
        }
        closed = true;

        if (pendingCommit != null) {
            pendingCommit.cancel();
            pendingCommit = null;
        }
        for (Cancellable task : tasks) {
            task.cancel();
        }
        tasks.clear();
        for (Cancellable subscription : subscriptions) {
            subscription.cancel();
        }
        subscriptions.clear();

        try {
            if (gui.getOnClose() != null) {
                gui.getOnClose().accept(new CloseContext(this, surface, viewer, reason));
            }
        } catch (Throwable e) {
            EverNifeCore.getLog().severe("The onClose handler of a gui failed for [" + viewerName + "]: " + e);
            e.printStackTrace();
        }

        this.viewer = null;
        this.slotIcons = null;
        this.slotIconLayers = null;
        this.slotRegions = null;
    }

    /** What the container holds at those slots right now. */
    @Nonnull
    public List<ItemStack> getContents(@Nonnull SlotSet slots) {
        SlotSet resolved = slots.resolve(geometry);
        List<ItemStack> contents = new ArrayList<>(resolved.size());
        for (Integer slot : resolved) {
            contents.add(surface.getItem(slot));
        }
        return contents;
    }

}
