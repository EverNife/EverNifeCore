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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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

    private static volatile @Nullable EventSourceUsage global;

    private final RegionLookup lookup;
    // ponytail: the Count of a source its plugin unregistered stays here at zero - harmless; dropping it would tie
    // EventSource.unregisterAll to this singleton
    private final ConcurrentHashMap<EventSource, Count> counts = new ConcurrentHashMap<>();
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
        Count count = counts.get(source);
        return count != null && count.needed.get() == 1;
    }

    /** How many indexed regions hold a value of a flag that needs {@code source}. */
    public synchronized int getUses(EventSource source) {
        Count count = counts.get(source);
        return count == null ? 0 : count.uses;
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
            countOf(source).holds++;
            transitions = settle();
        }
        transitions.forEach(Runnable::run);
        return new UsageHold(this, source);
    }

    void release(EventSource source) {
        List<Runnable> transitions;
        synchronized (this) {
            countOf(source).holds--;
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
        Map<EventSource, Integer> counted = new HashMap<>();
        Set<EventSource> seen = new HashSet<>();
        for (Region region : lookup.all()) {
            seen.clear();
            collect(region.getFlags().keySet(), seen);
            for (AccessGroup group : region.getGroups().values()) {
                collect(group.getFlags().keySet(), seen);
            }
            for (EventSource source : seen) {
                counted.merge(source, 1, Integer::sum);
            }
        }
        List<Runnable> transitions;
        synchronized (this) {
            for (Count count : counts.values()) {
                count.uses = 0;
            }
            counted.forEach((source, uses) -> countOf(source).uses = uses);
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
        for (Map.Entry<EventSource, Count> entry : counts.entrySet()) {
            EventSource source = entry.getKey();
            Count count = entry.getValue();
            boolean now = !source.equals(EventSource.NONE) && (count.uses > 0 || count.holds > 0);
            if (now == (count.needed.get() == 1)) {
                continue;
            }
            count.needed.set(now ? 1 : 0);
            if (transitions == null) {
                transitions = new ArrayList<>(2);
            }
            if (now) {
                transitions.add(() -> becomeAll(source, true));
            } else {
                long generation = ++count.idleGeneration;
                transitions.add(() -> RegionScheduler.scheduleAsync(() -> idleIfStill(source, generation), idleTime.toMillis()));
            }
        }
        return transitions == null ? new ArrayList<>(0) : transitions;
    }

    /** Under the lock. */
    private Count countOf(EventSource source) {
        return counts.computeIfAbsent(source, key -> new Count());
    }

    private void idleIfStill(EventSource source, long generation) {
        synchronized (this) {
            //needed again meanwhile, or unneeded again later with a wait of its own: this wait is void
            if (countOf(source).idleGeneration != generation || isNeeded(source)) {
                return;
            }
        }
        becomeAll(source, false);
    }

    private void becomeAll(EventSource source, boolean isNeeded) {
        for (UsageWatch watch : watches) {
            if (watch.getSource().equals(source)) {
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

    private static final class Count {
        int uses;            //indexed regions holding a value of a flag that needs the source
        int holds;           //acquire() holds
        long idleGeneration; //bumps when the source goes unneeded; an idle wait of an older generation is void
        final AtomicInteger needed = new AtomicInteger(); //1 while needed; read lock-free on every protected event
    }
}
