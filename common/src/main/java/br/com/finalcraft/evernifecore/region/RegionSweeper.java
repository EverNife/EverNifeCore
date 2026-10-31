package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.region.event.RegionRemovedEvent;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.GroupManager;
import br.com.finalcraft.evernifecore.time.ECClock;
import br.com.finalcraft.evernifecore.time.ECScheduler;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The one sweeper of everything with a deadline: regions, temporary flag values, timed memberships, invites, the groups
 * of every {@link GroupManager#all() GroupManager} (and their live roster and kick rules, sampled at every pass). It
 * runs on the region scheduler only while something has a deadline, and applies each world's expirations on the thread
 * that owns that world, so the events they cause go out where a world's events belong.
 */
public final class RegionSweeper {

    public static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(1);

    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    /** Worlds whose thread refused work; each is logged once until it accepts again. */
    private static final Set<String> UNREACHABLE = ConcurrentHashMap.newKeySet();
    private static volatile long intervalMillis = DEFAULT_INTERVAL.toMillis();
    private static volatile boolean woken;

    private RegionSweeper() {
    }

    /** Internal: something gained a deadline (here or in a {@link GroupManager}): starts the sweep when it is not running. */
    public static void wake() {
        woken = true;
        if (RUNNING.compareAndSet(false, true)) {
            ECScheduler.scheduleAsync(RegionSweeper::tick, intervalMillis);
        }
    }

    static boolean isRunning() {
        return RUNNING.get();
    }

    static void tick() {
        woken = false;
        Instant now = ECClock.now();
        Map<String, List<Region>> due = new HashMap<>();
        boolean pending = false;
        for (RegionManager manager : RegionManager.all()) {
            for (Region region : manager.getAll()) {
                Instant next = nextDeadline(region);
                if (next == null) {
                    continue;
                }
                if (now.isBefore(next)) {
                    pending = true;
                } else {
                    due.computeIfAbsent(region.getWorld(), world -> new ArrayList<>()).add(region);
                }
            }
        }
        //a group of a manager lives in no world: it is swept on this thread, and its events go out from here
        for (GroupManager manager : GroupManager.all()) {
            for (Group group : manager.getAll()) {
                pending |= sweep(group, now);
            }
        }
        due.forEach((world, regions) -> {
            CompletableFuture<Void> applied;
            try {
                applied = EverNifeCore.getPlatform().runOnWorldThread(world, () -> {
                    for (Region region : regions) {
                        expire(region, now);
                    }
                });
            } catch (RuntimeException refused) {
                applied = new CompletableFuture<>();
                applied.completeExceptionally(refused);
            }
            applied.whenComplete((ignored, error) -> {
                if (error == null) {
                    UNREACHABLE.remove(world);
                } else if (UNREACHABLE.add(world)) {
                    ECDebugModule.REGIONS.debug("Expirations in world '{}' wait for the next sweep: {}", world, error.toString());
                }
            });
        });
        //what expired may uncover a later deadline, and a world that refused keeps its own: one more look either way
        if (pending || !due.isEmpty()) {
            ECScheduler.scheduleAsync(RegionSweeper::tick, intervalMillis);
            return;
        }
        RUNNING.set(false);
        if (woken && RUNNING.compareAndSet(false, true)) {
            ECScheduler.scheduleAsync(RegionSweeper::tick, intervalMillis);
        }
    }

    private static void expire(Region region, Instant now) {
        if (!region.isAttached()) {
            return;
        }
        Instant deadline = region.getExpiresAt();
        if (deadline != null && !now.isBefore(deadline)) {
            ECDebugModule.REGIONS.debug("Region {} expired (deadline {}, swept at {}).", region.getId(), deadline, now);
            region.getManager().remove(region, RemoveStrategy.ORPHAN_CHILDREN, RegionRemovedEvent.Cause.EXPIRED);
            return;
        }
        region.getFlagStore().expire(now);
        for (Group group : region.getGroups().values()) {
            group.expire(now);
        }
    }

    /** Expires what is due in {@code group} and samples its live rules; whether it needs another pass later. */
    private static boolean sweep(Group group, Instant now) {
        Instant next = group.getNextDeadline();
        if (next != null && !now.isBefore(next)) {
            group.expire(now);
        }
        if (group.isSampled()) {
            group.sample();
        }
        return !group.isDissolved() && (group.getNextDeadline() != null || group.isSampled());
    }

    private static @Nullable Instant nextDeadline(Region region) {
        Instant next = earliest(region.getExpiresAt(), region.getFlagStore().getNextDeadline());
        for (Group group : region.getGroups().values()) {
            next = earliest(next, group.getNextDeadline());
        }
        return next;
    }

    private static @Nullable Instant earliest(@Nullable Instant a, @Nullable Instant b) {
        return a == null ? b : b == null || a.isBefore(b) ? a : b;
    }

    /** How often the sweeper looks for expired deadlines. Wired from the core config at boot and reload; tests set it directly. */
    public static void setInterval(Duration interval) {
        intervalMillis = interval.toMillis();
    }

    /** Forgets the running state - the core's shutdown, and tests that swapped the scheduler. */
    static void reset() {
        RUNNING.set(false);
        woken = false;
        UNREACHABLE.clear();
        intervalMillis = DEFAULT_INTERVAL.toMillis();
    }
}
