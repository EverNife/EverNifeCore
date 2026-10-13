package br.com.finalcraft.evernifecore.region.usage;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.eventbus.ECListenerWatch;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionScheduler;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.event.RegionEnterEvent;
import br.com.finalcraft.evernifecore.region.event.RegionFlagChangedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionLeaveEvent;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.Consumer;

/**
 * Which kinds of game event some region needs heard, so a platform listens to a kind only while it matters. A
 * source is needed while an indexed region holds a value - its own or a group's - of a flag that needs it, or while
 * something else holds it ({@link #acquire}); MOVE is also held while anyone listens to region enter or leave events.
 *
 * <p>The answer is an action, not only a question: {@link #watch} runs {@code onNeeded} when a source becomes needed
 * and {@code onIdle} once it has stayed unneeded for {@link #getIdleTime()} - a source that comes back in the
 * meantime fires nothing, so a region recreated in a loop does not churn listeners.</p>
 */
public final class EventSourceUsage {

    public static final Duration DEFAULT_IDLE_TIME = Duration.ofMinutes(3);
    private static final EventSource[] SOURCES = EventSource.values();

    private static volatile @Nullable EventSourceUsage global;

    private final RegionLookup lookup;
    private final int[] uses = new int[SOURCES.length];
    private final int[] holds = new int[SOURCES.length];
    private final long[] idleGeneration = new long[SOURCES.length];
    /** 1 when needed; read without a lock on every protected event. */
    private final AtomicIntegerArray needed = new AtomicIntegerArray(SOURCES.length);
    private final List<UsageWatch> watches = new CopyOnWriteArrayList<>();
    private final Consumer<String> publishListener = world -> recount();
    private final Runnable registryListener = this::recount;
    private final ECEventSubscription<RegionFlagChangedEvent> flagSubscription;
    private final ECListenerWatch moveWatch;
    private volatile Duration idleTime = DEFAULT_IDLE_TIME;
    private volatile @Nullable UsageHold moveHold;

    /** A count over {@code lookup}'s regions that follows the listeners of {@code bus}. */
    EventSourceUsage(RegionLookup lookup, ECEventBus bus) {
        this.lookup = lookup;
        lookup.addPublishListener(publishListener);
        flagSubscription = bus.subscribe(RegionFlagChangedEvent.class, event -> recount());
        FlagRegistry.get().addChangeListener(registryListener);
        recount();
        //last: the bus fires the first callback at once when someone already listens, and every field must be ready
        moveWatch = bus.watchListeners(() -> moveHold = acquire(EventSource.MOVE), () -> {
            UsageHold hold = moveHold;
            moveHold = null;
            if (hold != null) {
                hold.release();
            }
        }, RegionEnterEvent.class, RegionLeaveEvent.class);
    }

    /** The count of every indexed region, following the global bus; created on first use. */
    public static EventSourceUsage get() {
        EventSourceUsage usage = global;
        if (usage == null) {
            synchronized (EventSourceUsage.class) {
                usage = global;
                if (usage == null) {
                    usage = new EventSourceUsage(RegionLookup.get(), ECEventBus.global());
                    global = usage;
                }
            }
        }
        return usage;
    }

    /** Internal: stops the global count, if one was created; the next {@link #get()} starts a fresh one. */
    public static synchronized void shutdown() {
        EventSourceUsage usage = global;
        global = null;
        if (usage != null) {
            usage.stop();
        }
    }

    /** Whether events of {@code source} must be heard now. Lock-free; {@link EventSource#NONE} never is. */
    public boolean isNeeded(EventSource source) {
        return needed.get(source.ordinal()) == 1;
    }

    /** How many indexed regions hold a value of a flag that needs {@code source}. */
    public synchronized int getUses(EventSource source) {
        return uses[source.ordinal()];
    }

    public Duration getIdleTime() {
        return idleTime;
    }

    /** How long a source stays unneeded before {@code onIdle} runs; a negative time counts as zero. */
    public void setIdleTime(Duration idleTime) {
        this.idleTime = idleTime.isNegative() ? Duration.ZERO : idleTime;
    }

    /** Keeps {@code source} needed until the hold is released - for a need that is not a region's, like a selection in progress. */
    public UsageHold acquire(EventSource source) {
        List<Runnable> transitions;
        synchronized (this) {
            holds[source.ordinal()]++;
            transitions = settle();
        }
        transitions.forEach(Runnable::run);
        return new UsageHold(this, source);
    }

    void release(EventSource source) {
        List<Runnable> transitions;
        synchronized (this) {
            holds[source.ordinal()]--;
            transitions = settle();
        }
        transitions.forEach(Runnable::run);
    }

    /**
     * Runs {@code onNeeded} whenever {@code source} becomes needed - at once when it already is - and {@code onIdle}
     * once it has stayed unneeded for {@link #getIdleTime()}. One callback per transition, through
     * {@code IPlatform.runOnMainThread} because they register native listeners and have no world of their own; a
     * callback that throws is logged and the watch keeps the state it moved to.
     */
    public UsageWatch watch(ECPluginData owner, EventSource source, Runnable onNeeded, Runnable onIdle) {
        UsageWatch watch = new UsageWatch(this, owner, source, onNeeded, onIdle);
        watches.add(watch);
        run(watch, watch.become(isNeeded(source)));
        return watch;
    }

    void forget(UsageWatch watch) {
        watches.remove(watch);
    }

    /**
     * Counts again, from scratch, which sources the indexed regions need. Runs after every snapshot publish, flag
     * change and flag registration.
     */
    public void recount() {
        // ponytail: a full recount per change is O(regions x flags); count per region incrementally if a profiler ever asks
        int[] counted = new int[SOURCES.length];
        Set<EventSource> seen = EnumSet.noneOf(EventSource.class);
        for (Region region : lookup.all()) {
            seen.clear();
            collect(region.getFlags().keySet(), seen);
            for (AccessGroup group : region.getGroups().values()) {
                collect(group.getFlags().keySet(), seen);
            }
            for (EventSource source : seen) {
                counted[source.ordinal()]++;
            }
        }
        List<Runnable> transitions;
        synchronized (this) {
            System.arraycopy(counted, 0, uses, 0, counted.length);
            transitions = settle();
        }
        transitions.forEach(Runnable::run);
    }

    private static void collect(Set<FlagKey> keys, Set<EventSource> into) {
        for (FlagKey key : keys) {
            RegionFlag<?> flag = FlagRegistry.get().get(key);
            if (flag != null) {
                into.addAll(flag.getNeeds());
            }
        }
    }

    /** Holds the lock: flips what changed and returns the callbacks to run outside it. */
    private List<Runnable> settle() {
        List<Runnable> transitions = null;
        for (EventSource source : SOURCES) {
            int index = source.ordinal();
            boolean now = source != EventSource.NONE && (uses[index] > 0 || holds[index] > 0);
            if (now == (needed.get(index) == 1)) {
                continue;
            }
            needed.set(index, now ? 1 : 0);
            if (transitions == null) {
                transitions = new ArrayList<>(2);
            }
            if (now) {
                transitions.add(() -> becomeAll(source, true));
            } else {
                long generation = ++idleGeneration[index];
                transitions.add(() -> RegionScheduler.scheduleAsync(() -> idleIfStill(source, generation), idleTime.toMillis()));
            }
        }
        return transitions == null ? new ArrayList<>(0) : transitions;
    }

    private void idleIfStill(EventSource source, long generation) {
        synchronized (this) {
            //needed again meanwhile, or unneeded again later with a wait of its own: this wait is void
            if (idleGeneration[source.ordinal()] != generation || isNeeded(source)) {
                return;
            }
        }
        becomeAll(source, false);
    }

    private void becomeAll(EventSource source, boolean isNeeded) {
        for (UsageWatch watch : watches) {
            if (watch.getSource() == source) {
                run(watch, watch.become(isNeeded));
            }
        }
    }

    /**
     * The one hop to the main thread in the region system: a callback registers or drops a native listener, which a
     * platform wants on its main thread, and a source has no world to pick a thread by.
     */
    private static void run(UsageWatch watch, @Nullable Runnable callback) {
        if (callback == null) {
            return;
        }
        EverNifeCore.getPlatform().runOnMainThread(() -> {
            try {
                callback.run();
            } catch (Throwable failure) {
                watch.getOwner().getLog().severe("The {} usage watch failed in its callback; it keeps the state it moved to.",
                        watch.getSource(), failure);
            }
        });
    }

    /** Lets go of the index, the bus and the registry, and stops every watch: nothing fires any more. */
    public void stop() {
        lookup.removePublishListener(publishListener);
        flagSubscription.unsubscribe();
        FlagRegistry.get().removeChangeListener(registryListener);
        moveWatch.stop();
        for (UsageWatch watch : new ArrayList<>(watches)) {
            watch.stop();
        }
    }
}
