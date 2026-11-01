package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.EventSourceUsage;
import br.com.finalcraft.evernifecore.action.UsageHold;
import br.com.finalcraft.evernifecore.action.event.ActionEvent;
import br.com.finalcraft.evernifecore.action.event.ActionEvents;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.Conditions;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.screen.prompt.PromptRegistry;
import br.com.finalcraft.evernifecore.screen.prompt.PromptSpec;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * One viewer's copy of a {@link Screen}: the surface its renderer drew, the buffer that decides what to write through
 * it, the components with their state, the tasks and holds that belong to it and the click bookkeeping.
 *
 * <p>A view exists only while its window is open (or set aside under another one). Releasing it cancels every task it
 * scheduled, drops every subscription, releases every {@link UsageHold} in reverse order and lets go of the viewer.</p>
 *
 * <p>Everything a screen changes lands in the buffer first and reaches the surface once per tick, in ONE
 * {@link ScreenSurface#apply} call with only the slots that differ - any number of changes inside one tick cost one
 * write. Drawing happens on the renderer thread ({@link #getScheduler()}); a state may go stale on any thread, so what
 * another thread reads or writes here is volatile or held under this view's monitor.</p>
 */
public final class ScreenView {

    /** Icons the layout painted sit here; each component gets its own layer above it. */
    private static final int FIRST_COMPONENT_LAYER = SlotBuffer.LAYER_CONTENT + 1;

    private final Screen<?> screen;
    private final Layout layout;
    private final ScreenRenderer renderer;
    private final List<Object> arguments;
    private final UUID viewerId;
    private final String viewerName;
    private final Grid grid;
    private final SlotBuffer buffer;
    private final ScreenScheduler scheduler;

    private final List<ScreenComponent> components = new ArrayList<>();
    private final Set<ScreenComponent> dirtyComponents = Collections.newSetFromMap(new ConcurrentHashMap<>());
    //copy-on-write so a watch that registers another watch while being polled neither breaks the pass in flight nor is
    //lost: the newcomer joins the next one
    private final List<WatchState<?>> watches = new CopyOnWriteArrayList<>();
    private final TreeMap<Integer, Painted[]> paintLayers = new TreeMap<>();
    private final Map<EventSource, Set<ScreenComponent>> listeners = new ConcurrentHashMap<>();
    private final List<UsageHold> holds = new ArrayList<>();
    private final List<ECEventSubscription<?>> eventSubscriptions = new ArrayList<>();

    private final List<Cancellable> tasks = new ArrayList<>();
    private final List<Cancellable> subscriptions = new ArrayList<>();

    private ScreenSurface surface;
    private FPlayer viewer;
    private NamedRegion[] slotRegions;
    private StorageBinding[] slotStorages;
    private Icon closedBy;
    private String currentTitle;

    private Cancellable watchTask;
    private Cancellable pendingCommit;
    private CompletableFuture<Object> pendingResult;
    private boolean layoutIconsDirty = true;
    private volatile boolean suspended = false;
    private volatile long clickToken = 0L;
    private long lastAcceptedClickAt = 0L;
    private volatile boolean closed = false;

    ScreenView(@Nonnull Screen<?> screen, @Nonnull FPlayer viewer, @Nonnull ScreenRenderer renderer, @Nonnull Object[] arguments) {
        this.screen = screen;
        //asked once, here: resolving it costs a look at the filesystem, and a render costs none
        this.layout = screen.getLayoutFor(viewer);
        this.renderer = renderer;
        this.arguments = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(arguments)));
        this.viewer = viewer;
        this.viewerId = viewer.getUniqueId();
        this.viewerName = viewer.getName();
        this.grid = screen.getGrid();
        this.buffer = new SlotBuffer(grid.getSize());
        this.scheduler = renderer.getScheduler();
        this.currentTitle = screen.getTitleFor(viewer, argumentsOrNull());
    }

    /** The window the renderer opened for this view. */
    void attach(@Nonnull ScreenSurface surface) {
        this.surface = surface;
    }

    /** Builds the components and arms what they asked for. Runs once, right after the window opened. */
    void start() {
        screen.getElements(); //refuses two elements under one id before anything is armed
        claimStorages();
        int layer = FIRST_COMPONENT_LAYER;
        int backgroundLayer = SlotBuffer.LAYER_BACKGROUND + 1; //over the layout's backdrop, under its content
        for (Consumer<ScreenComponent> declaration : screen.getComponentDeclarations()) {
            boolean background = screen.isBackgroundDeclaration(declaration) && backgroundLayer < SlotBuffer.LAYER_CONTENT;
            ScreenComponent component = new ScreenComponent(this, background ? backgroundLayer++ : layer++);
            components.add(component);
            declaration.accept(component);
            component.start();
        }
    }

    /** Hands the editable areas to the renderer: the buffer stops drawing on their slots. */
    private void claimStorages() {
        List<StorageBinding> declared = screen.getStorages();
        if (declared.isEmpty()) {
            return;
        }
        slotStorages = new StorageBinding[grid.getSize()];
        for (StorageBinding binding : declared) {
            for (int slot : binding.getSlots().resolve(grid).toArray()) {
                if (!grid.isInside(slot)) {
                    EverNifeCore.getLog().warning("The storage region [" + binding.getName() + "] was given slot " + slot
                            + ", outside a " + grid + ". The slot was skipped.");
                    continue;
                }
                if (slotStorages[slot] != null) {
                    throw new IllegalStateException("Slot " + slot + " is claimed by the storage region ["
                            + slotStorages[slot].getName() + "] and by [" + binding.getName() + "] at once. Two editable "
                            + "areas sharing a slot would each read the other's items into their own store, so give each "
                            + "region slots of its own.");
                }
                slotStorages[slot] = binding;
                buffer.disown(slot);
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Reading
    // -----------------------------------------------------------------------------------------------------------------

    @Nonnull
    public Screen<?> getScreen() {
        return screen;
    }

    /** The layout this window was painted from (the viewer's language copy), or {@code null} on a screen sized by hand. */
    @Nullable
    public Layout getLayout() {
        return layout;
    }

    @Nonnull
    public ScreenRenderer getRenderer() {
        return renderer;
    }

    /** What the opener passed after the viewer - {@code {0}}..{@code {n}} in the screen's text. */
    @Nonnull
    public List<Object> getArguments() {
        return arguments;
    }

    /** The player, or {@code null} once the view has been released. */
    @Nullable
    public FPlayer getViewer() {
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

    /** The surface it draws through; {@code null} only before the renderer opened the window. */
    @Nullable
    public ScreenSurface getSurface() {
        return surface;
    }

    @Nonnull
    public Grid getGrid() {
        return grid;
    }

    @Nonnull
    public SlotBuffer getBuffer() {
        return buffer;
    }

    @Nonnull
    public ScreenScheduler getScheduler() {
        return scheduler;
    }

    @Nonnull
    public List<ScreenComponent> getComponents() {
        return Collections.unmodifiableList(components);
    }

    /** The title the window shows now. */
    @Nonnull
    public String getCurrentTitle() {
        return currentTitle;
    }

    public boolean isClosed() {
        return closed;
    }

    /** Whether it is set aside: it holds its state and tasks but has no window - a screen on top of it, or a prompt, has. */
    public boolean isSuspended() {
        return suspended;
    }

    /** Whether this view is the one drawn on the renderer's {@code nativeWindow}. */
    public boolean isSurface(@Nullable Object nativeWindow) {
        return nativeWindow != null && surface != null && surface.isBackedBy(nativeWindow);
    }

    /** The event sources this view holds needed right now. */
    @Nonnull
    public Set<EventSource> getHeldSources() {
        Set<EventSource> held = new LinkedHashSet<>();
        synchronized (holds) {
            for (UsageHold hold : holds) {
                held.add(hold.getSource());
            }
        }
        return held;
    }

    /** The icon painted at {@code slot} - the topmost layer that put one there - or {@code null}. */
    @Nullable
    public Icon getIconAt(int slot) {
        Painted painted = paintedAt(slot);
        return painted == null ? null : painted.icon;
    }

    /** What painted {@code slot} - element id, kind, click - or {@code null} when nothing did. */
    @Nullable
    public Paint getPaintAt(int slot) {
        Painted painted = paintedAt(slot);
        return painted == null ? null : painted.paint;
    }

    @Nullable
    private Painted paintedAt(int slot) {
        if (!grid.isInside(slot)) {
            return null;
        }
        for (Map.Entry<Integer, Painted[]> entry : paintLayers.descendingMap().entrySet()) {
            Painted painted = entry.getValue()[slot];
            if (painted != null) {
                return painted;
            }
        }
        return null;
    }

    /** The policy ruling {@code slot}: the editable area's when one holds it, then a region's, then the screen's. */
    @Nonnull
    public ClickPolicy getPolicyAt(int slot) {
        StorageBinding storage = getStorageAt(slot);
        if (storage != null) {
            return storage.getPolicy();
        }
        if (slotRegions != null && slot >= 0 && slot < slotRegions.length && slotRegions[slot] != null) {
            return slotRegions[slot].getPolicy();
        }
        return screen.getPolicy();
    }

    /** The editable area {@code slot} belongs to, or {@code null} when the screen draws it. */
    @Nullable
    public StorageBinding getStorageAt(int slot) {
        if (slotStorages == null || slot < 0 || slot >= slotStorages.length) {
            return null;
        }
        return slotStorages[slot];
    }

    public boolean hasStorage() {
        return !screen.getStorages().isEmpty();
    }

    /** The icon whose handler asked for the close, or {@code null} - which is what the escape key looks like. */
    @Nullable
    public Icon getClosedBy() {
        return closedBy;
    }

    void markClosedBy(@Nullable Icon icon) {
        if (!closed) {
            this.closedBy = icon;
        }
    }

    /**
     * A context for logic run on behalf of this view: the screen's owner as the caller (the core when it has none), the
     * viewer as the player, this view as the attribute {@code ScreenView.class.getName()}.
     */
    @Nonnull
    public ExecutionContext.Builder newExecutionContext() {
        ECPluginData owner = screen.getOwner() != null ? screen.getOwner() : EverNifeCore.getEcPluginData();
        return ExecutionContext.builder(Caller.of(owner, "screen:" + screen.describe()))
                .player(viewer)
                .attribute(ScreenView.class.getName(), this);
    }

    /** Whether {@code condition} passes for the viewer right now. */
    public boolean test(@Nonnull Condition condition) {
        return Conditions.test(condition, newExecutionContext().build()).isPassed();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Painting
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Paints {@code icon} over every slot of {@code slots}, on {@code layer}, as {@code paint} says ({@code null}: a
     * decoration). An icon the viewer may not see paints nothing and erases nothing; a slot an editable area holds is
     * skipped whole. Renderer thread.
     */
    public void paint(int layer, @Nonnull SlotSet slots, @Nonnull Icon icon, @Nullable Paint paint) {
        if (!icon.isVisibleTo(viewer)) {
            return;
        }
        //text and state are resolved here, not when the icon was declared: this is the first moment the viewer is known
        RenderedIcon rendered = icon.renderFor(viewer, icon.getCurrentState(), screen.getReplacer(), argumentsOrNull());
        Painted painted = null;
        for (int slot : slots.resolve(grid).toArray()) {
            if (!grid.isInside(slot)) {
                EverNifeCore.getLog().warning("A screen icon was bound to slot " + slot + ", outside a " + grid
                        + ". The icon was skipped.");
                continue;
            }
            if (!buffer.owns(slot)) {
                continue;
            }
            if (painted == null) {
                painted = new Painted(icon, paint != null ? paint
                        : Paint.decoration(icon.getName() != null ? icon.getName() : "slot#" + slot));
            }
            buffer.write(layer, slot, rendered);
            paintsOf(layer)[slot] = painted;
        }
    }

    /** Erases one layer: its icons and what painted them, everywhere. */
    public void clearLayer(int layer) {
        buffer.clearLayer(layer);
        paintLayers.remove(layer);
    }

    private Painted[] paintsOf(int layer) {
        Painted[] paints = paintLayers.get(layer);
        if (paints == null) {
            paints = new Painted[grid.getSize()];
            paintLayers.put(layer, paints);
        }
        return paints;
    }

    /** Renders everything now: the layout's own icons, then every component. Renderer thread. */
    public void render() {
        renderLayoutIcons();
        for (ScreenComponent component : components) {
            component.renderNow();
        }
        dirtyComponents.clear();
    }

    private void renderLayoutIcons() {
        layoutIconsDirty = false;
        slotRegions = new NamedRegion[grid.getSize()];
        for (NamedRegion region : screen.getRegions().values()) {
            for (int slot : region.getSlots().resolve(grid).toArray()) {
                if (grid.isInside(slot)) {
                    slotRegions[slot] = region;
                }
            }
        }
        clearLayer(SlotBuffer.LAYER_BACKGROUND);
        clearLayer(SlotBuffer.LAYER_CONTENT);
        for (Screen.IconBinding binding : screen.getIconBindings(layout)) {
            Icon icon = binding.getIcon();
            paint(icon.isBackground() ? SlotBuffer.LAYER_BACKGROUND : SlotBuffer.LAYER_CONTENT, binding.getSlots(), icon, null);
        }
    }

    /** Marks everything for a redraw on the next tick. The commit still writes only what changed. Any thread. */
    public void refresh() {
        if (closed) {
            return;
        }
        layoutIconsDirty = true;
        dirtyComponents.addAll(components);
        scheduleCommit();
    }

    /** Marks one component for a redraw on the next tick. Any thread. */
    public void markComponentDirty(@Nonnull ScreenComponent component) {
        if (closed) {
            return;
        }
        dirtyComponents.add(component);
        scheduleCommit();
    }

    /**
     * Asks for a pass on the next tick, once: every change made in the same tick lands in the same write, and a screen
     * nobody is changing schedules nothing. Any thread.
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
            if (layoutIconsDirty) {
                renderLayoutIcons();
            }
            if (!dirtyComponents.isEmpty()) {
                List<ScreenComponent> pending = new ArrayList<>(dirtyComponents);
                dirtyComponents.removeAll(pending);
                for (ScreenComponent component : pending) {
                    component.renderNow();
                }
            }
            applyTitleIfChanged();
            commitNow();
        } catch (Throwable e) {
            EverNifeCore.getLog().severe("A screen render pass failed for [{}]", viewerName, e);
        }
    }

    private void applyTitleIfChanged() {
        String wanted = screen.getTitleFor(viewer, argumentsOrNull());
        if (wanted.equals(currentTitle) || !renderer.getCapabilities().contains(RendererCapability.TITLE_CHANGE)) {
            return;
        }
        surface.setTitle(wanted);
        currentTitle = wanted;
    }

    /**
     * Sends the slots whose rendered output changed to the surface, right now, in one {@link ScreenSurface#apply} call.
     *
     * @return how many slots were sent
     * @throws IllegalStateException off the renderer thread, naming the thread that tried
     */
    public int commitNow() {
        if (closed || suspended || surface == null) {
            return 0;
        }
        if (!scheduler.isRenderThread()) {
            throw new IllegalStateException("A screen commit was attempted on thread [" + Thread.currentThread().getName()
                    + "]. Every write to a screen happens on its renderer's thread - hop with view.getScheduler().run(...) "
                    + "before touching it.");
        }
        List<Integer> changed = buffer.commit();
        if (changed.isEmpty()) {
            return 0;
        }
        List<ElementPatch> patch = new ArrayList<>(changed.size());
        for (int slot : changed) {
            Painted painted = paintedAt(slot);
            Paint paint = painted == null ? Paint.decoration("slot#" + slot) : painted.paint;
            patch.add(new ElementPatch(paint.getElementId(), paint.getKind(), slot, buffer.getCommitted(slot),
                    paint.getProgress(), paint.isEnabled(), paint.getReason()));
        }
        surface.apply(Collections.unmodifiableList(patch));
        return patch.size();
    }

    /** Re-reads a grid surface and puts back whatever stopped showing what this screen drew. Renderer thread. */
    public void resync() {
        if (closed || suspended || !(surface instanceof GridSurface)) {
            return;
        }
        GridSurface gridSurface = (GridSurface) surface;
        buffer.adoptContainer(gridSurface::read);
        commitNow();
    }

    /** What puts the opener's arguments into a text, or {@code null} when it passed none. */
    @Nullable
    private UnaryOperator<String> argumentsOrNull() {
        return arguments.isEmpty() ? null : this::withArguments;
    }

    /** {@code {0}}..{@code {n}} replaced by the opener's arguments. */
    @Nonnull
    public String withArguments(@Nullable String text) {
        if (text == null || arguments.isEmpty() || text.indexOf('{') < 0) {
            return text == null ? "" : text;
        }
        String resolved = text;
        for (int i = 0; i < arguments.size(); i++) {
            resolved = resolved.replace("{" + i + "}", String.valueOf(arguments.get(i)));
        }
        return resolved;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Scheduling, subscriptions and holds owned by this view
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

    /** Takes a watch onto the view's clock: one task ticks every watch, and exists only while one does. */
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
                EverNifeCore.getLog().severe("A screen watch failed for [{}]", viewerName, e);
            }
        }
    }

    /**
     * Redraws {@code component} when an action of one of {@code sources} happens with the viewer as the actor. The view
     * holds each source needed ({@link EventSourceUsage#acquire}) until it is released - asking "does anybody listen?"
     * is answered by acting, so the producer of each source stays registered exactly while somebody looks.
     */
    void listen(@Nonnull ScreenComponent component, @Nonnull Set<EventSource> sources) {
        if (closed) {
            return;
        }
        for (EventSource source : sources) {
            Set<ScreenComponent> dependents = listeners.get(source);
            if (dependents == null) {
                dependents = new CopyOnWriteArraySet<>();
                listeners.put(source, dependents);
                synchronized (holds) {
                    holds.add(EventSourceUsage.get().acquire(source));
                }
                ActionEvents.Pair pair = ActionEvents.of(source);
                if (pair != null) {
                    eventSubscriptions.add(ECEventBus.global().subscribe(pair.getPost(), this::onAction));
                }
            }
            dependents.add(component);
        }
    }

    private void onAction(ActionEvent<?> event) {
        Set<ScreenComponent> dependents = listeners.get(event.getSource());
        FPlayer actor = event.getPlayer();
        if (dependents == null || actor == null || !viewerId.equals(actor.getUniqueId())) {
            return;
        }
        for (ScreenComponent component : dependents) {
            markComponentDirty(component);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Clicks
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * A click on {@code slot}, as the renderer read it. Runs the policy, the debounce and the handler of what is painted
     * there, in that order. Renderer thread.
     *
     * @return whether the item move the gesture would make may go through - a policy that allows it, or a handler that
     *         {@link ClickContext#allowMove() allowed} it
     */
    public boolean click(int slot, @Nonnull ClickGesture gesture, @Nonnull ClickKind kind, @Nullable ItemSpec cursor) {
        if (closed || suspended || !grid.isInside(slot)) {
            return false;
        }
        boolean allowed = getPolicyAt(slot).allowsKind(gesture, kind);
        Painted painted = paintedAt(slot);
        if (painted == null || painted.paint.getOnClick() == null) {
            return allowed;
        }
        long now = System.currentTimeMillis();
        if (isWithinDebounce(now)) {
            return allowed;
        }
        markClickAccepted(now);
        ClickContext context = new ClickContext(this, nextClickToken(), slot, gesture, kind, cursor, painted.paint, painted.icon);
        try {
            painted.paint.getOnClick().accept(context);
        } catch (Throwable e) {
            EverNifeCore.getLog().severe("A screen click handler failed for [{}] on slot {}", viewerName, slot, e);
        }
        return allowed || context.isMoveAllowed();
    }

    /**
     * A click on the element {@code elementId} - the form a renderer that draws by element uses. Lands on the first slot
     * that element painted; an id nothing painted is ignored. Renderer thread.
     */
    public boolean click(@Nonnull String elementId, @Nonnull ClickGesture gesture) {
        for (int slot = 0; slot < grid.getSize(); slot++) {
            Painted painted = paintedAt(slot);
            if (painted != null && painted.paint.getElementId().equals(elementId)) {
                return click(slot, gesture, ClickKind.NOTHING, null);
            }
        }
        return false;
    }

    long nextClickToken() {
        return ++clickToken;
    }

    /** Whether {@code token} is still the current click and the view is still open. */
    public boolean isTokenAlive(long token) {
        return !closed && token == clickToken;
    }

    boolean isWithinDebounce(long nowMillis) {
        long debounce = screen.getDebounceMillis();
        return debounce > 0 && (nowMillis - lastAcceptedClickAt) < debounce;
    }

    void markClickAccepted(long nowMillis) {
        this.lastAcceptedClickAt = nowMillis;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Asking for a value
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Asks the viewer through {@link PromptRegistry}: completes with the value, or is cancelled when no value came
     * (cancelled, timed out, left, or no channel serves the viewer). Any thread.
     */
    @Nonnull
    public <T> CompletableFuture<T> ask(@Nonnull PromptSpec<T> spec) {
        CompletableFuture<T> answered = new CompletableFuture<>();
        PromptRegistry.get().ask(this, spec).thenAccept(result -> {
            if (result.hasValue()) {
                answered.complete(result.getValue());
            } else {
                answered.cancel(false);
            }
        });
        return answered;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Suspending - the screen outlives its window while another one, or a prompt, has the player
    // -----------------------------------------------------------------------------------------------------------------

    void suspend() {
        if (closed || suspended) {
            return;
        }
        suspended = true;
        cancelPendingCommit();
        if (surface != null) {
            surface.suspended();
        }
    }

    private synchronized void cancelPendingCommit() {
        if (pendingCommit != null) {
            pendingCommit.cancel();
            pendingCommit = null;
        }
    }

    /** Brings the screen back onto a window it has never written into, so everything is drawn again. */
    void resume(@Nonnull ScreenSurface newSurface) {
        if (closed) {
            return;
        }
        suspended = false;
        this.surface = newSurface;
        buffer.forgetCommitted();
    }

    /** The title it would open with right now - what a renderer gives the next window. */
    @Nonnull
    String resolveTitle() {
        currentTitle = screen.getTitleFor(viewer, argumentsOrNull());
        return currentTitle;
    }

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

    /** Asks the renderer to take the window away; the renderer reports it gone and the view is released then. */
    public void close() {
        if (!closed && !suspended && surface != null) {
            surface.close(CloseReason.REQUESTED);
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
     * Tears the view down: tasks cancelled, subscriptions and holds dropped (holds in reverse order), the surface told
     * ({@link ScreenSurface#released}), {@code onClose} run, {@link ScreenCloseEvent} posted, the viewer released.
     * Idempotent.
     */
    void release(@Nonnull CloseReason reason) {
        if (closed) {
            return;
        }
        closed = true;
        FPlayer releasedViewer = this.viewer;

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
        for (ECEventSubscription<?> subscription : eventSubscriptions) {
            subscription.unsubscribe();
        }
        eventSubscriptions.clear();
        synchronized (holds) {
            for (int i = holds.size() - 1; i >= 0; i--) {
                holds.get(i).release();
            }
            holds.clear();
        }
        listeners.clear();
        dirtyComponents.clear();

        if (surface != null) {
            try {
                surface.released(reason);
            } catch (Throwable e) {
                EverNifeCore.getLog().severe("The window of a screen failed to give back what it held for [{}]", viewerName, e);
            }
        }
        //before onClose, so a handler that opens another screen finds no question left over from this one
        PromptRegistry.get().abandon(this);
        try {
            fireOnClose(reason);
        } catch (Throwable e) {
            EverNifeCore.getLog().severe("The onClose handler of a screen failed for [{}]", viewerName, e);
        }
        ECEventBus.global().postIfListened(ScreenCloseEvent.class, () -> new ScreenCloseEvent(this, releasedViewer, reason));

        this.viewer = null;
        this.slotRegions = null;
        this.slotStorages = null;
        this.paintLayers.clear();
        this.components.clear();
    }

    //the handler was registered on a Screen<L>, and this view only knows Screen<?> - the layout the context hands back is
    //the one that screen was built from either way
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void fireOnClose(CloseReason reason) {
        Consumer handler = screen.getOnClose();
        if (handler != null) {
            handler.accept(new CloseContext<>(this, viewer, reason));
        }
    }

    @Override
    public String toString() {
        return "ScreenView{" + screen.describe() + ", viewer=" + viewerName + ", renderer=" + renderer.getId()
                + (closed ? ", closed" : suspended ? ", suspended" : "") + "}";
    }

    /** An icon on a slot and what painted it. */
    private static final class Painted {

        private final Icon icon;
        private final Paint paint;

        private Painted(Icon icon, Paint paint) {
            this.icon = icon;
            this.paint = paint;
        }

    }

}
