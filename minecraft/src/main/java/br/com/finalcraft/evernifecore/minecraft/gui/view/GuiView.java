package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.component.GuiComponent;
import br.com.finalcraft.evernifecore.minecraft.gui.component.StorageBinding;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutBase;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Cancellable;
import br.com.finalcraft.evernifecore.minecraft.gui.model.ClickPolicy;
import br.com.finalcraft.evernifecore.minecraft.gui.model.GuiGeometry;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Region;
import br.com.finalcraft.evernifecore.minecraft.gui.model.SlotSet;
import br.com.finalcraft.evernifecore.minecraft.gui.state.WatchState;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
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
 *
 * <p>Drawing happens on the main thread, but a state may go stale on any thread, so the flags another
 * thread reads or writes - {@code closed}, {@code suspended}, the click token - are {@code volatile}
 * and the scheduled pass is held under this view's monitor.</p>
 */
public final class GuiView {

    /** Icons declared straight on the gui sit here; each component gets its own layer above it. */
    private static final int FIRST_COMPONENT_LAYER = GuiBuffer.LAYER_CONTENT + 1;

    private final Gui<?> gui;
    private final LayoutBase layout;
    private final UUID viewerId;
    private final String viewerName;
    private final GuiGeometry geometry;
    private final GuiBuffer buffer;
    private final GuiScheduler scheduler;

    private final List<GuiComponent> components = new ArrayList<>();
    private final List<StorageView> storages = new ArrayList<>();
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
    private StorageView[] slotStorages;
    private Icon closedBy;
    private String currentTitle;

    private Cancellable watchTask;
    private Cancellable pendingCommit;
    private CompletableFuture<Object> pendingResult;
    private boolean staticIconsDirty = true;
    private boolean swappingSurface = false;
    private volatile boolean suspended = false;
    private volatile long clickToken = 0L;
    private long lastAcceptedClickAt = 0L;
    private volatile boolean closed = false;

    GuiView(Gui<?> gui, Player viewer, GuiSurface surface, GuiScheduler scheduler, String title) {
        this.gui = gui;
        //asked once, here: resolving it costs a look at the filesystem, and a render costs none
        this.layout = gui.getLayoutFor(viewer);
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
        startStorages();
        for (Gui.IconBinding binding : gui.getIconBindings(layout)) {
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

    /**
     * Takes the editable areas over: their slots leave the buffer's hands and each store is written into
     * the container. Before any component is built, so nothing can draw over a slot the player now owns.
     */
    private void startStorages() {
        List<StorageBinding> declared = gui.getStorages();
        if (declared.isEmpty()) {
            return;
        }
        slotStorages = new StorageView[geometry.getSize()];
        for (StorageBinding binding : declared) {
            int[] claimed = claimSlotsOf(binding);
            StorageView storage = new StorageView(this, binding, claimed);
            for (int slot : claimed) {
                slotStorages[slot] = storage;
                buffer.disown(slot);
            }
            storages.add(storage);
            storage.seed(surface);
        }
    }

    /** The slots of {@code binding} that this window actually has, and that no other region claimed. */
    private int[] claimSlotsOf(StorageBinding binding) {
        int[] resolved = binding.getSlots().resolve(geometry).toArray();
        int[] claimed = new int[resolved.length];
        int size = 0;
        for (int slot : resolved) {
            if (!geometry.isInside(slot)) {
                EverNifeCore.getLog().warning("The storage region [" + binding.getName() + "] was given slot "
                        + slot + ", outside a " + geometry + ". The slot was skipped.");
                continue;
            }
            if (slotStorages[slot] != null) {
                throw new IllegalStateException("Slot " + slot + " is claimed by the storage region ["
                        + slotStorages[slot].getBinding().getName() + "] and by [" + binding.getName()
                        + "] at once. Two editable areas sharing a slot would each read the other's items "
                        + "into their own store, so give each region slots of its own.");
            }
            claimed[size++] = slot;
        }
        if (size == 0) {
            EverNifeCore.getLog().warning("The storage region [" + binding.getName() + "] of ["
                    + gui.getTitle() + "] resolved to no slot at all, so nothing of its store is reachable. "
                    + "Give it slots that this window has - through the Slot list of the layout icon it was "
                    + "declared from, or through the SlotSet handed to storage(...).");
        }
        return Arrays.copyOf(claimed, size);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Reading
    // -----------------------------------------------------------------------------------------------------------------

    @Nonnull
    public Gui<?> getGui() {
        return gui;
    }

    /**
     * The layout this window was painted from: the copy resolved for the viewer's own language when the
     * admin wrote an overlay of it, and {@code null} on a screen sized by hand.
     */
    @Nullable
    public LayoutBase getLayout() {
        return layout;
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

    /**
     * The policy ruling {@code slot}: the editable area's when one holds it, then a region's, then the
     * gui's.
     */
    @Nonnull
    public ClickPolicy getPolicyAt(int slot) {
        StorageView storage = getStorageAt(slot);
        if (storage != null) {
            return storage.getPolicy();
        }
        if (slotRegions != null && slot >= 0 && slot < slotRegions.length && slotRegions[slot] != null) {
            return slotRegions[slot].getPolicy();
        }
        return gui.getPolicy();
    }

    /** The editable area {@code slot} belongs to, or {@code null} when the framework still owns it. */
    @Nullable
    public StorageView getStorageAt(int slot) {
        if (slotStorages == null || slot < 0 || slot >= slotStorages.length) {
            return null;
        }
        return slotStorages[slot];
    }

    /** This screen's editable areas, in the order they were declared. */
    @Nonnull
    public List<StorageView> getStorages() {
        return Collections.unmodifiableList(storages);
    }

    /**
     * Whether this screen declares an editable area at all. It says nothing about what any gesture is
     * allowed to do there - that is the area's own {@link ClickPolicy}.
     */
    public boolean hasStorage() {
        return !storages.isEmpty();
    }

    /**
     * The icon whose handler asked for the close, or {@code null} when nothing did - which is what the
     * escape key looks like. See {@link CloseContext#wasClosedBy(java.util.function.Function)}.
     */
    @Nullable
    public Icon getClosedBy() {
        return closedBy;
    }

    /** Remembers the icon a close was asked from, so the close handler can tell which gesture it was. */
    void markClosedBy(@Nullable Icon icon) {
        if (!closed) {
            this.closedBy = icon;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Painting
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Paints one icon over every slot of {@code slots}, on {@code layer}.
     *
     * <p>An icon this viewer cannot see paints nothing and erases nothing: what another icon already
     * put on that layer at that slot stays, which is what makes the next icon in line appear when the
     * one that owns the slot is not alive. Blanking is a whole layer's job - {@link #clearLayer(int)},
     * which every render pass runs before drawing.</p>
     *
     * <p>A slot inside an editable area is skipped whole: it holds what the player put there, and an
     * icon painted over it would be an item destroyed.</p>
     */
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
            if (!buffer.owns(slot)) {
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

        clearLayer(GuiBuffer.LAYER_BACKGROUND);
        clearLayer(GuiBuffer.LAYER_CONTENT);
        for (Gui.IconBinding binding : gui.getIconBindings(layout)) {
            Icon icon = animatedIcons.getOrDefault(binding, binding.getIcon());
            paint(icon.isBackground() ? GuiBuffer.LAYER_BACKGROUND : GuiBuffer.LAYER_CONTENT,
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
            EverNifeCore.getLog().severe("A gui render pass failed for [{}]", viewerName, e);
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
     * Takes a watch onto the view's clock. One task serves every watch of the view and ticks once, each
     * watch counting its own interval, so a cadence is a watch's business and never the view's. The task
     * exists only while at least one watch does - a screen with no watch polls nothing.
     */
    public void addWatch(@Nonnull WatchState<?> watch) {
        if (closed) {
            return;
        }
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
                EverNifeCore.getLog().severe("A gui watch failed for [{}]", viewerName, e);
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

    /**
     * Adopts the replacement container. It starts empty, so everything is written again - including what
     * an editable area holds, which the framework does not redraw and the old container is the only copy
     * of.
     */
    void adoptSurface(GuiSurface newSurface) {
        for (StorageView storage : storages) {
            storage.carryOver(surface, newSurface);
        }
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
        cancelPendingCommit();
    }

    /** Drops the pass that was scheduled, if any. Under the monitor {@link #scheduleCommit()} takes. */
    private synchronized void cancelPendingCommit() {
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
     * Tears the view down: tasks cancelled, subscriptions dropped, editable areas read back into their
     * stores, whatever is on the cursor given back, {@code onClose} run, {@code Player} released.
     * Idempotent.
     */
    void release(CloseReason reason) {
        if (closed) {
            return;
        }
        closed = true;

        cancelPendingCommit();
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

        //while the container is still readable, and before the close handler, so it reads a store that
        //already agrees with what is on screen. One region at a time: a store that throws costs its own
        //contents and nothing else, and the cursor is given back whatever any of them did
        for (StorageView storage : storages) {
            try {
                storage.syncNow(true);
            } catch (Throwable e) {
                EverNifeCore.getLog().severe("The editable region [{}] of a gui could not be read back for [{}]",
                        storage.getBinding().getName(), viewerName, e);
            }
        }
        try {
            returnCarriedItem();
        } catch (Throwable e) {
            EverNifeCore.getLog().severe("What [{}] was holding on the cursor could not be "
                    + "given back", viewerName, e);
        }

        try {
            fireOnClose(reason);
        } catch (Throwable e) {
            EverNifeCore.getLog().severe("The onClose handler of a gui failed for [{}]", viewerName, e);
        }

        for (StorageView storage : storages) {
            storage.teardown();
        }
        storages.clear();

        this.viewer = null;
        this.slotRegions = null;
        this.slotStorages = null;
        this.iconLayers.clear();
        this.animatedIcons.clear();
        this.components.clear();
    }

    //the handler was registered on a Gui<L>, and this view only ever knows Gui<?> - the layout the
    //context hands back is the one that Gui was built from either way
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void fireOnClose(CloseReason reason) {
        Consumer handler = gui.getOnClose();
        if (handler != null) {
            handler.accept(new CloseContext<>(this, surface, viewer, reason));
        }
    }

    /**
     * Gives the viewer back whatever they are still holding on the cursor.
     *
     * <p>Only a screen with an editable area can have put anything there. The platform's own answer to a
     * window torn down mid-gesture is to drop the stack on the ground - and to a player already gone,
     * nothing at all - so it is taken off the cursor first and handed over afterwards, which is also what
     * stops the platform from giving out a second copy of it.</p>
     */
    private void returnCarriedItem() {
        Player player = this.viewer;
        if (storages.isEmpty() || player == null) {
            return;
        }
        ItemStack carried = player.getItemOnCursor();
        if (GuiBuffer.isEmpty(carried)) {
            return;
        }
        player.setItemOnCursor(new ItemStack(Material.AIR));
        FCBukkitUtil.giveItemsTo(player, carried);
    }

    /** What the container holds at those slots right now, empty slots included as {@code null}. */
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
