package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.component.GuiComponent;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Cancellable;
import br.com.finalcraft.evernifecore.minecraft.gui.model.ClickPolicy;
import br.com.finalcraft.evernifecore.minecraft.gui.model.GuiGeometry;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Region;
import br.com.finalcraft.evernifecore.minecraft.gui.model.SlotSet;
import br.com.finalcraft.evernifecore.minecraft.gui.state.WatchState;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * One viewer's copy of a {@link Gui}: the container, the buffer that decides what to write into it,
 * the components with their state, the tasks that belong to it and the click bookkeeping.
 *
 * <p>A view exists only while the window is open. Closing it cancels every task it scheduled, drops
 * every state subscription it took and releases the {@code Player}, in that order and without
 * waiting for a garbage collector.</p>
 *
 * <p>Everything a screen changes lands in the buffer first and reaches the container once per tick,
 * so any number of state changes inside one tick cost one pass and only over the slots that differ.</p>
 */
public final class GuiView {

    /** Icons declared straight on the gui sit here; each component gets its own layer above it. */
    private static final int FIRST_COMPONENT_LAYER = Region.LAYER_CONTENT + 1;

    private final Gui<?> gui;
    private final UUID viewerId;
    private final String viewerName;
    private final GuiGeometry geometry;
    private final GuiBuffer buffer;
    private final GuiScheduler scheduler;

    private final List<GuiComponent> components = new ArrayList<>();
    private final Set<GuiComponent> dirtyComponents = Collections.newSetFromMap(new ConcurrentHashMap<>());
    //copy-on-write so a watch that registers another watch while being polled neither breaks the pass
    //in flight nor is lost: the newcomer joins the next one
    private final List<WatchState<?>> watches = new CopyOnWriteArrayList<>();
    private final Map<Gui.IconBinding, Icon> animatedIcons = new IdentityHashMap<>();
    private final TreeMap<Integer, Icon[]> iconLayers = new TreeMap<>();

    private final List<Cancellable> tasks = new ArrayList<>();
    private final List<Cancellable> subscriptions = new ArrayList<>();

    private GuiSurface surface;
    private Player viewer;
    private Region[] slotRegions;
    private String currentTitle;

    private Cancellable watchTask;
    private Cancellable pendingCommit;
    private CompletableFuture<Object> pendingResult;
    private boolean staticIconsDirty = true;
    private boolean swappingSurface = false;
    private boolean suspended = false;
    private long clickToken = 0L;
    private long lastAcceptedClickAt = 0L;
    private boolean closed = false;

    GuiView(Gui<?> gui, Player viewer, GuiSurface surface, GuiScheduler scheduler, String title) {
        this.gui = gui;
        this.viewer = viewer;
        this.viewerId = viewer.getUniqueId();
        this.viewerName = viewer.getName();
        this.surface = surface;
        this.geometry = new GuiGeometry(gui.getType(), surface.getSize());
        this.buffer = new GuiBuffer(surface.getSize());
        this.scheduler = scheduler;
        this.currentTitle = title;
    }

    /** Builds the components and arms whatever they asked to be armed. Runs once, right after opening. */
    void start() {
        for (Gui.IconBinding binding : gui.getIconBindings()) {
            Icon icon = binding.getIcon();
            if (!icon.isAnimated()) {
                continue;
            }
            Icon owned = icon.copy();
            animatedIcons.put(binding, owned);
            repeat(icon.getEveryTicks(), () -> {
                owned.runRenderer();
                staticIconsDirty = true;
                scheduleCommit();
            });
        }

        int layer = FIRST_COMPONENT_LAYER;
        for (Consumer<GuiComponent> declaration : gui.getComponentDeclarations()) {
            GuiComponent component = new GuiComponent(this, layer++);
            components.add(component);
            declaration.accept(component);
            component.start();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Reading
    // -----------------------------------------------------------------------------------------------------------------

    @Nonnull
    public Gui<?> getGui() {
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

    @Nonnull
    public List<GuiComponent> getComponents() {
        return Collections.unmodifiableList(components);
    }

    /** The title the container was opened with. It changes only when the surface is swapped. */
    @Nonnull
    public String getCurrentTitle() {
        return currentTitle;
    }

    public boolean isClosed() {
        return closed;
    }

    /**
     * Whether this screen is set aside: it still holds its state, its components and its tasks, but
     * it has no window - a screen opened on top of it has one, or a prompt is waiting on chat.
     */
    public boolean isSuspended() {
        return suspended;
    }

    /** Whether this view is the one drawn on {@code inventory}. */
    public boolean isSurface(@Nullable Inventory inventory) {
        return inventory != null && surface.isBackedBy(inventory);
    }

    /** The icon painted at {@code slot} - the topmost layer that put one there - or {@code null}. */
    @Nullable
    public Icon getIconAt(int slot) {
        if (!geometry.isInside(slot)) {
            return null;
        }
        for (Map.Entry<Integer, Icon[]> entry : iconLayers.descendingMap().entrySet()) {
            Icon icon = entry.getValue()[slot];
            if (icon != null) {
                return icon;
            }
        }
        return null;
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
    //  Painting
    // -----------------------------------------------------------------------------------------------------------------

    /** Paints one icon over every slot of {@code slots}, on {@code layer}. */
    public void paint(int layer, @Nonnull SlotSet slots, @Nonnull Icon icon) {
        if (!icon.isVisibleTo(viewer)) {
            return;
        }
        //Text and state are resolved here, not when the icon was declared: this is the first moment the
        //viewer is known, and it is what lets one icon read differently to two players
        ItemStack rendered = icon.renderFor(viewer, icon.getCurrentState(), gui.getReplacer());
        for (int slot : slots.resolve(geometry).toArray()) {
            if (!geometry.isInside(slot)) {
                EverNifeCore.getLog().warning("A gui icon was bound to slot " + slot + ", outside a "
                        + geometry + ". The icon was skipped.");
                continue;
            }
            buffer.write(layer, slot, rendered);
            iconsOf(layer)[slot] = icon;
        }
    }

    /** Erases one layer: its items and its icons, everywhere. */
    public void clearLayer(int layer) {
        buffer.clearLayer(layer);
        iconLayers.remove(layer);
    }

    /** Renders everything: the icons declared on the gui, then every component. */
    public void render() {
        renderStaticIcons();
        for (GuiComponent component : components) {
            component.renderNow();
        }
        dirtyComponents.clear();
    }

    private void renderStaticIcons() {
        staticIconsDirty = false;
        slotRegions = new Region[geometry.getSize()];
        for (Region region : gui.getRegions().values()) {
            for (int slot : region.getSlots().resolve(geometry).toArray()) {
                if (geometry.isInside(slot)) {
                    slotRegions[slot] = region;
                }
            }
        }

        clearLayer(Region.LAYER_BACKGROUND);
        clearLayer(Region.LAYER_CONTENT);
        for (Gui.IconBinding binding : gui.getIconBindings()) {
            Icon icon = animatedIcons.containsKey(binding) ? animatedIcons.get(binding) : binding.getIcon();
            paint(icon.isBackground() ? Region.LAYER_BACKGROUND : Region.LAYER_CONTENT,
                    binding.getSlots(), icon);
        }
    }

    private Icon[] iconsOf(int layer) {
        Icon[] icons = iconLayers.get(layer);
        if (icons == null) {
            icons = new Icon[geometry.getSize()];
            iconLayers.put(layer, icons);
        }
        return icons;
    }

    /** Renders every component again and schedules the commit. The commit still writes only what changed. */
    public void refresh() {
        if (closed) {
            return;
        }
        staticIconsDirty = true;
        for (GuiComponent component : components) {
            dirtyComponents.add(component);
        }
        scheduleCommit();
    }

    /** Marks one component for a re-render on the next tick. */
    public void markComponentDirty(@Nonnull GuiComponent component) {
        if (closed) {
            return;
        }
        dirtyComponents.add(component);
        scheduleCommit();
    }

    /**
     * Asks for a pass on the next tick, once. Every change made in the same tick lands in the same
     * write, and a screen nobody is changing schedules nothing.
     */
    public synchronized void scheduleCommit() {
        if (closed || suspended || pendingCommit != null) {
            return;
        }
        pendingCommit = scheduler.later(1L, this::runPass);
    }

    private void runPass() {
        synchronized (this) {
            pendingCommit = null;
        }
        if (closed) {
            return;
        }
        try {
            if (staticIconsDirty) {
                renderStaticIcons();
            }
            if (!dirtyComponents.isEmpty()) {
                List<GuiComponent> pending = new ArrayList<>(dirtyComponents);
                dirtyComponents.removeAll(pending);
                for (GuiComponent component : pending) {
                    component.renderNow();
                }
            }
            applyTitleIfChanged();
            commitNow();
        } catch (Throwable e) {
            EverNifeCore.getLog().severe("A gui render pass failed for [" + viewerName + "]: " + e);
            e.printStackTrace();
        }
    }

    private void applyTitleIfChanged() {
        String wanted = gui.getTitleFor(viewer);
        if (wanted.equals(currentTitle)) {
            return;
        }
        if (GuiViews.swapSurfaceForTitle(this, wanted)) {
            currentTitle = wanted;
        }
    }

    /** Writes the slots whose rendered output changed, right now. Main thread only. */
    public void commitNow() {
        if (closed || suspended) {
            return;
        }
        buffer.commit(surface);
    }

    /** Re-reads the whole container and puts back whatever stopped showing what this screen drew. */
    public void resync() {
        if (closed || suspended) {
            return;
        }
        buffer.adoptContainer(surface);
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

    /**
     * Polls a watched value once per tick. One task serves every watch of the view, and it exists
     * only while at least one watch does - a screen with no watch polls nothing.
     */
    public void addWatch(@Nonnull WatchState<?> watch) {
        watches.add(watch);
        if (watchTask == null) {
            watchTask = repeat(1L, this::pollWatches);
        }
    }

    private void pollWatches() {
        for (WatchState<?> watch : watches) {
            try {
                watch.poll();
            } catch (Throwable e) {
                EverNifeCore.getLog().severe("A gui watch failed for [" + viewerName + "]: " + e);
                e.printStackTrace();
            }
        }
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
    //  Surface swap - the reopen a title change costs
    // -----------------------------------------------------------------------------------------------------------------

    /** True while the container is being replaced, so the close it causes is not a real close. */
    boolean isSwappingSurface() {
        return swappingSurface;
    }

    void beginSurfaceSwap() {
        this.swappingSurface = true;
    }

    void endSurfaceSwap() {
        this.swappingSurface = false;
    }

    /** Adopts the replacement container. It starts empty, so everything is written again. */
    void adoptSurface(GuiSurface newSurface) {
        this.surface = newSurface;
        buffer.forgetCommitted();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Suspending - the screen outlives its window while another one, or a prompt, has the player
    // -----------------------------------------------------------------------------------------------------------------

    /** Sets the screen aside. It keeps everything it holds and stops writing, having nowhere to write. */
    void suspend() {
        if (closed || suspended) {
            return;
        }
        suspended = true;
        if (pendingCommit != null) {
            pendingCommit.cancel();
            pendingCommit = null;
        }
    }

    /** Brings the screen back onto a container it has never written into, so everything is drawn again. */
    void resume(GuiSurface newSurface, String title) {
        if (closed) {
            return;
        }
        suspended = false;
        currentTitle = title;
        adoptSurface(newSurface);
    }

    /** The future whoever opened this screen is waiting on, or {@code null} when nobody is. */
    @Nullable
    CompletableFuture<Object> getPendingResult() {
        return pendingResult;
    }

    void setPendingResult(@Nullable CompletableFuture<Object> pendingResult) {
        this.pendingResult = pendingResult;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Closing
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Closes the window now. The close event releases the view.
     *
     * <p>A suspended screen has no window of its own, so it closes nothing: whatever the player is
     * looking at belongs to another screen.</p>
     */
    public void close() {
        Player player = this.viewer;
        if (player != null && !closed && !suspended) {
            player.closeInventory();
        }
    }

    /** Closes on the next tick - the safe form from inside a click handler. */
    public void closeNextTick() {
        if (closed || suspended) {
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
        watchTask = null;
        watches.clear();
        for (Cancellable subscription : subscriptions) {
            subscription.cancel();
        }
        subscriptions.clear();
        dirtyComponents.clear();

        try {
            if (gui.getOnClose() != null) {
                gui.getOnClose().accept(new CloseContext(this, surface, viewer, reason));
            }
        } catch (Throwable e) {
            EverNifeCore.getLog().severe("The onClose handler of a gui failed for [" + viewerName + "]: " + e);
            e.printStackTrace();
        }

        this.viewer = null;
        this.slotRegions = null;
        this.iconLayers.clear();
        this.animatedIcons.clear();
        this.components.clear();
    }

    /** What the container holds at those slots right now. */
    @Nonnull
    public List<ItemStack> getContents(@Nonnull SlotSet slots) {
        SlotSet resolved = slots.resolve(geometry);
        List<ItemStack> contents = new ArrayList<>(resolved.size());
        for (int slot : resolved.toArray()) {
            contents.add(surface.getItem(slot));
        }
        return contents;
    }

}
