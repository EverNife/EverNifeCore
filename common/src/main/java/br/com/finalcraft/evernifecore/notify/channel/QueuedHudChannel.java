package br.com.finalcraft.evernifecore.notify.channel;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.notify.IHudAccess;
import br.com.finalcraft.evernifecore.notify.Notification;
import br.com.finalcraft.evernifecore.notify.NotifyChannel;
import br.com.finalcraft.evernifecore.time.ECClock;
import br.com.finalcraft.evernifecore.time.ECScheduler;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A channel that shows one notification at a time per player out of a queue: the highest priority is on screen, a
 * notification with the id of a queued one replaces it, an expired one leaves. A tick every {@link #TICK_MILLIS} on
 * the {@link ECScheduler} shows the head again and stops once the queue is empty or the player left.
 *
 * <p>Each player's queue belongs to the {@link IHudAccess} installed when it was created, and every write - the one in
 * {@link #deliver}, every tick, the blank at the end - goes to that one. A queue of a platform that is no longer
 * installed is closed and replaced on the next delivery, so a new platform starts with an empty queue.</p>
 */
public abstract class QueuedHudChannel implements NotifyChannel {

    /** How often a queue shows its head again. */
    public static final long TICK_MILLIS = 500L;

    private final IHudAccess.Capability capability;
    private final Map<UUID, PlayerQueue> queues = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    protected QueuedHudChannel(IHudAccess.Capability capability) {
        this.capability = capability;
    }

    @Override
    public boolean isSupported() {
        return EverNifeCore.getPlatform().getHud().supports(capability);
    }

    /** How long {@code notification} stays in the queue; {@link Duration#ZERO} until cleared. */
    protected abstract Duration lifetime(Notification notification);

    /**
     * Puts the head on screen. Called on every tick and every queue change, under the queue's monitor.
     *
     * @param changed whether {@code head} is not the entry shown last
     */
    protected abstract void show(IHudAccess hud, FPlayer player, Entry head, boolean changed, Instant now);

    /** Takes this channel off the screen: the queue emptied. Under the queue's monitor. */
    protected abstract void blank(IHudAccess hud, FPlayer player);

    @Override
    public void deliver(FPlayer player, Notification notification) {
        IHudAccess hud = EverNifeCore.getPlatform().getHud();
        Instant now = ECClock.now();
        Duration lifetime = lifetime(notification);
        Entry entry = new Entry(notification, now, lifetime.isZero() ? null : now.plus(lifetime), sequence.incrementAndGet());
        UUID uuid = player.getUniqueId();
        while (true) {
            PlayerQueue queue = queues.computeIfAbsent(uuid, ignored -> new PlayerQueue(player, hud));
            if (queue.hud != hud) {
                //a queue of a platform that is gone: it writes nothing more, and this platform starts empty
                if (queues.remove(uuid, queue)) {
                    queue.close();
                }
                continue;
            }
            if (queue.add(entry)) {
                return;
            }
            queues.remove(uuid, queue); //it ended (emptied, player left) after the lookup; a fresh one takes the entry
        }
    }

    @Override
    public void clear(FPlayer player, String id) {
        PlayerQueue queue = queues.get(player.getUniqueId());
        if (queue != null) {
            queue.remove(id);
        }
    }

    @Override
    public void onQuit(UUID player) {
        PlayerQueue queue = queues.remove(player);
        if (queue != null) {
            queue.close();
        }
    }

    @Override
    public void clearAll() {
        for (UUID player : new ArrayList<>(queues.keySet())) {
            onQuit(player);
        }
    }

    /** What waits in {@code player}'s queue on the installed platform, head first; empty when nothing does. */
    public List<Notification> getQueued(FPlayer player) {
        PlayerQueue queue = queues.get(player.getUniqueId());
        List<Notification> queued = new ArrayList<>();
        if (queue == null || queue.hud != EverNifeCore.getPlatform().getHud()) {
            return queued;
        }
        synchronized (queue) {
            if (queue.closed) {
                return queued;
            }
            List<Entry> sorted = new ArrayList<>(queue.entries);
            sorted.sort(ORDER);
            for (Entry entry : sorted) {
                queued.add(entry.notification);
            }
        }
        return queued;
    }

    private static final Comparator<Entry> ORDER = Comparator.comparingInt((Entry entry) -> entry.notification.getPriority()).reversed()
            .thenComparing(Comparator.comparingLong((Entry entry) -> entry.sequence).reversed());

    /** One queued notification and when it entered and leaves the queue. */
    protected static final class Entry {

        public final Notification notification;
        public final Instant start;
        /** Null when it stays until cleared. */
        public final @Nullable Instant end;
        private final long sequence;

        private Entry(Notification notification, Instant start, @Nullable Instant end, long sequence) {
            this.notification = notification;
            this.start = start;
            this.end = end;
            this.sequence = sequence;
        }

        boolean isExpired(Instant now) {
            return end != null && !now.isBefore(end);
        }
    }

    /**
     * One player's queue. Its monitor guards the entries and the closed flag AND is held across every write to the
     * platform, so {@link #close()} returns only after a tick already running finished - nothing writes after it.
     */
    private final class PlayerQueue {

        private final FPlayer player;
        private final IHudAccess hud;
        private final PriorityQueue<Entry> entries = new PriorityQueue<>(3, ORDER);
        private @Nullable Entry shown;
        private boolean ticking;
        private boolean closed;

        private PlayerQueue(FPlayer player, IHudAccess hud) {
            this.player = player;
            this.hud = hud;
        }

        /** False when this queue already ended: the caller starts a new one. */
        synchronized boolean add(Entry entry) {
            if (closed) {
                return false;
            }
            entries.removeIf(queued -> queued.notification.getIdOrAnonymous().equals(entry.notification.getIdOrAnonymous()));
            entries.offer(entry);
            showHead();
            if (!ticking && !closed) {
                ticking = true;
                ECScheduler.scheduleAsync(this::tick, TICK_MILLIS);
            }
            return true;
        }

        synchronized void remove(String id) {
            if (!closed && entries.removeIf(queued -> queued.notification.getIdOrAnonymous().equals(id))) {
                showHead();
            }
        }

        private synchronized void tick() {
            if (closed) {
                return;
            }
            showHead();
            if (!closed) {
                ECScheduler.scheduleAsync(this::tick, TICK_MILLIS);
            }
        }

        private void showHead() {
            if (!player.isOnline()) {
                closed = true;
                return;
            }
            Instant now = ECClock.now();
            while (entries.peek() != null && entries.peek().isExpired(now)) {
                entries.poll();
            }
            Entry head = entries.peek();
            if (head == null) {
                closed = true;
                blank(hud, player);
                return;
            }
            show(hud, player, head, head != shown, now);
            shown = head;
        }

        synchronized void close() {
            closed = true;
        }
    }
}
