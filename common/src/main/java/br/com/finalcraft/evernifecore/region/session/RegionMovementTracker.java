package br.com.finalcraft.evernifecore.region.session;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.FlagTriggers;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.event.RegionChangedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionEnterEvent;
import br.com.finalcraft.evernifecore.region.event.RegionFlagChangedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionGroupChangedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionLeaveEvent;
import br.com.finalcraft.evernifecore.region.flag.MoveContext;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Follows every online player across region borders. The platform hands each move in with the position it already
 * has - the tracker never asks a player where they are - and applies the verdict: an allowed move happens, a denied
 * one puts the player at {@link MoveVerdict#getRevertTo()}.
 *
 * <p>For a move into another set of regions the movement flags of each region entered or left decide first; then
 * {@link RegionLeaveEvent}s go out, then {@link RegionEnterEvent}s, and a listener may still cancel a cancellable
 * crossing. Only an allowed, uncancelled move changes what the tracker knows about the player. Regions created or
 * removed under a player who stands still reach them too, as non-cancellable crossings.</p>
 *
 * <p><b>Threads:</b> {@code onMove}, {@code onJoin} and {@code onQuit} run on the thread of the player's world, where
 * the platform's event already is; they never block. The work a snapshot publish causes is moved to the thread of
 * that snapshot's world.</p>
 */
public final class RegionMovementTracker {

    private static volatile @Nullable RegionMovementTracker global;

    private final RegionLookup lookup;
    private final ECEventBus bus;
    private final SessionFlags sessionFlags;
    private final List<ECEventSubscription<?>> subscriptions = new ArrayList<>();
    private final Consumer<String> publishListener = this::onSnapshotPublished;
    /** Call sites already warned about running off their world's thread. */
    private final Set<String> warnedSites = ConcurrentHashMap.newKeySet();
    private final Set<String> unreachableWorlds = ConcurrentHashMap.newKeySet();

    /** A tracker over {@code lookup}'s sessions that posts on and listens to {@code bus}. */
    RegionMovementTracker(RegionLookup lookup, ECEventBus bus) {
        this.lookup = lookup;
        this.bus = bus;
        this.sessionFlags = new SessionFlags(lookup);
        subscriptions.add(bus.subscribe(ECPlayerQuitEvent.class, event -> onQuit(event.getPlayerData().getUniqueId())));
        //what a region change means for the session flags of the players under it
        subscriptions.add(bus.subscribe(RegionFlagChangedEvent.class, event -> invalidateUnder(event.getRegion())));
        subscriptions.add(bus.subscribe(RegionGroupChangedEvent.class, event -> invalidateUnder(event.getRegion())));
        subscriptions.add(bus.subscribe(RegionChangedEvent.class, event -> {
            if (event.getType() == RegionChangedEvent.Type.FLAG_SETTINGS) {
                invalidateUnder(event.getRegion());
            }
        }));
        lookup.addPublishListener(publishListener);
        lookup.setRevalidationListener(session -> sessionFlags.wake());
    }

    /** The tracker of every indexed region, on the global bus; created on first use. */
    public static RegionMovementTracker get() {
        RegionMovementTracker tracker = global;
        if (tracker == null) {
            synchronized (RegionMovementTracker.class) {
                tracker = global;
                if (tracker == null) {
                    tracker = new RegionMovementTracker(RegionLookup.get(), ECEventBus.global());
                    global = tracker;
                }
            }
        }
        return tracker;
    }

    /** Internal: stops the global tracker, if one was created; the next {@link #get()} starts a fresh one. */
    public static void shutdown() {
        RegionMovementTracker tracker;
        synchronized (RegionMovementTracker.class) {
            tracker = global;
            global = null;
        }
        if (tracker != null) {
            tracker.stop();
        }
    }

    /** Lets go of the bus and the index: this tracker hears nothing more. */
    void stop() {
        subscriptions.forEach(ECEventSubscription::unsubscribe);
        lookup.removePublishListener(publishListener);
        lookup.setRevalidationListener(null);
    }

    SessionFlags getSessionFlags() {
        return sessionFlags;
    }

    /**
     * The player moves from {@code from} to {@code to}. Thread: the player's world; never blocks.
     *
     * @return {@link MoveVerdict#allowed()}, or where to put the player instead - then nothing changed
     */
    public MoveVerdict onMove(FPlayer player, WorldLocPos from, WorldLocPos to, MoveCause cause) {
        warnIfOffThread("onMove", to.getWorldName());
        RegionPlayerSession session = lookup.getSession(player);
        session.setOnlinePlayer(player);
        if (session.isAt(to)) {
            return MoveVerdict.allowed();
        }
        List<Region> next = lookup.at(to);
        List<Region> current = session.getCurrent();
        if (next.isEmpty() && current.isEmpty()) {
            session.moveTo(to);
            return MoveVerdict.allowed();
        }
        List<Region> exited = minus(current, next);
        List<Region> entered = minus(next, current);
        if (exited.isEmpty() && entered.isEmpty()) {
            session.moveTo(to);
            sessionFlags.apply(player, session, SessionFlags.Trigger.LOOKUP);
            return MoveVerdict.allowed();
        }
        if (cause.isCancellable()) {
            for (Region region : exited) {
                MoveVerdict verdict = FlagTriggers.decideCrossing(lookup, new MoveContext(player, from, to, cause, MoveContext.Transition.LEAVE, region), session);
                if (!verdict.isAllowed()) {
                    return verdict;
                }
            }
            for (Region region : entered) {
                MoveVerdict verdict = FlagTriggers.decideCrossing(lookup, new MoveContext(player, from, to, cause, MoveContext.Transition.ENTER, region), session);
                if (!verdict.isAllowed()) {
                    return verdict;
                }
            }
        }
        if (postCrossings(player, from, to, cause, exited, entered)) {
            WorldLocPos lastValid = session.lastValid();
            return MoveVerdict.revert(lastValid != null ? lastValid : from);   //a listener that cancels does not pick a strategy
        }
        session.commit(next, to, lookup.getSnapshotVersion(to.getWorldName()));
        sessionFlags.apply(player, session, SessionFlags.Trigger.BOUNDARY);
        return MoveVerdict.allowed();
    }

    /** The player appeared at {@code at}: they are inside wherever that is, unasked. Thread: the player's world. */
    public void onJoin(FPlayer player, WorldLocPos at) {
        warnIfOffThread("onJoin", at.getWorldName());
        lookup.forgetSession(player.getUniqueId());
        RegionPlayerSession session = lookup.getSession(player);
        session.setOnlinePlayer(player);
        List<Region> next = lookup.at(at);
        postCrossings(player, null, at, MoveCause.JOIN, Collections.emptyList(), next);
        session.commit(next, at, lookup.getSnapshotVersion(at.getWorldName()));
        sessionFlags.apply(player, session, SessionFlags.Trigger.BOUNDARY);
    }

    /** The player left: they leave every region they were in and the session goes. A second call does nothing. */
    public void onQuit(FPlayer player) {
        onQuit(player.getUniqueId());
    }

    void onQuit(UUID uuid) {
        RegionPlayerSession session = lookup.getSessionIfPresent(uuid);
        if (session == null) {
            return;
        }
        lookup.forgetSession(uuid);
        FlagTriggers.forget(uuid);
        FPlayer player = session.getOnlinePlayer();
        if (player != null) {
            postCrossings(player, session.getPosition(), null, MoveCause.QUIT, session.getCurrent(), Collections.emptyList());
            sessionFlags.leaveAll(player, session);
        }
    }

    /** A world's snapshot changed: the players standing still in it may be under other regions now. */
    private void onSnapshotPublished(String world) {
        CompletableFuture<Void> ran;
        try {
            ran = EverNifeCore.getPlatform().runOnWorldThread(world, () -> {
                for (RegionPlayerSession session : lookup.getSessions()) {
                    WorldLocPos at = session.getPosition();
                    if (at != null && at.getWorldName().equals(world)) {
                        reevaluate(session, at);
                    }
                }
            });
        } catch (RuntimeException refused) {
            ran = new CompletableFuture<>();
            ran.completeExceptionally(refused);
        }
        ran.whenComplete((ignored, error) -> {
            if (error == null) {
                unreachableWorlds.remove(world);
            } else if (unreachableWorlds.add(world)) {
                ECDebugModule.REGIONS.debug("Players in world '{}' were not re-checked after its regions changed: {}", world, error.toString());
            }
        });
    }

    private void reevaluate(RegionPlayerSession session, WorldLocPos at) {
        FPlayer player = session.getOnlinePlayer();
        if (player == null) {
            return;
        }
        List<Region> next = lookup.at(at);
        List<Region> exited = minus(session.getCurrent(), next);
        List<Region> entered = minus(next, session.getCurrent());
        for (Region region : exited) {
            bus.postIfListened(RegionLeaveEvent.class, () -> new RegionLeaveEvent(region, player, at, at, MoveCause.REGION_REMOVED));
        }
        for (Region region : entered) {
            bus.postIfListened(RegionEnterEvent.class, () -> new RegionEnterEvent(region, player, at, at, MoveCause.REGION_CREATED));
        }
        session.setCurrent(next, lookup.getSnapshotVersion(at.getWorldName()));
        sessionFlags.apply(player, session, SessionFlags.Trigger.BOUNDARY);
    }

    /** Leaves, then enters; true when a listener cancelled a cancellable crossing - the ones already posted stay posted. */
    private boolean postCrossings(FPlayer player, @Nullable WorldLocPos from, @Nullable WorldLocPos to, MoveCause cause,
                                  List<Region> exited, List<Region> entered) {
        for (Region region : exited) {
            RegionLeaveEvent leave = bus.postIfListened(RegionLeaveEvent.class, () -> new RegionLeaveEvent(region, player, from, to, cause));
            if (leave != null && leave.isCancelled()) {
                return true;
            }
        }
        for (Region region : entered) {
            RegionEnterEvent enter = bus.postIfListened(RegionEnterEvent.class, () -> new RegionEnterEvent(region, player, from, to, cause));
            if (enter != null && enter.isCancelled()) {
                return true;
            }
        }
        return false;
    }

    private void invalidateUnder(Region region) {
        lookup.invalidate(region);
    }

    private void warnIfOffThread(String site, String world) {
        boolean onWorldThread;
        try {
            onWorldThread = EverNifeCore.getPlatform().isWorldThread(world);
        } catch (RuntimeException unanswered) {
            onWorldThread = false;
        }
        if (!onWorldThread && warnedSites.add(site)) {
            ECDebugModule.REGIONS.debug("RegionMovementTracker.{} ran off the thread of world '{}' ({}); call it from the platform's event of"
                    + " that world.", site, world, Thread.currentThread().getName());
        }
    }

    boolean hasWarned(String site) {
        return warnedSites.contains(site);
    }

    /** The regions of {@code from} that {@code without} does not hold, by identity, in {@code from}'s order. */
    private static List<Region> minus(List<Region> from, List<Region> without) {
        List<Region> rest = null;
        for (Region region : from) {
            boolean kept = true;
            for (Region other : without) {
                if (other == region) {
                    kept = false;
                    break;
                }
            }
            if (kept) {
                if (rest == null) {
                    rest = new ArrayList<>(2);
                }
                rest.add(region);
            }
        }
        return rest == null ? Collections.emptyList() : rest;
    }
}
