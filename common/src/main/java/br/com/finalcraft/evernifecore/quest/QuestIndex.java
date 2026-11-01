package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.EventSourceUsage;
import br.com.finalcraft.evernifecore.action.UsageHold;
import br.com.finalcraft.evernifecore.action.event.ActionEvent;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventPriority;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.eventbus.ECSubscribeOptions;
import br.com.finalcraft.evernifecore.group.GroupId;
import br.com.finalcraft.evernifecore.group.GroupLookup;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.event.RegionCrossedEvent;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How an action reaches the active objectives of every engine without scanning them: per source, the objectives each
 * owner has active now. An action is offered only to the owners it counts for (the player who did it, the groups they
 * belong to, the server, the world and the regions it happened in) and, among theirs, only to the objectives of its
 * source. Groups and regions are looked up only while some objective of that source has such an owner.
 *
 * <p>A source is listened to only while an active objective needs it: the index subscribes to the source's action
 * event when the first such objective appears and unsubscribes when the last one goes - and that subscription is what
 * switches the platform's native listener on. A region objective ({@link RegionObjectiveType}) listens to the region
 * crossings instead, holding {@link EventSource#MOVE} needed meanwhile. One per process; reads never lock, writes copy.</p>
 */
public final class QuestIndex {

    private static final QuestIndex INSTANCE = new QuestIndex();

    private final Map<EventSource, Bucket> buckets = new ConcurrentHashMap<>();
    private final Map<EngineOwner, List<Entry>> published = new HashMap<>();

    private QuestIndex() {
    }

    public static QuestIndex get() {
        return INSTANCE;
    }

    /**
     * Replaces what {@code engine} has active for {@code owner} by {@code entries}, subscribing to a source that gained its
     * first objective and unsubscribing from one that lost its last. Any thread.
     */
    synchronized void publish(QuestEngine engine, ProgressOwner owner, List<Entry> entries) {
        EngineOwner key = new EngineOwner(engine, owner);
        List<Entry> previous = entries.isEmpty() ? published.remove(key) : published.put(key, entries);
        Map<EventSource, List<Entry>> changed = new LinkedHashMap<>();
        if (previous != null) {
            for (Entry entry : previous) {
                changed.computeIfAbsent(entry.source, source -> new ArrayList<>());
            }
        }
        for (Entry entry : entries) {
            changed.computeIfAbsent(entry.source, source -> new ArrayList<>()).add(entry);
        }
        for (Map.Entry<EventSource, List<Entry>> source : changed.entrySet()) {
            Bucket bucket = buckets.computeIfAbsent(source.getKey(), Bucket::new);
            bucket.replace(engine, owner, source.getValue());
            bucket.refreshSubscriptions();
            if (bucket.isEmpty()) {
                buckets.remove(source.getKey());
            }
        }
    }

    /** Drops everything {@code engine} published. */
    synchronized void forget(QuestEngine engine) {
        for (EngineOwner key : new ArrayList<>(published.keySet())) {
            if (key.engine == engine) {
                publish(engine, key.owner, Collections.<Entry>emptyList());
            }
        }
    }

    /** The sources listened to now; what {@code /ecore quest} shows. */
    public synchronized List<EventSource> getListenedSources() {
        List<EventSource> sources = new ArrayList<>();
        for (Bucket bucket : buckets.values()) {
            if (bucket.post != null || bucket.pre != null || bucket.crossing != null) {
                sources.add(bucket.source);
            }
        }
        return sources;
    }

    /** The active objectives {@code owner} has on {@code source}, in every engine; empty for none. */
    public List<Entry> getEntries(EventSource source, ProgressOwner owner) {
        Bucket bucket = buckets.get(source);
        if (bucket == null) {
            return Collections.emptyList();
        }
        List<Entry> found = new ArrayList<>();
        for (List<Entry> entries : bucket.byOwner.getOrDefault(owner, Collections.<EngineOwner, List<Entry>>emptyMap()).values()) {
            found.addAll(entries);
        }
        return found;
    }

    /**
     * The owners something {@code player} did at {@code at} counts for, among those {@code bucket} has objectives of:
     * the player, their groups, the server, the world and the regions there.
     */
    static List<ProgressOwner> ownersOf(Bucket bucket, @Nullable FPlayer player, @Nullable WorldLocPos at) {
        Set<String> kinds = bucket.kinds;
        List<ProgressOwner> owners = new ArrayList<>(4);
        if (player != null) {
            owners.add(ProgressOwner.player(player.getUniqueId()));
            if (kinds.contains(ProgressOwner.GROUP)) {
                for (GroupId group : GroupLookup.get().getGroupsOf(player.getUniqueId())) {
                    owners.add(ProgressOwner.group(group));
                }
            }
        }
        owners.add(ProgressOwner.global());
        if (at != null) {
            owners.add(ProgressOwner.world(at.getWorldName()));
            if (kinds.contains(ProgressOwner.REGION)) {
                for (Region region : RegionLookup.get().at(at)) {
                    owners.add(ProgressOwner.region(region.getId()));
                }
            }
        }
        return owners;
    }

    private void dispatchCrossing(Bucket bucket, RegionCrossedEvent event) {
        WorldLocPos at = event.getTo() != null ? event.getTo() : event.getFrom();
        for (ProgressOwner owner : ownersOf(bucket, event.getPlayer(), at)) {
            Map<EngineOwner, List<Entry>> ofOwner = bucket.byOwner.get(owner);
            if (ofOwner == null) {
                continue;
            }
            for (List<Entry> entries : ofOwner.values()) {
                for (Entry entry : entries) {
                    if (entry.crossing) {
                        entry.engine.onCrossing(entry, event);
                    }
                }
            }
        }
    }

    private void dispatch(Bucket bucket, ActionEvent<?> event, boolean pre) {
        for (ProgressOwner owner : ownersOf(bucket, event.getPlayer(), event.getContext().getPosition())) {
            Map<EngineOwner, List<Entry>> ofOwner = bucket.byOwner.get(owner);
            if (ofOwner == null) {
                continue;
            }
            for (List<Entry> entries : ofOwner.values()) {
                for (Entry entry : entries) {
                    if (entry.pre != pre || entry.crossing) {
                        continue;
                    }
                    boolean completed = entry.engine.onAction(entry, event);
                    if (completed && pre && event instanceof ECCancellable) {
                        ((ECCancellable) event).setCancelled(true);
                    }
                }
            }
        }
    }

    /** One active objective of one owner, on one source. */
    public static final class Entry {

        final QuestEngine engine;
        final ProgressOwner owner;
        final QuestId questId;
        final Objective objective;
        final ObjectiveFilter filter;
        final EventSource source;
        /** Counts on the source's {@code Pre} (the type cancels the native outcome). */
        final boolean pre;
        /** A state objective: the action only asks the engine to check the condition again. */
        final boolean state;
        /** A region objective: counted on the region crossings, not on the source's action event. */
        final boolean crossing;

        Entry(QuestEngine engine, ProgressOwner owner, QuestId questId, Objective objective, ObjectiveFilter filter, EventSource source,
              boolean pre, boolean state) {
            this.engine = engine;
            this.owner = owner;
            this.questId = questId;
            this.objective = objective;
            this.filter = filter;
            this.source = source;
            this.pre = pre;
            this.state = state;
            this.crossing = filter.getType() instanceof RegionObjectiveType;
        }

        public QuestEngine getEngine() {
            return engine;
        }

        public ProgressOwner getOwner() {
            return owner;
        }

        public QuestId getQuestId() {
            return questId;
        }

        public Objective getObjective() {
            return objective;
        }

        public EventSource getSource() {
            return source;
        }

        @Override
        public String toString() {
            return questId + "/" + objective.getId() + " on " + source + (pre ? " (pre)" : "") + (state ? " (state)" : "");
        }
    }

    private final class Bucket {

        final EventSource source;
        /** owner -> (engine, owner) -> entries; the inner lists are never changed in place. */
        final Map<ProgressOwner, Map<EngineOwner, List<Entry>>> byOwner = new ConcurrentHashMap<>();
        @Nullable ECEventSubscription<?> post;
        @Nullable ECEventSubscription<?> pre;
        @Nullable ECEventSubscription<?> crossing;
        @Nullable UsageHold movement;
        /** The owner kinds this source has objectives of; replaced whole, read lock-free by the dispatch. */
        volatile Set<String> kinds = Collections.emptySet();

        Bucket(EventSource source) {
            this.source = source;
        }

        void replace(QuestEngine engine, ProgressOwner owner, List<Entry> entries) {
            EngineOwner key = new EngineOwner(engine, owner);
            if (entries.isEmpty()) {
                Map<EngineOwner, List<Entry>> ofOwner = byOwner.get(owner);
                if (ofOwner != null) {
                    ofOwner.remove(key);
                    if (ofOwner.isEmpty()) {
                        byOwner.remove(owner);
                    }
                }
                return;
            }
            byOwner.computeIfAbsent(owner, o -> new ConcurrentHashMap<>()).put(key, Collections.unmodifiableList(new ArrayList<>(entries)));
        }

        /** Recounts {@link #kinds}; under the index's lock. */
        void refreshKinds() {
            Set<String> found = new HashSet<>();
            for (ProgressOwner owner : byOwner.keySet()) {
                found.add(owner.getKind());
            }
            kinds = Collections.unmodifiableSet(found);
        }

        boolean isEmpty() {
            return byOwner.isEmpty() && post == null && pre == null && crossing == null;
        }

        /** First objective in: subscribe. Last one out: unsubscribe. */
        void refreshSubscriptions() {
            refreshKinds();
            Class<? extends ActionEvent<?>> postType = null;
            Class<? extends ActionEvent<?>> preType = null;
            boolean crossings = false;
            for (Map<EngineOwner, List<Entry>> ofOwner : byOwner.values()) {
                for (List<Entry> entries : ofOwner.values()) {
                    for (Entry entry : entries) {
                        if (entry.crossing) {
                            crossings = true;
                        } else if (entry.pre) {
                            preType = preType != null ? preType : entry.filter.getType().getPreEvent(source);
                        } else {
                            postType = postType != null ? postType : entry.filter.getType().getPostEvent(source);
                        }
                    }
                }
            }
            if (postType != null && post == null) {
                post = subscribe(postType, ECSubscribeOptions.defaults().withPriority(ECEventPriority.LAST), false);
                ECDebugModule.QUESTS.debug("The quest index listens to {} now.", source);
            } else if (postType == null && post != null) {
                post.unsubscribe();
                post = null;
                ECDebugModule.QUESTS.debug("The quest index stopped listening to {}: no active objective needs it.", source);
            }
            if (preType != null && pre == null) {
                pre = subscribe(preType, ECSubscribeOptions.defaults().withPriority(ECEventPriority.LAST).withIgnoreCancelled(true), true);
            } else if (preType == null && pre != null) {
                pre.unsubscribe();
                pre = null;
            }
            if (crossings && crossing == null) {
                crossing = ECEventBus.global().subscribe(RegionCrossedEvent.class, ECSubscribeOptions.defaults().withPriority(ECEventPriority.LAST),
                        event -> dispatchCrossing(this, event));
                movement = EventSourceUsage.get().acquire(EventSource.MOVE);
                ECDebugModule.QUESTS.debug("The quest index follows region crossings now.");
            } else if (!crossings && crossing != null) {
                crossing.unsubscribe();
                crossing = null;
                if (movement != null) {
                    movement.release();
                    movement = null;
                }
                ECDebugModule.QUESTS.debug("The quest index stopped following region crossings: no active region objective.");
            }
        }

        private <E extends ActionEvent<?>> ECEventSubscription<E> subscribe(Class<E> type, ECSubscribeOptions options, boolean onPre) {
            return ECEventBus.global().subscribe(type, options, event -> dispatch(this, event, onPre));
        }
    }

    private static final class EngineOwner {

        final QuestEngine engine;
        final ProgressOwner owner;

        EngineOwner(QuestEngine engine, ProgressOwner owner) {
            this.engine = engine;
            this.owner = owner;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EngineOwner && ((EngineOwner) other).engine == engine && ((EngineOwner) other).owner.equals(owner);
        }

        @Override
        public int hashCode() {
            return Objects.hash(System.identityHashCode(engine), owner);
        }
    }
}
