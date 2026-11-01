package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.action.ActionSupport;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.event.ActionEvent;
import br.com.finalcraft.evernifecore.action.provenance.ProvenanceHold;
import br.com.finalcraft.evernifecore.action.provenance.ProvenanceTracker;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerFullyLoggedInEvent;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.GroupId;
import br.com.finalcraft.evernifecore.group.GroupLookup;
import br.com.finalcraft.evernifecore.group.event.GroupDissolvedEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.Conditions;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.logic.GateResult;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.RepeatPolicy;
import br.com.finalcraft.evernifecore.logic.reward.RewardContext;
import br.com.finalcraft.evernifecore.logic.reward.RewardRule;
import br.com.finalcraft.evernifecore.logic.reward.RewardTrigger;
import br.com.finalcraft.evernifecore.logic.reward.Rewards;
import br.com.finalcraft.evernifecore.playerdata.PDSectionConfiguration;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.playerdata.storage.SectionLifecycle;
import br.com.finalcraft.evernifecore.quest.event.ObjectiveCompleteEvent;
import br.com.finalcraft.evernifecore.quest.event.ObjectiveProgressEvent;
import br.com.finalcraft.evernifecore.quest.event.ObjectiveProgressPreEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestAbandonEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestAbandonPreEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestCompleteEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestCompletePreEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestExpireEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestFailEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestOfferEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestOfferPreEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestResetEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestStartEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestStartPreEvent;
import br.com.finalcraft.evernifecore.quest.event.StageCompleteEvent;
import br.com.finalcraft.evernifecore.region.Persistence;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionId;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.event.RegionCrossedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionRemovedEvent;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.time.ECClock;
import br.com.finalcraft.evernifecore.time.ECScheduler;
import br.com.finalcraft.evernifecore.time.Window;
import br.com.finalcraft.everydatabase.query.ScanRow;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The quests of one plugin: their definitions, every owner's progress and the transitions between states. A player's
 * progress lives in the plugin's own {@link QuestSection}; the progress of any other owner in the plugin's
 * {@link ECStorage} ({@link QuestStorage}). Actions reach the active objectives through the process-wide
 * {@link QuestIndex}.
 *
 * <pre>{@code
 * public final class MyQuestSection extends QuestSection {}
 *
 * QuestEngine quests = QuestEngine.of(pluginData, storage, MyQuestSection.class);
 * quests.load().join();
 * quests.register(QuestDefinitions.parseYaml(pluginData, yaml));
 * quests.start(ProgressOwner.player(uuid), QuestId.of("myplugin", "mine-iron"), QuestParams.NONE);
 * }</pre>
 *
 * <p><b>Threads:</b> every method may be called from any thread. Each owner's state is changed under that owner's own
 * lock, so actions of two owners never wait for each other and two threads progressing the same owner lose nothing.
 * A player's progress counts only while the player's section is loaded - nothing loads it from storage during an
 * action. Storage I/O never runs inside an owner's lock: changes of non-player owners are written a moment later, in
 * the background. Events go out on the thread of whatever made the change; effects go through {@link Effects#apply}.</p>
 */
public final class QuestEngine {

    private static final long FLUSH_DELAY_MILLIS = 50;
    private static final long SWEEP_MILLIS = 60_000;
    private static final long CLOSE_TIMEOUT_SECONDS = 30;
    private static final int MAX_STAGE_HOPS = 64;

    private static final Map<ECPluginData, QuestEngine> ENGINES = new ConcurrentHashMap<>();
    private static final List<Consumer<QuestDefinition>> DEFINITION_CHECKS = new CopyOnWriteArrayList<>();

    private final ECPluginData owner;
    private final @Nullable ECStorage storage;
    private final @Nullable QuestStorage questStorage;
    private final @Nullable Class<? extends QuestSection> sectionClass;
    private volatile Map<QuestId, QuestDefinition> definitions = Collections.emptyMap();
    private final Map<ProgressOwner, Object> lanes = new ConcurrentHashMap<>();
    /** States of non-player owners, and of players in a transient engine. */
    private final Map<ProgressOwner, Map<String, QuestState>> memory = new ConcurrentHashMap<>();
    /** Owners with at least one active instance: what the sweep visits. */
    private final Set<ProgressOwner> activeOwners = ConcurrentHashMap.newKeySet();
    /** Pool draws of non-player owners, and of players in a transient engine. */
    private final Map<ProgressOwner, Map<String, PoolDraw>> poolMemory = new ConcurrentHashMap<>();
    private final Map<String, QuestPool> pools = new ConcurrentHashMap<>();
    private final Set<String> dirtyPools = ConcurrentHashMap.newKeySet();
    private final Set<String> dirty = ConcurrentHashMap.newKeySet();
    private final Set<String> removed = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean flushScheduled = new AtomicBoolean();
    /** The owner|quest gates this thread is checking, in order: a quest-status that comes back to one is a loop. */
    private final ThreadLocal<Set<String>> evaluating = ThreadLocal.withInitial(LinkedHashSet::new);
    private final AtomicBoolean gateLoopWarned = new AtomicBoolean();
    private final Object writeChain = new Object();
    private CompletableFuture<Void> lastWrite = CompletableFuture.completedFuture(null);
    private final List<ECEventSubscription<?>> subscriptions = new ArrayList<>();
    private volatile boolean playersListened;
    /** Open while a registered quest has an objective with an origin test: what keeps the owner's tracker recording. */
    private @Nullable ProvenanceHold provenanceHold;
    private volatile @Nullable ProvenanceTracker provenance;
    private volatile QuestPresenter presenter = new NotifierQuestPresenter();
    private volatile boolean loaded;
    private volatile boolean closed;

    private QuestEngine(ECPluginData owner, @Nullable ECStorage storage, @Nullable Class<? extends QuestSection> sectionClass) {
        this.owner = owner;
        this.storage = storage;
        this.questStorage = storage == null ? null : new QuestStorage(storage);
        this.sectionClass = sectionClass;
        this.loaded = storage == null;
    }

    // ------------------------------------------------------------------
    //  Opening and closing
    // ------------------------------------------------------------------

    /**
     * The engine of {@code owner} keeping player progress in {@code playerSection} - a class of the plugin's own, registered
     * here as its section {@value QuestSection#SECTION_ID} (loaded at login) - and every other owner's in {@code storage}.
     * Later calls with the same arguments return the same engine. Any thread.
     *
     * @throws IllegalStateException when {@code owner} already has an engine opened otherwise, or another plugin's engine
     *                               already keeps its players in {@code playerSection}
     */
    public static <S extends QuestSection> QuestEngine of(ECPluginData owner, ECStorage storage, Class<S> playerSection) {
        return of(owner, storage, playerSection, (engine, section) -> {
        });
    }

    /**
     * {@link #of(ECPluginData, ECStorage, Class)}, with {@code configure} shaping the section's registration - a legacy file
     * layout, a migration, a collection - before the one {@code registerPDSectionCfg} of it. It runs only when this call
     * registers the section; the lifecycle stays {@code ONLINE} whatever it sets, since the engine reads the section while the
     * player is online. Any thread.
     */
    public static <S extends QuestSection> QuestEngine of(ECPluginData owner, ECStorage storage, Class<S> playerSection,
                                                          BiConsumer<QuestEngine, PDSectionConfiguration.Builder<S>> configure) {
        Objects.requireNonNull(storage, "'storage' cannot be null - QuestEngine.of(owner) keeps everything in memory.");
        Objects.requireNonNull(playerSection, "'playerSection' cannot be null - QuestEngine.of(owner, storage) has no player quests.");
        Objects.requireNonNull(configure, "configure");
        synchronized (ENGINES) {
            for (QuestEngine other : ENGINES.values()) {
                if (other.owner != owner && other.sectionClass == playerSection) {
                    throw new IllegalStateException(playerSection.getName() + " already keeps the quests of " + nameOf(other.owner) + ": declare a"
                            + " QuestSection subclass of " + nameOf(owner) + "'s own (public final class MyQuestSection extends QuestSection {}).");
                }
            }
            QuestEngine engine = open(owner, storage, playerSection);
            if (engine.sectionClass == playerSection && !engine.playersListened) {
                PDSectionConfiguration.Builder<S> section = PDSectionConfiguration.builder(owner, playerSection, QuestSection.SECTION_ID);
                configure.accept(engine, section);
                PlayerController.registerPDSectionCfg(section.lifecycle(SectionLifecycle.ONLINE).build());
                engine.listenToPlayers();
                engine.playersListened = true;
            }
            return engine;
        }
    }

    /** The engine of {@code owner} for owners that are not players, in {@code storage}; a player quest is refused by its start. Any thread. */
    public static QuestEngine of(ECPluginData owner, ECStorage storage) {
        Objects.requireNonNull(storage, "'storage' cannot be null - QuestEngine.of(owner) keeps everything in memory.");
        synchronized (ENGINES) {
            return open(owner, storage, null);
        }
    }

    /** The engine of {@code owner} that keeps everything in memory, players included, gone at restart: arenas, minigames. Any thread. */
    public static QuestEngine of(ECPluginData owner) {
        synchronized (ENGINES) {
            return open(owner, null, null);
        }
    }

    private static QuestEngine open(ECPluginData owner, @Nullable ECStorage storage, @Nullable Class<? extends QuestSection> section) {
        Objects.requireNonNull(owner, "'owner' cannot be null when opening a QuestEngine!");
        QuestEngine existing = ENGINES.get(owner);
        if (existing != null) {
            if (existing.storage == storage && existing.sectionClass == section) {
                return existing;
            }
            throw new IllegalStateException(nameOf(owner) + " already has a QuestEngine " + existing.describeShape() + " - close it"
                    + " (QuestEngine.closeOwnedBy) before opening one " + new QuestEngine(owner, storage, section).describeShape() + ".");
        }
        for (QuestEngine other : ENGINES.values()) {
            if (storage != null && other.storage == storage) {
                throw new IllegalStateException("That ECStorage already keeps the quests of " + nameOf(other.owner) + ": every plugin keeps its"
                        + " quests in its own storage, so open a separate ECStorage for " + nameOf(owner) + ".");
            }
        }
        QuestEngine created = new QuestEngine(owner, storage, section);
        ENGINES.put(owner, created);
        created.listenToOwners();
        created.scheduleSweep();
        ECDebugModule.QUESTS.debug("{} opened its QuestEngine {}.", nameOf(owner), created.describeShape());
        return created;
    }

    private String describeShape() {
        if (storage == null) {
            return "in memory (transient)";
        }
        return sectionClass == null ? "on a storage, without player quests" : "on a storage with players in " + sectionClass.getSimpleName();
    }

    /** The open engine of {@code owner}; null when it has none. Any thread. */
    public static @Nullable QuestEngine ofIfPresent(ECPluginData owner) {
        return ENGINES.get(owner);
    }

    /** Every open engine, read-only. */
    public static Collection<QuestEngine> all() {
        return Collections.unmodifiableCollection(ENGINES.values());
    }

    /**
     * Internal: closes the engine of {@code owner}, writing its non-player states (up to 30 s). The core calls it as the
     * owner starts shutting down, while its storage is still open.
     */
    public static void closeOwnedBy(ECPluginData owner) {
        QuestEngine engine = ENGINES.get(owner);
        if (engine == null) {
            return;
        }
        engine.stop();
        try {
            engine.write().get(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException slow) {
            owner.getLog().warning("Quests of {} were not all written within {} s of its shutdown; what was not written is lost.",
                    nameOf(owner), CLOSE_TIMEOUT_SECONDS);
        } catch (ExecutionException failed) {
            owner.getLog().warning("Quests of {} could not all be written on shutdown: {}", nameOf(owner), failed.getCause().toString());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Internal: the safety net for an owner whose storage already closed - forgets the engine and writes nothing. */
    public static void discardOwnedBy(ECPluginData owner) {
        QuestEngine engine = ENGINES.get(owner);
        if (engine == null) {
            return;
        }
        engine.stop();
        if (!engine.dirty.isEmpty() || !engine.removed.isEmpty()) {
            ECDebugModule.QUESTS.debug("{} disabled before its quests were written: {} change(s) and {} deletion(s) are dropped.",
                    nameOf(owner), engine.dirty.size(), engine.removed.size());
        }
        engine.dirty.clear();
        engine.removed.clear();
        engine.dirtyPools.clear();
    }

    /** Internal: closes every open engine - the core's own shutdown. */
    public static void closeAll() {
        for (ECPluginData owner : new ArrayList<>(ENGINES.keySet())) {
            closeOwnedBy(owner);
        }
    }

    private void stop() {
        synchronized (ENGINES) {
            closed = true;
            ENGINES.remove(owner, this);
        }
        for (ECEventSubscription<?> subscription : subscriptions) {
            subscription.unsubscribe();
        }
        subscriptions.clear();
        QuestIndex.get().forget(this);
        releaseProvenance();
        activeOwners.clear();
        for (QuestDefinition definition : definitions.values()) {
            unregisterHooks(definition);
        }
    }

    public ECPluginData getOwner() {
        return owner;
    }

    public @Nullable ECStorage getStorage() {
        return storage;
    }

    /** The plugin's section class of player progress; null for an engine without player quests or a transient one. */
    public @Nullable Class<? extends QuestSection> getSectionClass() {
        return sectionClass;
    }

    public boolean isClosed() {
        return closed;
    }

    /** How this engine shows quest moments to players; a {@link NotifierQuestPresenter} unless a product set its own. */
    public QuestPresenter getPresenter() {
        return presenter;
    }

    public void setPresenter(QuestPresenter presenter) {
        this.presenter = Objects.requireNonNull(presenter, "presenter");
    }

    // ------------------------------------------------------------------
    //  Load and write
    // ------------------------------------------------------------------

    /** Reads every non-player state from the storage; idempotent, and already done for an engine without storage. Any thread. */
    public CompletableFuture<Void> load() {
        if (questStorage == null || loaded) {
            return CompletableFuture.completedFuture(null);
        }
        return questStorage.loadPools().thenCompose(poolRows -> {
            for (ScanRow<QuestStorage.PoolRecord> row : poolRows) {
                if (row.isFailed()) {
                    owner.getLog().warning("Pool draw '{}' of {} does not read ({}); it is skipped and drawn again.", row.key(), nameOf(owner),
                            String.valueOf(row.error()));
                    continue;
                }
                QuestStorage.PoolRecord record = row.value();
                ProgressOwner progressOwner = ProgressOwner.parse(record.owner);
                synchronized (lane(progressOwner)) {
                    poolMemory.computeIfAbsent(progressOwner, key -> new LinkedHashMap<>()).put(record.pool, record.draw);
                }
            }
            return questStorage.loadAll();
        }).thenAccept(rows -> {
            int read = 0;
            for (ScanRow<QuestStorage.Record> row : rows) {
                if (row.isFailed()) {
                    owner.getLog().warning("Quest state '{}' of {} does not read ({}); it is skipped and stays in storage untouched.", row.key(),
                            nameOf(owner), String.valueOf(row.error()));
                    continue;
                }
                QuestStorage.Record record = row.value();
                ProgressOwner progressOwner = ProgressOwner.parse(record.owner);
                synchronized (lane(progressOwner)) {
                    memory.computeIfAbsent(progressOwner, key -> new LinkedHashMap<>()).put(record.state.getQuestId(), record.state);
                }
                read++;
            }
            loaded = true;
            for (ProgressOwner progressOwner : memory.keySet()) {
                reindex(progressOwner);
            }
            ECDebugModule.QUESTS.debug("{} loaded {} quest state(s) of owners that are not players.", nameOf(owner), read);
        });
    }

    private void markDirty(ProgressOwner progressOwner, String questId) {
        if (isPlayerSectionOwner(progressOwner)) {
            QuestSection section = section(progressOwner);
            if (section != null) {
                section.markDirty();
            }
            return;
        }
        if (questStorage == null || closed || isEphemeral(progressOwner)) {
            return;
        }
        String key = QuestStorage.keyOf(progressOwner.toString(), questId);
        removed.remove(key);
        dirty.add(key);
        scheduleFlush();
    }

    private void markRemoved(ProgressOwner progressOwner, String questId) {
        if (isPlayerSectionOwner(progressOwner)) {
            QuestSection section = section(progressOwner);
            if (section != null) {
                section.markDirty();
            }
            return;
        }
        if (questStorage == null || closed) {
            return;
        }
        String key = QuestStorage.keyOf(progressOwner.toString(), questId);
        dirty.remove(key);
        removed.add(key);
        scheduleFlush();
    }

    private void scheduleFlush() {
        if (flushScheduled.compareAndSet(false, true)) {
            ECScheduler.scheduleAsync(this::write, FLUSH_DELAY_MILLIS);
        }
    }

    /** Writes every pending non-player change now; completes once the storage confirmed it. Any thread. */
    public CompletableFuture<Void> flush() {
        return write();
    }

    /** Runs after every write already started, so an older state never lands after a newer one. */
    private CompletableFuture<Void> write() {
        synchronized (writeChain) {
            lastWrite = lastWrite.handle((ignored, error) -> null).thenCompose(ignored -> writeNow());
            return lastWrite;
        }
    }

    private CompletableFuture<Void> writeNow() {
        flushScheduled.set(false);
        if (questStorage == null) {
            return CompletableFuture.completedFuture(null);
        }
        List<CompletableFuture<?>> pending = new ArrayList<>();
        for (String key : new ArrayList<>(removed)) {
            if (removed.remove(key)) {
                pending.add(questStorage.delete(key));
            }
        }
        for (String key : new ArrayList<>(dirty)) {
            if (!dirty.remove(key)) {
                continue;
            }
            int bar = key.indexOf('|');
            ProgressOwner progressOwner = ProgressOwner.parse(key.substring(0, bar));
            String questId = key.substring(bar + 1);
            QuestState snapshot;
            synchronized (lane(progressOwner)) {
                Map<String, QuestState> states = memory.get(progressOwner);
                QuestState state = states == null ? null : states.get(questId);
                snapshot = state == null ? null : state.copy();
            }
            if (snapshot != null) {
                pending.add(questStorage.save(new QuestStorage.Record(progressOwner.toString(), snapshot)).whenComplete((ignored, error) -> {
                    if (error != null) {
                        dirty.add(key);
                        owner.getLog().warning("Quest state {} of {} could not be written ({}); the next write tries again.", key, nameOf(owner),
                                error.toString());
                    }
                }));
            }
        }
        for (String key : new ArrayList<>(dirtyPools)) {
            if (!dirtyPools.remove(key)) {
                continue;
            }
            int bar = key.indexOf('|');
            ProgressOwner progressOwner = ProgressOwner.parse(key.substring(0, bar));
            String pool = key.substring(bar + 1);
            PoolDraw snapshot;
            synchronized (lane(progressOwner)) {
                Map<String, PoolDraw> draws = poolMemory.get(progressOwner);
                PoolDraw draw = draws == null ? null : draws.get(pool);
                snapshot = draw == null ? null : draw.copy();
            }
            if (snapshot != null) {
                pending.add(questStorage.savePool(new QuestStorage.PoolRecord(progressOwner.toString(), pool, snapshot)).whenComplete((ignored, error) -> {
                    if (error != null) {
                        dirtyPools.add(key);
                        owner.getLog().warning("Pool draw {} of {} could not be written ({}); the next write tries again.", key, nameOf(owner),
                                error.toString());
                    }
                }));
            }
        }
        return CompletableFuture.allOf(pending.toArray(new CompletableFuture[0]));
    }

    // ------------------------------------------------------------------
    //  Pools
    // ------------------------------------------------------------------

    /**
     * Opens (or replaces) the pool {@code name}, set up by {@code setup}; its draws live where the owners' quests live.
     * Any thread.
     *
     * @throws IllegalArgumentException when the pool draws from nothing
     */
    public QuestPool pool(String name, Consumer<QuestPool.Builder> setup) {
        QuestPool.Builder builder = new QuestPool.Builder();
        setup.accept(builder);
        QuestPool pool = QuestPool.build(this, name, builder);
        pools.put(name, pool);
        return pool;
    }

    public @Nullable QuestPool getPool(String name) {
        return pools.get(name);
    }

    public Collection<QuestPool> getPools() {
        return Collections.unmodifiableCollection(pools.values());
    }

    /** The owner's live draws by pool; null for a player whose quests are not loaded. Under the owner's lock. */
    @Nullable Map<String, PoolDraw> poolDraws(ProgressOwner progressOwner) {
        if (isPlayerSectionOwner(progressOwner)) {
            QuestSection section = section(progressOwner);
            return section == null ? null : section.getPools();
        }
        return poolMemory.computeIfAbsent(progressOwner, key -> new LinkedHashMap<>());
    }

    void markPoolDirty(ProgressOwner progressOwner, String pool) {
        if (isPlayerSectionOwner(progressOwner)) {
            QuestSection section = section(progressOwner);
            if (section != null) {
                section.markDirty();
            }
            return;
        }
        if (questStorage == null || closed || isEphemeral(progressOwner)) {
            return;
        }
        dirtyPools.add(QuestStorage.keyOf(progressOwner.toString(), pool));
        scheduleFlush();
    }

    /** Whether the owner has {@code id} active, or finished it since {@code windowStart} (ever, for no window): a reroll keeps it. */
    boolean isTakenInWindow(ProgressOwner progressOwner, QuestId id, @Nullable Instant windowStart) {
        Map<String, QuestState> states = states(progressOwner);
        QuestState state = states == null ? null : states.get(id.toString());
        if (state == null) {
            return false;
        }
        if (state.getStatus() == QuestStatus.ACTIVE) {
            return true;
        }
        return state.getLastCompletedAt() > 0 && (windowStart == null || state.getLastCompletedAt() >= windowStart.toEpochMilli());
    }

    // ------------------------------------------------------------------
    //  Definitions
    // ------------------------------------------------------------------

    /**
     * Adds {@code definition}, or replaces the one of the same id; a higher version applies the definition's
     * {@link DefinitionPolicy} to the open instances. The map is swapped by reference, so a reader never sees it half
     * filled. Any thread.
     *
     * @throws IllegalArgumentException when the quest belongs to another plugin, its {@code requires} close a cycle, or an
     *                                  objective needs an action source this platform never raises
     */
    public void register(QuestDefinition definition) {
        registerAll(Collections.singletonList(definition));
    }

    /** {@link #register} of several at once: one swap, one reindex. */
    public void registerAll(Collection<QuestDefinition> added) {
        Map<QuestId, QuestDefinition> next = new LinkedHashMap<>(definitions);
        for (QuestDefinition definition : added) {
            if (!definition.getId().getOwner().equals(nameOf(owner).toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Quest '" + definition.getId() + "' belongs to the plugin '" + definition.getId().getOwner()
                        + "', not to " + nameOf(owner) + ": register it in that plugin's engine, or name it " + nameOf(owner) + ":"
                        + definition.getId().getName() + ".");
            }
            validate(definition);
            next.put(definition.getId(), definition);
        }
        requireNoCycle(next);
        for (QuestDefinition definition : added) {
            if (hasProvenanceTest(definition)) {
                requireProvenance(definition);
            }
        }
        Map<QuestId, QuestDefinition> previous = definitions;
        definitions = Collections.unmodifiableMap(next);
        holdProvenance();
        for (QuestDefinition definition : added) {
            QuestDefinition before = previous.get(definition.getId());
            if (before != null) {
                unregisterHooks(before);
            }
            registerHooks(definition);
            if (before != null && definition.getVersion() > before.getVersion()) {
                applyPolicy(definition);
            }
        }
        reindexAll();
        ECDebugModule.QUESTS.debug("{} registered {} quest(s); {} in all.", nameOf(owner), added.size(), next.size());
    }

    /**
     * Adds a check every engine runs on each definition it registers, before it takes any; a check refuses by throwing an
     * {@link IllegalArgumentException} that teaches. A platform uses it for what only it can tell (what its native
     * quest system expresses). Closing the handle removes the check. Any thread.
     */
    public static AutoCloseable addDefinitionCheck(Consumer<QuestDefinition> check) {
        DEFINITION_CHECKS.add(check);
        return () -> DEFINITION_CHECKS.remove(check);
    }

    private void validate(QuestDefinition definition) {
        for (Consumer<QuestDefinition> check : DEFINITION_CHECKS) {
            check.accept(definition);
        }
        Map<String, String> firstValues = new LinkedHashMap<>();
        for (Map.Entry<String, QuestDefinition.Param> param : definition.getParams().entrySet()) {
            firstValues.put(param.getKey(), param.getValue().getValues().get(0));
        }
        QuestParams first = QuestParams.of(firstValues);
        for (Objective objective : definition.getObjectives()) {
            if (objective.isSkippedHere()) {
                continue;
            }
            ObjectiveFilter filter = objective.getFilter(first);
            if (filter == null || filter.getType().getNature() != ObjectiveNature.EVENT || filter.getType() instanceof RegionObjectiveType) {
                continue; //a region objective is counted on the region system's crossings, served everywhere
            }
            String what = "The objective '" + objective.getId() + "' of quest '" + definition.getId() + "'";
            for (EventSource source : filter.getType().getSources()) {
                ActionSupport.requireServed(source, false, what);
                if (filter.getType().cancelsNative() && filter.getType().getPreEvent(source) == null) {
                    ActionSupport.requireServed(source, true, what); //names the missing pre-outcome event
                }
            }
        }
    }

    private static boolean hasProvenanceTest(QuestDefinition definition) {
        for (Objective objective : definition.getObjectives()) {
            if (objective.getProvenance() != null) {
                return true;
            }
        }
        return false;
    }

    /** The tracker the origin tests of {@code definition} read: the owner's on this engine's storage, or the shared one. */
    private void requireProvenance(QuestDefinition definition) {
        if (provenance != null) {
            return;
        }
        ProvenanceTracker tracker = storage != null ? ProvenanceTracker.open(owner, storage) : ProvenanceTracker.ofIfPresent(owner);
        if (tracker == null) {
            throw new IllegalArgumentException("Quest '" + definition.getId() + "' has an objective with a provenance test, and this engine keeps"
                    + " everything in memory, so it has no storage to record origins in: open the engine on a storage"
                    + " (QuestEngine.of(owner, storage, ...)), open the owner's tracker first (ProvenanceTracker.open(owner, storage)),"
                    + " or turn the shared tracker on (Provenance.shared in the core's settings).");
        }
        provenance = tracker;
    }

    /** Records origins while a registered quest tests them, and stops when none does. */
    private synchronized void holdProvenance() {
        boolean needed = false;
        for (QuestDefinition definition : definitions.values()) {
            needed |= hasProvenanceTest(definition);
        }
        ProvenanceTracker tracker = provenance;
        if (needed && provenanceHold == null && tracker != null) {
            provenanceHold = tracker.record();
        } else if (!needed && provenanceHold != null) {
            provenanceHold.close();
            provenanceHold = null;
        }
    }

    private synchronized void releaseProvenance() {
        if (provenanceHold != null) {
            provenanceHold.close();
            provenanceHold = null;
        }
    }

    private static void requireNoCycle(Map<QuestId, QuestDefinition> all) {
        Set<QuestId> done = new HashSet<>();
        for (QuestId start : all.keySet()) {
            List<QuestId> path = new ArrayList<>();
            visit(all, start, path, done);
        }
    }

    private static void visit(Map<QuestId, QuestDefinition> all, QuestId at, List<QuestId> path, Set<QuestId> done) {
        int seen = path.indexOf(at);
        if (seen >= 0) {
            StringBuilder chain = new StringBuilder();
            for (QuestId id : path.subList(seen, path.size())) {
                chain.append(id).append(" -> ");
            }
            chain.append(at);
            throw new IllegalArgumentException("Quests " + chain + " require each other in a cycle (requires:). A quest can only require"
                    + " quests that do not, directly or through others, require it - drop one edge, or chain them with the start-quest"
                    + " effect instead.");
        }
        if (!done.add(at) && path.isEmpty()) {
            return;
        }
        QuestDefinition definition = all.get(at);
        if (definition == null) {
            return;
        }
        path.add(at);
        for (QuestId required : definition.getRequires()) {
            visit(all, required, path, done);
        }
        path.remove(path.size() - 1);
    }

    /** Removes the definition; every owner's state of it stays as it is (an orphan) until a {@link #reset}. Any thread. */
    public void unregister(QuestId id) {
        Map<QuestId, QuestDefinition> next = new LinkedHashMap<>(definitions);
        QuestDefinition gone = next.remove(id);
        if (gone != null) {
            definitions = Collections.unmodifiableMap(next);
            holdProvenance();
            unregisterHooks(gone);
            reindexAll();
        }
    }

    /** What the region objectives of {@code definition} need while it is registered (a target region). */
    private void registerHooks(QuestDefinition definition) {
        forEachRegionObjective(definition, (type, objective, filter) -> type.onRegister(this, definition.getId(), objective, filter));
    }

    private void unregisterHooks(QuestDefinition definition) {
        forEachRegionObjective(definition, (type, objective, filter) -> type.onUnregister(this, definition.getId(), objective, filter));
    }

    private interface RegionHook {
        void run(RegionObjectiveType<Object> type, Objective objective, Object filter);
    }

    @SuppressWarnings("unchecked")
    private void forEachRegionObjective(QuestDefinition definition, RegionHook hook) {
        for (Objective objective : definition.getObjectives()) {
            ObjectiveFilter filter = objective.getFilter(QuestParams.NONE);
            if (filter == null || !(filter.getType() instanceof RegionObjectiveType)) {
                continue;
            }
            try {
                hook.run((RegionObjectiveType<Object>) filter.getType(), objective, filter.getFilter());
            } catch (RuntimeException failure) {
                owner.getLog().warning("Objective '{}' of quest {} could not set up or tear down its region: {}", objective.getId(),
                        definition.getId(), failure.toString());
            }
        }
    }

    public @Nullable QuestDefinition get(QuestId id) {
        return definitions.get(id);
    }

    public Collection<QuestDefinition> getAll() {
        return definitions.values();
    }

    private void applyPolicy(QuestDefinition definition) {
        String key = definition.getId().toString();
        for (ProgressOwner progressOwner : knownOwners()) {
            synchronized (lane(progressOwner)) {
                Map<String, QuestState> states = states(progressOwner);
                QuestState state = states == null ? null : states.get(key);
                if (state == null || state.getDefinitionVersion() >= definition.getVersion()) {
                    continue;
                }
                state.setDefinitionVersion(definition.getVersion());
                if (state.getStatus().isOpen() || state.getStatus() == QuestStatus.ACTIVE) {
                    if (definition.getPolicy() == DefinitionPolicy.RESET) {
                        state.setStatus(QuestStatus.AVAILABLE);
                        state.setStageId(null);
                        state.getObjectives().clear();
                    } else {
                        Set<String> ids = new HashSet<>();
                        for (Objective objective : definition.getObjectives()) {
                            ids.add(objective.getId());
                        }
                        state.getObjectives().keySet().retainAll(ids);
                        if (state.getStageId() != null && definition.getStage(state.getStageId()) == null) {
                            owner.getLog().warning("Quest {} of {}: stage '{}' is gone in version {}; the instance restarts at stage '{}'.",
                                    key, progressOwner, state.getStageId(), definition.getVersion(), definition.getStages().get(0).getId());
                            state.setStageId(definition.getStages().get(0).getId());
                            state.setStageStartedAt(ECClock.now().toEpochMilli());
                        }
                    }
                }
                markDirty(progressOwner, key);
                if (state.getStatus() == QuestStatus.ACTIVE) {
                    evaluateStage(progressOwner, definition, state, null, 0);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  Transitions
    // ------------------------------------------------------------------

    /** AVAILABLE (or LOCKED) -> OFFERED: a dialogue, a NPC or a command offers the quest. Any thread. */
    public CompletableFuture<StartResult> offer(ProgressOwner progressOwner, QuestId id) {
        QuestDefinition definition = definitions.get(id);
        if (definition == null) {
            return done(StartResult.refused(StartResult.Reason.UNKNOWN_QUEST, unknown(id)));
        }
        synchronized (lane(progressOwner)) {
            Map<String, QuestState> states = states(progressOwner);
            if (states == null) {
                return done(StartResult.refused(StartResult.Reason.NOT_LOADED, notLoaded(progressOwner, id)));
            }
            QuestState state = states.get(id.toString());
            QuestStatus status = effectiveStatus(state);
            if (status != null && status != QuestStatus.AVAILABLE) {
                return done(StartResult.refused(StartResult.Reason.STATUS, "Quest '" + id + "' is " + status + " for " + progressOwner + ": only an available quest is offered."));
            }
            QuestState offered = state != null ? state : new QuestState(id);
            Caller caller = caller(definition, null, null);
            if (cancelled(new QuestOfferPreEvent(this, id, progressOwner, caller, offered.copy()))) {
                return done(StartResult.refused(StartResult.Reason.CANCELLED, "A listener cancelled the offer of quest '" + id + "'."));
            }
            offered.setStatus(QuestStatus.OFFERED);
            offered.setDefinitionVersion(definition.getVersion());
            states.put(id.toString(), offered);
            markDirty(progressOwner, id.toString());
            post(QuestOfferEvent.class,
                    () -> new QuestOfferEvent(this, id, progressOwner, caller, offered.copy()));
            return done(StartResult.done(StartResult.Outcome.OFFERED, offered.copy()));
        }
    }

    /**
     * Starts the quest for {@code requested}: checks its window, {@code requires} and the gate (every failure listed), takes
     * the gate's costs, freezes the params ({@code params} wins over the definition's pick) and enters the first stage. A
     * group quest asked for a player starts for that player's group of the quest's {@code provider} kind. Any thread.
     */
    public CompletableFuture<StartResult> start(ProgressOwner requested, QuestId id, QuestParams params) {
        QuestDefinition definition = definitions.get(id);
        if (definition == null) {
            return done(StartResult.refused(StartResult.Reason.UNKNOWN_QUEST, unknown(id)));
        }
        ProgressOwner resolved = requested;
        if (definition.getOwnerKind().equals(ProgressOwner.GROUP) && requested.isKind(ProgressOwner.PLAYER)) {
            GroupId group = groupOf(UUID.fromString(requested.getId()), definition.getOwnerProvider());
            if (group == null) {
                return done(StartResult.noGroup(definition, requested));
            }
            resolved = ProgressOwner.group(group);
        }
        final ProgressOwner progressOwner = resolved;
        Instant at = ECClock.now();
        if (definition.getWindowStart() != null && at.isBefore(definition.getWindowStart())) {
            return done(StartResult.refused(StartResult.Reason.WINDOW, "Quest '" + id + "' opens at " + definition.getWindowStart() + "; it cannot start before that."));
        }
        if (definition.getWindowEnd() != null && !at.isBefore(definition.getWindowEnd())) {
            return done(StartResult.refused(StartResult.Reason.WINDOW, "Quest '" + id + "' closed at " + definition.getWindowEnd() + "; it cannot start any more."));
        }
        if (!definition.getOwnerKind().equals(progressOwner.getKind())) {
            return done(StartResult.refused(StartResult.Reason.OWNER_KIND, "Quest '" + id + "' is owned by a " + definition.getOwnerKind() + ", not by a " + progressOwner.getKind()
                    + " - start it for a " + definition.getOwnerKind() + " owner."));
        }
        if (progressOwner.isKind(ProgressOwner.PLAYER) && sectionClass == null && storage != null) {
            return done(StartResult.refused(StartResult.Reason.NOT_LOADED, "The QuestEngine of " + nameOf(owner) + " keeps no player quests: open it with QuestEngine.of(plugin,"
                    + " storage, MyQuestSection.class) to start '" + id + "' for a player."));
        }
        synchronized (lane(progressOwner)) {
            Map<String, QuestState> states = states(progressOwner);
            if (states == null) {
                return done(StartResult.refused(StartResult.Reason.NOT_LOADED, notLoaded(progressOwner, id)));
            }
            QuestState previous = states.get(id.toString());
            QuestStatus status = effectiveStatus(previous);
            if (status == QuestStatus.ACTIVE || status == QuestStatus.COMPLETED || status == QuestStatus.COOLDOWN) {
                return done(StartResult.refused(StartResult.Reason.STATUS, "Quest '" + id + "' is " + status + " for " + progressOwner + ": it cannot start again now."));
            }
            List<QuestId> missing = missingRequires(progressOwner, definition);
            if (!missing.isEmpty()) {
                return done(StartResult.refused(StartResult.Reason.REQUIRES, "Quest '" + id + "' requires " + missing + " completed first."));
            }
            //a group or global quest is checked and paid by the member who asked for it
            ExecutionContext ctx = context(progressOwner, definition, previous, playerOf(requested), null, null, null);
            GateResult checked = definition.getGate().check(ctx);
            if (!checked.isPassed()) {
                return done(StartResult.denied(checked));
            }
            QuestState state = new QuestState(id);
            if (previous != null) {
                state.setTimes(previous.getTimes());
                state.setLastCompletedAt(previous.getLastCompletedAt());
            }
            state.setParams(resolveParams(definition, params).asMap());
            Caller caller = caller(definition, null, null);
            if (cancelled(new QuestStartPreEvent(this, id, progressOwner, caller, state.copy()))) {
                return done(StartResult.refused(StartResult.Reason.CANCELLED, "A listener cancelled the start of quest '" + id + "'."));
            }
            GateResult paid = definition.getGate().pay(ctx);
            if (!paid.isPassed()) {
                return done(StartResult.denied(paid));
            }
            long now = ECClock.now().toEpochMilli();
            state.setStatus(QuestStatus.ACTIVE);
            state.setDefinitionVersion(definition.getVersion());
            state.setStartedAt(now);
            state.setStageId(definition.getStages().get(0).getId());
            state.setStageStartedAt(now);
            states.put(id.toString(), state);
            markDirty(progressOwner, id.toString());
            ECDebugModule.QUESTS.debug("{} started {} for {}.", nameOf(owner), id, progressOwner);
            post(QuestStartEvent.class,
                    () -> new QuestStartEvent(this, id, progressOwner, caller, state.copy()));
            apply(definition.getOnStart(), context(progressOwner, definition, state, playerOf(progressOwner), null, null, null));
            evaluateStage(progressOwner, definition, state, null, 0);
            reindex(progressOwner);
            return done(StartResult.done(StartResult.Outcome.STARTED, state.copy()));
        }
    }

    /** ACTIVE -> ABANDONED, only for a quest the definition lets be abandoned. Any thread. */
    public CompletableFuture<Void> abandon(ProgressOwner progressOwner, QuestId id) {
        QuestDefinition definition = definitions.get(id);
        if (definition == null) {
            return failed(unknown(id));
        }
        if (!definition.isAbandonable()) {
            return failed("Quest '" + id + "' cannot be abandoned: its definition says abandonable: false.");
        }
        synchronized (lane(progressOwner)) {
            QuestState state = activeState(progressOwner, id);
            if (state == null) {
                return failed("Quest '" + id + "' is not active for " + progressOwner + ".");
            }
            Caller caller = caller(definition, null, null);
            if (cancelled(new QuestAbandonPreEvent(this, id, progressOwner, caller, state.copy()))) {
                return failed("A listener cancelled abandoning quest '" + id + "'.");
            }
            state.setStatus(QuestStatus.ABANDONED);
            state.setStageId(null);
            markDirty(progressOwner, id.toString());
            post(QuestAbandonEvent.class,
                    () -> new QuestAbandonEvent(this, id, progressOwner, caller, state.copy()));
            present(progressOwner, (viewer, shown) -> shown.onQuestAbandon(viewer, this, definition, state.copy()));
            apply(definition.getOnAbandon(), context(progressOwner, definition, state, playerOf(progressOwner), null, null, null));
            reindex(progressOwner);
            return CompletableFuture.completedFuture(null);
        }
    }

    /** Admin: forces an active quest to complete, applying what completion applies. Any thread. */
    public CompletableFuture<Void> complete(ProgressOwner progressOwner, QuestId id) {
        QuestDefinition definition = definitions.get(id);
        if (definition == null) {
            return failed(unknown(id));
        }
        synchronized (lane(progressOwner)) {
            QuestState state = activeState(progressOwner, id);
            if (state == null) {
                return failed("Quest '" + id + "' is not active for " + progressOwner + ".");
            }
            completeQuest(progressOwner, definition, state);
            reindex(progressOwner);
            return CompletableFuture.completedFuture(null);
        }
    }

    /** ACTIVE -> FAILED for {@code cause}. Any thread. */
    public CompletableFuture<Void> fail(ProgressOwner progressOwner, QuestId id, FailCause cause) {
        QuestDefinition definition = definitions.get(id);
        if (definition == null) {
            return failed(unknown(id));
        }
        synchronized (lane(progressOwner)) {
            QuestState state = activeState(progressOwner, id);
            if (state == null) {
                return failed("Quest '" + id + "' is not active for " + progressOwner + ".");
            }
            failQuest(progressOwner, definition, state, cause);
            reindex(progressOwner);
            return CompletableFuture.completedFuture(null);
        }
    }

    /** Drops the owner's state of the quest, whatever it is - an orphan's too. Any thread. */
    public CompletableFuture<Void> reset(ProgressOwner progressOwner, QuestId id) {
        synchronized (lane(progressOwner)) {
            Map<String, QuestState> states = states(progressOwner);
            if (states == null) {
                return failed(notLoaded(progressOwner, id));
            }
            QuestState removedState = states.remove(id.toString());
            if (removedState == null) {
                return CompletableFuture.completedFuture(null);
            }
            markRemoved(progressOwner, id.toString());
            QuestDefinition definition = definitions.get(id);
            Caller caller = definition == null ? Caller.of(owner, id.getName()) : caller(definition, null, null);
            post(QuestResetEvent.class,
                    () -> new QuestResetEvent(this, id, progressOwner, caller, removedState));
            reindex(progressOwner);
            return CompletableFuture.completedFuture(null);
        }
    }

    /** Progress through the API (a product's own trigger): adds {@code delta} to the objective, if its stage is the current one. Any thread. */
    public CompletableFuture<Void> progress(ProgressOwner progressOwner, QuestId id, String objectiveId, long delta) {
        QuestDefinition definition = definitions.get(id);
        if (definition == null) {
            return failed(unknown(id));
        }
        synchronized (lane(progressOwner)) {
            QuestState state = activeState(progressOwner, id);
            if (state == null) {
                return failed("Quest '" + id + "' is not active for " + progressOwner + ".");
            }
            Stage stage = definition.getStage(state.getStageId());
            Objective objective = stage == null ? null : stage.getObjective(objectiveId);
            if (objective == null) {
                return failed("Quest '" + id + "' has no objective '" + objectiveId + "' in its current stage '" + state.getStageId() + "'.");
            }
            advance(progressOwner, definition, state, objective, delta, null,
                    context(progressOwner, definition, state, playerOf(progressOwner), null, stage, objective));
            reindex(progressOwner);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Who holds {@code player}'s instance of quest {@code id}: the player, or their group of the quest's provider kind for a
     * group quest; null when it is a group quest and the player is in no such group, or a quest of another owner kind.
     */
    public @Nullable ProgressOwner getOwnerFor(UUID player, QuestId id) {
        QuestDefinition definition = definitions.get(id);
        if (definition == null || definition.getOwnerKind().equals(ProgressOwner.PLAYER)) {
            return ProgressOwner.player(player);
        }
        if (definition.getOwnerKind().equals(ProgressOwner.GROUP)) {
            GroupId group = groupOf(player, definition.getOwnerProvider());
            return group == null ? null : ProgressOwner.group(group);
        }
        return null;
    }

    /**
     * What the gate of quest {@code id} answers for {@code player} now, without paying it - what an offer shows before the
     * player accepts. Null for an unknown quest, or a group quest of a player in no such group.
     */
    public @Nullable GateResult checkStart(FPlayer player, QuestId id) {
        QuestDefinition definition = definitions.get(id);
        ProgressOwner holder = definition == null ? null : getOwnerFor(player.getUniqueId(), id);
        if (holder == null) {
            return null;
        }
        QuestState state = getState(holder, id).orElse(null);
        return definition.getGate().check(context(holder, definition, state, player, null, null, null));
    }

    /** The quest {@code player} tracks (null for none). Any thread. */
    public CompletableFuture<Void> track(FPlayer player, @Nullable QuestId id) {
        ProgressOwner progressOwner = ProgressOwner.player(player.getUniqueId());
        synchronized (lane(progressOwner)) {
            QuestSection section = section(progressOwner);
            if (section == null) {
                return failed(notLoaded(progressOwner, id));
            }
            String before = section.getTracked();
            section.setTracked(id == null ? null : id.toString());
            section.markDirty();
            QuestPresenter shown = presenter;
            try {
                if (before != null && !before.equals(section.getTracked())) {
                    shown.onUntrack(player, this, QuestId.parse(before, nameOf(owner)));
                }
                QuestDefinition definition = id == null ? null : definitions.get(id);
                if (definition != null) {
                    Map<String, QuestState> states = states(ProgressOwner.player(player.getUniqueId()));
                    ProgressOwner holder = getOwnerFor(player.getUniqueId(), id);
                    Map<String, QuestState> held = holder == null ? states : states(holder);
                    QuestState state = held == null ? null : held.get(id.toString());
                    shown.onTrack(player, this, definition, state == null ? new QuestState(id) : state.copy());
                }
            } catch (RuntimeException failed) {
                owner.getLog().warning("The quest presenter of {} failed on a track change: {}", nameOf(owner), failed.toString());
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    /** The quest {@code player} tracks; null for none, or when their section is not loaded. */
    public @Nullable QuestId getTracked(FPlayer player) {
        return getTracked(player.getUniqueId());
    }

    /** The quest the player {@code player} tracks; null for none, or when their section is not loaded. */
    public @Nullable QuestId getTracked(UUID player) {
        QuestSection section = section(ProgressOwner.player(player));
        String tracked = section == null ? null : section.getTracked();
        return tracked == null ? null : QuestId.parse(tracked, nameOf(owner));
    }

    // ------------------------------------------------------------------
    //  Reading
    // ------------------------------------------------------------------

    /** A snapshot of the state; empty when there is none, or when a player's section is not loaded (nothing counts offline). */
    public Optional<QuestState> getState(ProgressOwner progressOwner, QuestId id) {
        synchronized (lane(progressOwner)) {
            Map<String, QuestState> states = states(progressOwner);
            QuestState state = states == null ? null : states.get(id.toString());
            return state == null ? Optional.<QuestState>empty() : Optional.of(state.copy());
        }
    }

    /** Snapshots of every state of the owner, orphans included; empty when not loaded. */
    public Collection<QuestState> getStates(ProgressOwner progressOwner) {
        synchronized (lane(progressOwner)) {
            Map<String, QuestState> states = states(progressOwner);
            List<QuestState> copies = new ArrayList<>();
            if (states != null) {
                for (QuestState state : states.values()) {
                    copies.add(state.copy());
                }
            }
            return copies;
        }
    }

    /** The owner's state ids whose quest this engine does not know: kept as they are until {@link #reset}. */
    public Collection<String> getOrphans(ProgressOwner progressOwner) {
        List<String> orphans = new ArrayList<>();
        for (QuestState state : getStates(progressOwner)) {
            if (!definitions.containsKey(QuestId.parse(state.getQuestId(), nameOf(owner)))) {
                orphans.add(state.getQuestId());
            }
        }
        return orphans;
    }

    /** {@link #getStatus(ProgressOwner, QuestId, FPlayer)} with the owner's own player (none for a group or global owner). */
    public QuestStatus getStatus(ProgressOwner progressOwner, QuestId id) {
        return getStatus(progressOwner, id, playerOf(progressOwner));
    }

    /**
     * Where the owner stands: the state's status, else LOCKED when {@code requires} or the gate fail, else AVAILABLE. The
     * gate is checked with {@code viewer} as its player - the member who would start a group or global quest. Gates that
     * reach each other through {@code quest-status} answer LOCKED where the loop closes.
     */
    public QuestStatus getStatus(ProgressOwner holder, QuestId id, @Nullable FPlayer viewer) {
        QuestDefinition definition = definitions.get(id);
        synchronized (lane(holder)) {
            Map<String, QuestState> states = states(holder);
            QuestStatus status = effectiveStatus(states == null ? null : states.get(id.toString()));
            if (status != null && status != QuestStatus.AVAILABLE) {
                return status;
            }
            if (definition == null) {
                return QuestStatus.AVAILABLE;
            }
            if (!missingRequires(holder, definition).isEmpty()) {
                return QuestStatus.LOCKED;
            }
            Set<String> chain = evaluating.get();
            String key = holder + "|" + id;
            if (!chain.add(key)) {
                warnGateLoop(chain, key, id);
                return QuestStatus.LOCKED;
            }
            try {
                boolean open = definition.getGate().check(context(holder, definition, null, viewer, null, null, null)).isPassed();
                return open ? QuestStatus.AVAILABLE : QuestStatus.LOCKED;
            } finally {
                chain.remove(key);
                if (chain.isEmpty()) {
                    evaluating.remove();
                }
            }
        }
    }

    private void warnGateLoop(Set<String> chain, String closing, QuestId id) {
        if (!gateLoopWarned.compareAndSet(false, true)) {
            return;
        }
        StringBuilder names = new StringBuilder();
        boolean inLoop = false;
        for (String key : chain) {
            inLoop |= key.equals(closing);
            if (inLoop) {
                names.append(key.substring(key.lastIndexOf('|') + 1)).append(" -> ");
            }
        }
        owner.getLog().warning("Quests {}{} gate each other through quest-status: the loop answers LOCKED - drop one edge, or use requires:.",
                names, id);
    }

    // ------------------------------------------------------------------
    //  Progress
    // ------------------------------------------------------------------

    /** The index hands an action to an active objective; true when it completed the objective. On the action's thread. */
    boolean onAction(QuestIndex.Entry entry, ActionEvent<?> event) {
        if (closed) {
            return false;
        }
        ProgressOwner progressOwner = entry.owner;
        synchronized (lane(progressOwner)) {
            QuestState state = activeState(progressOwner, entry.questId);
            QuestDefinition definition = definitions.get(entry.questId);
            if (state == null || definition == null) {
                return false;
            }
            Stage stage = definition.getStage(state.getStageId());
            if (stage == null || stage.getObjective(entry.objective.getId()) != entry.objective) {
                return false;
            }
            FPlayer player = event.getPlayer();
            ExecutionContext ctx = context(progressOwner, definition, state, player, event.getContext(), stage, entry.objective);
            if (entry.state) {
                boolean done = checkState(progressOwner, definition, state, entry.objective, ctx);
                reindex(progressOwner);
                return done;
            }
            if (!entry.filter.matches(event)) {
                return false;
            }
            ProvenanceFilter origin = entry.objective.getProvenance();
            ProvenanceTracker tracker = provenance;
            if (origin != null && tracker != null && !origin.admits(ProvenanceFilter.originOf(tracker, event.getContext()), player)) {
                return false;
            }
            for (Condition condition : entry.objective.getConditions()) {
                if (!Conditions.test(condition, ctx).isPassed()) {
                    return false;
                }
            }
            if (!entry.objective.getCosts().isEmpty()
                    && !Gate.of(Collections.<Condition>emptyList(), entry.objective.getCosts()).pay(ctx).isPassed()) {
                return false;
            }
            boolean done = advance(progressOwner, definition, state, entry.objective, entry.filter.countOf(event),
                    player == null ? null : player.getUniqueId(), ctx);
            if (done) {
                reindex(progressOwner);
            }
            return done;
        }
    }

    /** Adds {@code delta} to the objective under the owner's lock; true when that completed it. */
    private boolean advance(ProgressOwner progressOwner, QuestDefinition definition, QuestState state, Objective objective, long delta,
                            @Nullable UUID actor, ExecutionContext ctx) {
        ObjectiveState objectiveState = state.objective(objective.getId());
        if (objectiveState.isDone() || delta <= 0) {
            return false;
        }
        QuestId id = definition.getId();
        Caller caller = ctx.getCaller();
        ObjectiveProgressPreEvent pre = ECEventBus.global().postIfListened(
                ObjectiveProgressPreEvent.class,
                () -> new ObjectiveProgressPreEvent(this, id, progressOwner, caller, state.copy(),
                        objective.getId(), delta, actor));
        long counted = delta;
        if (pre != null) {
            if (pre.isCancelled() || pre.getDelta() <= 0) {
                return false;
            }
            counted = pre.getDelta();
        }
        long finalCounted = counted;
        if (actor != null && !progressOwner.isKind(ProgressOwner.PLAYER)) {
            QuestContribution contribution = state.getContributions().computeIfAbsent(actor.toString(), member -> {
                QuestContribution created = new QuestContribution();
                created.setJoinedAt(ECClock.now().toEpochMilli());
                return created;
            });
            contribution.add(objective.getId(), counted);
        }
        if (objective.getMode() == ProgressMode.EACH_MATCH) {
            objectiveState.setCount(objectiveState.getCount() + counted);
            markDirty(progressOwner, id.toString());
            post(ObjectiveProgressEvent.class,
                    () -> new ObjectiveProgressEvent(this, id, progressOwner, caller, state.copy(),
                            objective.getId(), finalCounted, actor));
            apply(objective.getOnMatch(), ctx.derive().variable("delta", String.valueOf(counted)).build());
            return false;
        }
        objectiveState.setCount(Math.min(objective.getGoal(), objectiveState.getCount() + counted));
        markDirty(progressOwner, id.toString());
        post(ObjectiveProgressEvent.class,
                () -> new ObjectiveProgressEvent(this, id, progressOwner, caller, state.copy(),
                        objective.getId(), finalCounted, actor));
        if (objectiveState.getCount() < objective.getGoal()) {
            present(progressOwner, (viewer, shown) -> shown.onProgress(viewer, this, definition, state.copy(), objective));
            return false;
        }
        closeObjective(progressOwner, definition, state, objective, actor);
        return true;
    }

    private void closeObjective(ProgressOwner progressOwner, QuestDefinition definition, QuestState state, Objective objective, @Nullable UUID actor) {
        ObjectiveState objectiveState = state.objective(objective.getId());
        objectiveState.setDone(true);
        objectiveState.setCount(Math.max(objectiveState.getCount(), objective.getGoal()));
        if (actor != null && !progressOwner.isKind(ProgressOwner.PLAYER)) {
            state.setLastActor(actor.toString());
        }
        markDirty(progressOwner, definition.getId().toString());
        Caller caller = caller(definition, state.getStageId(), objective.getId());
        post(ObjectiveCompleteEvent.class,
                () -> new ObjectiveCompleteEvent(this, definition.getId(), progressOwner, caller, state.copy(),
                        objective.getId()));
        present(progressOwner, (viewer, shown) -> shown.onObjectiveComplete(viewer, this, definition, state.copy(), objective));
        evaluateStage(progressOwner, definition, state, objective, 0);
    }

    /** Checks a state objective's condition; true when that completed it. */
    private boolean checkState(ProgressOwner progressOwner, QuestDefinition definition, QuestState state, Objective objective, ExecutionContext ctx) {
        if (state.objective(objective.getId()).isDone()) {
            return false;
        }
        ObjectiveFilter filter = objective.getFilter(QuestParams.of(state.getParams()));
        Condition condition = filter == null ? null : filter.stateOf();
        if (condition == null || !Conditions.test(condition, ctx).isPassed()) {
            return false;
        }
        closeObjective(progressOwner, definition, state, objective, null);
        return true;
    }

    private boolean isDone(QuestState state, Objective objective) {
        if (objective.isSkippedHere()) {
            return true;
        }
        ObjectiveState objectiveState = state.getObjectives().get(objective.getId());
        return objectiveState != null && objectiveState.isDone();
    }

    /** Closes the current stage when its group is closed, and moves where its then says. */
    private void evaluateStage(ProgressOwner progressOwner, QuestDefinition definition, QuestState state, @Nullable Objective closer, int hops) {
        if (state.getStatus() != QuestStatus.ACTIVE) {
            return;
        }
        Stage stage = definition.getStage(state.getStageId());
        if (stage == null) {
            return;
        }
        if (!stage.getObjectives().isClosedBy(objective -> isDone(state, objective))) {
            checkStates(progressOwner, definition, state, stage, hops);
            return;
        }
        if (hops >= MAX_STAGE_HOPS) {
            owner.getLog().warning("Quest {} of {} went through {} stages in one step; it stops at '{}' - a then cycle over stages that close"
                    + " at once never ends.", definition.getId(), progressOwner, hops, stage.getId());
            return;
        }
        Caller caller = caller(definition, stage.getId(), null);
        post(StageCompleteEvent.class,
                () -> new StageCompleteEvent(this, definition.getId(), progressOwner, caller, state.copy(),
                        stage.getId()));
        apply(stage.getOnComplete(), context(progressOwner, definition, state, playerOf(progressOwner), null, stage, null));
        present(progressOwner, (viewer, shown) -> shown.onStageComplete(viewer, this, definition, state.copy(), stage));
        Next next = stage.getObjectives().getMode() == ObjectiveGroup.Mode.ANY && closer != null && closer.getThen() != null
                ? closer.getThen() : stage.getThen();
        int index = definition.getStages().indexOf(stage);
        switch (next.getKind()) {
            case COMPLETE:
                completeQuest(progressOwner, definition, state);
                return;
            case FAIL:
                failQuest(progressOwner, definition, state, FailCause.EFFECT);
                return;
            case GOTO:
                enterStage(progressOwner, definition, state, definition.getStage(next.getStageId()), hops);
                return;
            default:
                if (index + 1 >= definition.getStages().size()) {
                    completeQuest(progressOwner, definition, state);
                } else {
                    enterStage(progressOwner, definition, state, definition.getStages().get(index + 1), hops);
                }
        }
    }

    private void enterStage(ProgressOwner progressOwner, QuestDefinition definition, QuestState state, Stage stage, int hops) {
        state.setStageId(stage.getId());
        state.setStageStartedAt(ECClock.now().toEpochMilli());
        for (Objective objective : stage.getObjectives().getObjectives()) {
            state.getObjectives().remove(objective.getId());
        }
        markDirty(progressOwner, definition.getId().toString());
        evaluateStage(progressOwner, definition, state, null, hops + 1);
    }

    /** A stage just entered (or not yet closed) may already have its state objectives true. */
    private void checkStates(ProgressOwner progressOwner, QuestDefinition definition, QuestState state, Stage stage, int hops) {
        for (Objective objective : stage.getObjectives().getObjectives()) {
            ObjectiveFilter filter = objective.getFilter(QuestParams.of(state.getParams()));
            if (filter == null || filter.getType().getNature() != ObjectiveNature.STATE || isDone(state, objective)) {
                continue;
            }
            if (checkState(progressOwner, definition, state, objective,
                    context(progressOwner, definition, state, playerOf(progressOwner), null, stage, objective))) {
                return; //closeObjective evaluated the stage again
            }
        }
    }

    private void completeQuest(ProgressOwner progressOwner, QuestDefinition definition, QuestState state) {
        QuestId id = definition.getId();
        Caller caller = caller(definition, null, null);
        if (cancelled(new QuestCompletePreEvent(this, id, progressOwner, caller, state.copy()))) {
            return;
        }
        long now = ECClock.now().toEpochMilli();
        state.setTimes(state.getTimes() + 1);
        state.setCompletedAt(now);
        state.setLastCompletedAt(now);
        state.setStageId(null);
        RepeatPolicy repeat = definition.getRepeat();
        if (repeat.isLimited() && state.getTimes() >= repeat.getLimit()) {
            state.setStatus(QuestStatus.COMPLETED);
        } else if (repeat.getCooldown() != null || repeat.getWindow() != null) {
            state.setStatus(QuestStatus.COOLDOWN);
        } else {
            state.setStatus(QuestStatus.AVAILABLE);
        }
        markDirty(progressOwner, id.toString());
        ECDebugModule.QUESTS.debug("{} completed {} for {} ({} time(s)).", nameOf(owner), id, progressOwner, state.getTimes());
        post(QuestCompleteEvent.class,
                () -> new QuestCompleteEvent(this, id, progressOwner, caller, state.copy()));
        present(progressOwner, (viewer, shown) -> shown.onQuestComplete(viewer, this, definition, state.copy()));
        apply(definition.getOnComplete(), context(progressOwner, definition, state, playerOf(progressOwner), null, null, null));
        pay(progressOwner, definition, state, RewardTrigger.ON_COMPLETE);
        for (QuestEngine engine : ENGINES.values()) {
            if (engine != this) {
                engine.recheckStates(progressOwner);
            }
        }
        recheckStatesLater(progressOwner);
    }

    private void failQuest(ProgressOwner progressOwner, QuestDefinition definition, QuestState state, FailCause cause) {
        state.setStatus(QuestStatus.FAILED);
        state.setFailCause(cause);
        state.setStageId(null);
        markDirty(progressOwner, definition.getId().toString());
        Caller caller = caller(definition, null, null);
        ECDebugModule.QUESTS.debug("{} failed {} for {}: {}.", nameOf(owner), definition.getId(), progressOwner, cause);
        post(QuestFailEvent.class,
                () -> new QuestFailEvent(this, definition.getId(), progressOwner, caller, state.copy(), cause));
        present(progressOwner, (viewer, shown) -> shown.onQuestFail(viewer, this, definition, state.copy()));
        apply(definition.getOnFail(), context(progressOwner, definition, state, playerOf(progressOwner), null, null, null));
        if (cause == FailCause.OWNER_GONE) {
            pay(progressOwner, definition, state, RewardTrigger.ON_OWNER_GONE);
        }
    }

    /**
     * Pays every rule of the quest's reward that takes {@code trigger}, through {@link Rewards#pay}: the contribution of
     * each member is the sum of what it counted on the objectives, the last actor is whose action closed the latest
     * objective (on a player's own quest, the player), and the state goes as the {@code quest} attribute.
     */
    private void pay(ProgressOwner progressOwner, QuestDefinition definition, QuestState state, RewardTrigger trigger) {
        if (!definition.getReward().paysAt(trigger)) {
            return;
        }
        Map<UUID, BigDecimal> contributions = new LinkedHashMap<>();
        for (Map.Entry<String, QuestContribution> contribution : state.getContributions().entrySet()) {
            contributions.put(UUID.fromString(contribution.getKey()), BigDecimal.valueOf(contribution.getValue().getTotal()));
        }
        UUID lastActor = state.getLastActorId();
        if (lastActor == null && progressOwner.isKind(ProgressOwner.PLAYER)) {
            lastActor = UUID.fromString(progressOwner.getId());
        }
        RewardContext ctx = RewardContext.builder(Caller.of(owner, definition.getId().getName() + "/reward"), progressOwner)
                .contributions(contributions)
                .lastActor(lastActor)
                .trigger(trigger)
                .attribute("quest", state.copy())
                .build();
        for (RewardRule rule : definition.getReward().getRules()) {
            try {
                Rewards.pay(rule, ctx).whenComplete((paid, error) -> {
                    if (error != null) {
                        owner.getLog().warning("A reward of quest {} for {} failed while paying: {}", definition.getId(), progressOwner, error.toString());
                    }
                });
            } catch (RuntimeException refused) {
                owner.getLog().warning("A reward of quest {} for {} was not paid: {}", definition.getId(), progressOwner, refused.getMessage());
            }
        }
    }

    /** Re-checks the state objectives of every active quest of the owner - another quest of it just completed. */
    void recheckStates(ProgressOwner progressOwner) {
        if (closed) {
            return;
        }
        synchronized (lane(progressOwner)) {
            Map<String, QuestState> states = states(progressOwner);
            if (states == null) {
                return;
            }
            for (QuestState state : new ArrayList<>(states.values())) {
                QuestDefinition definition = definitions.get(QuestId.parse(state.getQuestId(), nameOf(owner)));
                if (definition != null && state.getStatus() == QuestStatus.ACTIVE) {
                    evaluateStage(progressOwner, definition, state, null, 0);
                }
            }
            reindex(progressOwner);
        }
    }

    private void recheckStatesLater(ProgressOwner progressOwner) {
        //the completing instance is still inside its own lane: the owner's other quests are checked once it returns
        ECScheduler.scheduleAsync(() -> recheckStates(progressOwner), 0);
    }

    // ------------------------------------------------------------------
    //  Sweep
    // ------------------------------------------------------------------

    private void scheduleSweep() {
        ECScheduler.scheduleAsync(() -> {
            if (closed) {
                return;
            }
            try {
                sweep();
            } finally {
                scheduleSweep();
            }
        }, SWEEP_MILLIS);
    }

    /**
     * Once a minute: fails what passed its time limit or stopped passing its {@code fail-when}, expires what passed its
     * expiry, turns a cooldown that ended back to available, and samples the state objectives without needs.
     */
    void sweep() {
        Instant now = ECClock.now();
        Set<ProgressOwner> owners = new LinkedHashSet<>(activeOwners);
        owners.addAll(memory.keySet());
        owners.addAll(poolMemory.keySet());
        if (sectionClass != null && !pools.isEmpty()) {
            owners.addAll(knownOwners());
        }
        for (ProgressOwner progressOwner : owners) {
            synchronized (lane(progressOwner)) {
                Map<String, QuestState> states = states(progressOwner);
                if (states == null) {
                    continue;
                }
                for (QuestState state : new ArrayList<>(states.values())) {
                    QuestDefinition definition = definitions.get(QuestId.parse(state.getQuestId(), nameOf(owner)));
                    if (definition == null) {
                        continue;
                    }
                    sweepOne(progressOwner, definition, state, now);
                }
                for (QuestPool pool : pools.values()) {
                    pool.refresh(progressOwner);
                }
                reindex(progressOwner);
            }
        }
    }

    private void sweepOne(ProgressOwner progressOwner, QuestDefinition definition, QuestState state, Instant now) {
        String key = definition.getId().toString();
        switch (state.getStatus()) {
            case ACTIVE:
                if (definition.getWindowEnd() != null && !now.isBefore(definition.getWindowEnd())) {
                    windowEnded(progressOwner, definition, state);
                    return;
                }
                Duration limit = definition.getTimeLimit();
                if (limit != null && now.toEpochMilli() - state.getStartedAt() >= limit.toMillis()) {
                    failQuest(progressOwner, definition, state, FailCause.TIME_LIMIT);
                    return;
                }
                if (progressOwner.isKind(ProgressOwner.PLAYER) && accrueTimed(progressOwner, definition, state, UUID.fromString(progressOwner.getId()), now)) {
                    return; //closing the objective evaluated the stage
                }
                ExecutionContext ctx = context(progressOwner, definition, state, playerOf(progressOwner), null, null, null);
                for (Condition condition : definition.getFailWhen()) {
                    if (Conditions.test(condition, ctx).isPassed()) {
                        failQuest(progressOwner, definition, state, FailCause.CONDITION);
                        return;
                    }
                }
                evaluateStage(progressOwner, definition, state, null, 0);
                return;
            case AVAILABLE:
            case OFFERED:
                if (definition.getExpiresAt() != null && !now.isBefore(definition.getExpiresAt())) {
                    state.setStatus(QuestStatus.EXPIRED);
                    markDirty(progressOwner, key);
                    Caller caller = caller(definition, null, null);
                    post(QuestExpireEvent.class,
                            () -> new QuestExpireEvent(this, definition.getId(), progressOwner, caller,
                                    state.copy()));
                }
                return;
            case COOLDOWN:
                if (effectiveStatus(state) == QuestStatus.AVAILABLE) {
                    state.setStatus(QuestStatus.AVAILABLE);
                    markDirty(progressOwner, key);
                }
                return;
            default:
        }
    }

    /** A pool's window turned: the quests of its last draw the owner still has active expire as a closed window does. Under the lane. */
    void expireActive(ProgressOwner progressOwner, List<QuestId> drawn) {
        Map<String, QuestState> states = states(progressOwner);
        if (states == null) {
            return;
        }
        boolean expired = false;
        for (QuestId id : drawn) {
            QuestState state = states.get(id.toString());
            QuestDefinition definition = definitions.get(id);
            if (state != null && definition != null && state.getStatus() == QuestStatus.ACTIVE) {
                windowEnded(progressOwner, definition, state);
                expired = true;
            }
        }
        if (expired) {
            reindex(progressOwner);
        }
    }

    /** The window closed with the instance still active: its on-window-end rules pay, and it expires. */
    private void windowEnded(ProgressOwner progressOwner, QuestDefinition definition, QuestState state) {
        pay(progressOwner, definition, state, RewardTrigger.ON_WINDOW_END);
        state.setStatus(QuestStatus.EXPIRED);
        state.setStageId(null);
        markDirty(progressOwner, definition.getId().toString());
        Caller caller = caller(definition, null, null);
        post(QuestExpireEvent.class, () -> new QuestExpireEvent(this, definition.getId(), progressOwner, caller, state.copy()));
    }

    /** Brings the timed objectives of the current stage up to {@code now} for {@code player}; true when one closed. */
    @SuppressWarnings("unchecked")
    private boolean accrueTimed(ProgressOwner progressOwner, QuestDefinition definition, QuestState state, UUID player, Instant now) {
        Stage stage = definition.getStage(state.getStageId());
        if (stage == null) {
            return false;
        }
        for (Objective objective : stage.getObjectives().getObjectives()) {
            ObjectiveFilter filter = objective.getFilter(QuestParams.of(state.getParams()));
            if (filter == null || isDone(state, objective) || !(filter.getType() instanceof RegionObjectiveType)
                    || !((RegionObjectiveType<?>) filter.getType()).isTimed()) {
                continue;
            }
            RegionObjectiveType<Object> type = (RegionObjectiveType<Object>) filter.getType();
            boolean finished = type.accrue(player, filter.getFilter(), state.objective(objective.getId()), now);
            markDirty(progressOwner, definition.getId().toString());
            if (finished) {
                closeObjective(progressOwner, definition, state, objective, player);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    //  Region crossings and owners that end
    // ------------------------------------------------------------------

    /** The index hands a region crossing to an active region objective; true when it completed the objective. On the crossing's thread. */
    @SuppressWarnings("unchecked")
    boolean onCrossing(QuestIndex.Entry entry, RegionCrossedEvent event) {
        if (closed) {
            return false;
        }
        ProgressOwner progressOwner = entry.owner;
        synchronized (lane(progressOwner)) {
            QuestState state = activeState(progressOwner, entry.questId);
            QuestDefinition definition = definitions.get(entry.questId);
            if (state == null || definition == null) {
                return false;
            }
            Stage stage = definition.getStage(state.getStageId());
            if (stage == null || stage.getObjective(entry.objective.getId()) != entry.objective || isDone(state, entry.objective)) {
                return false;
            }
            RegionObjectiveType<Object> type = (RegionObjectiveType<Object>) entry.filter.getType();
            FPlayer player = event.getPlayer();
            if (type.isTimed()) {
                boolean finished = type.accrue(player.getUniqueId(), entry.filter.getFilter(), state.objective(entry.objective.getId()), ECClock.now());
                markDirty(progressOwner, definition.getId().toString());
                if (finished) {
                    closeObjective(progressOwner, definition, state, entry.objective, player.getUniqueId());
                    reindex(progressOwner);
                }
                return finished;
            }
            long count = type.countOf(event, entry.filter.getFilter());
            if (count <= 0) {
                return false;
            }
            ExecutionContext ctx = context(progressOwner, definition, state, player, null, stage, entry.objective);
            for (Condition condition : entry.objective.getConditions()) {
                if (!Conditions.test(condition, ctx).isPassed()) {
                    return false;
                }
            }
            if (!entry.objective.getCosts().isEmpty()
                    && !Gate.of(Collections.<Condition>emptyList(), entry.objective.getCosts()).pay(ctx).isPassed()) {
                return false;
            }
            boolean finished = advance(progressOwner, definition, state, entry.objective, count, player.getUniqueId(), ctx);
            if (finished) {
                reindex(progressOwner);
            }
            return finished;
        }
    }

    /**
     * The group or region that owned instances ended: every active one fails with {@link FailCause#OWNER_GONE} (its
     * on-owner-gone rules pay what was earned), then the owner's states are dropped - nothing can progress them again.
     */
    void ownerGone(ProgressOwner progressOwner) {
        if (closed) {
            return;
        }
        synchronized (lane(progressOwner)) {
            Map<String, QuestState> states = memory.get(progressOwner);
            if (states == null || states.isEmpty()) {
                return;
            }
            for (QuestState state : new ArrayList<>(states.values())) {
                QuestDefinition definition = definitions.get(QuestId.parse(state.getQuestId(), nameOf(owner)));
                if (definition != null && state.getStatus() == QuestStatus.ACTIVE) {
                    failQuest(progressOwner, definition, state, FailCause.OWNER_GONE);
                }
            }
            for (String questId : new ArrayList<>(states.keySet())) {
                markRemoved(progressOwner, questId);
            }
            memory.remove(progressOwner);
            reindex(progressOwner);
        }
    }

    private void listenToOwners() {
        subscriptions.add(ECEventBus.global().subscribe(GroupDissolvedEvent.class,
                event -> ownerGone(ProgressOwner.group(event.getGroup().getId()))));
        subscriptions.add(ECEventBus.global().subscribe(RegionRemovedEvent.class, event -> {
            if (event.getCause() != RegionRemovedEvent.Cause.OWNER_DISABLED) { //an owner's restart brings it back
                ownerGone(ProgressOwner.region(event.getRegion().getId()));
            }
        }));
    }

    /**
     * The group of {@code player} a group quest of {@code provider} belongs to: the first group they are an explicit
     * member of whose kind is {@code provider} (any group when the quest names none). Null when they are in none.
     */
    static @Nullable GroupId groupOf(UUID player, @Nullable String provider) {
        for (GroupId id : GroupLookup.get().getGroupsOf(player)) {
            if (provider == null) {
                return id;
            }
            Group managed = GroupLookup.get().byId(id);
            //ponytail: a provider's own group carries no kind here, so only the core's managed groups match a named kind
            if (managed != null && managed.getKind().getKey().toString().equalsIgnoreCase(provider)) {
                return id;
            }
        }
        return null;
    }

    /** A transient group's or region's quests live in memory only: the owner itself is gone at restart. */
    private static boolean isEphemeral(ProgressOwner progressOwner) {
        if (progressOwner.isKind(ProgressOwner.GROUP)) {
            Group group = GroupLookup.get().byId(GroupId.parse(progressOwner.getId()));
            return group != null && group.getPersistence() == Persistence.TRANSIENT;
        }
        if (progressOwner.isKind(ProgressOwner.REGION)) {
            Region region = RegionLookup.get().byId(RegionId.parse(progressOwner.getId()));
            return region != null && region.getPersistence() == Persistence.TRANSIENT;
        }
        return false;
    }

    // ------------------------------------------------------------------
    //  Index
    // ------------------------------------------------------------------

    private void reindexAll() {
        for (ProgressOwner progressOwner : knownOwners()) {
            reindex(progressOwner);
        }
    }

    /** Publishes the owner's active objectives to the index. */
    void reindex(ProgressOwner progressOwner) {
        if (closed) {
            return;
        }
        List<QuestIndex.Entry> entries = new ArrayList<>();
        synchronized (lane(progressOwner)) {
            Map<String, QuestState> states = states(progressOwner);
            boolean active = false;
            if (states != null) {
                for (QuestState state : states.values()) {
                    if (state.getStatus() != QuestStatus.ACTIVE) {
                        continue;
                    }
                    QuestDefinition definition = definitions.get(QuestId.parse(state.getQuestId(), nameOf(owner)));
                    Stage stage = definition == null ? null : definition.getStage(state.getStageId());
                    if (stage == null) {
                        continue;
                    }
                    active = true;
                    for (Objective objective : stage.getObjectives().getObjectives()) {
                        if (isDone(state, objective)) {
                            continue;
                        }
                        ObjectiveFilter filter = objective.getFilter(QuestParams.of(state.getParams()));
                        if (filter == null) {
                            continue;
                        }
                        ObjectiveType<?> type = filter.getType();
                        if (type.getNature() == ObjectiveNature.EVENT) {
                            for (EventSource source : type.getSources()) {
                                entries.add(new QuestIndex.Entry(this, progressOwner, definition.getId(), objective, filter, source,
                                        type.cancelsNative(), false));
                            }
                        } else {
                            Condition condition = filter.stateOf();
                            if (condition != null) {
                                for (EventSource source : condition.getNeeds()) {
                                    if (type.getPostEvent(source) != null) {
                                        entries.add(new QuestIndex.Entry(this, progressOwner, definition.getId(), objective, filter, source, false, true));
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (active) {
                activeOwners.add(progressOwner);
            } else {
                activeOwners.remove(progressOwner);
            }
            QuestIndex.get().publish(this, progressOwner, entries);
        }
    }

    private void listenToPlayers() {
        subscriptions.add(ECEventBus.global().subscribe(ECPlayerFullyLoggedInEvent.class,
                event -> reindex(ProgressOwner.player(event.getPlayerData().getUniqueId()))));
        subscriptions.add(ECEventBus.global().subscribe(ECPlayerQuitEvent.class, event -> {
            ProgressOwner progressOwner = ProgressOwner.player(event.getPlayerData().getUniqueId());
            activeOwners.remove(progressOwner);
            QuestIndex.get().publish(this, progressOwner, Collections.<QuestIndex.Entry>emptyList());
        }));
    }

    // ------------------------------------------------------------------
    //  Internals
    // ------------------------------------------------------------------

    Object lane(ProgressOwner progressOwner) {
        return lanes.computeIfAbsent(progressOwner, key -> new Object());
    }

    private boolean isPlayerSectionOwner(ProgressOwner progressOwner) {
        return sectionClass != null && progressOwner.isKind(ProgressOwner.PLAYER);
    }

    private @Nullable QuestSection section(ProgressOwner progressOwner) {
        if (!isPlayerSectionOwner(progressOwner)) {
            return null;
        }
        return PlayerController.getLoadedSection(UUID.fromString(progressOwner.getId()), sectionClass);
    }

    /** The owner's live state map; null for a player whose section is not loaded. */
    private @Nullable Map<String, QuestState> states(ProgressOwner progressOwner) {
        if (isPlayerSectionOwner(progressOwner)) {
            QuestSection section = section(progressOwner);
            return section == null ? null : section.getStates();
        }
        return memory.computeIfAbsent(progressOwner, key -> new LinkedHashMap<>());
    }

    private Set<ProgressOwner> knownOwners() {
        Set<ProgressOwner> owners = new LinkedHashSet<>(memory.keySet());
        owners.addAll(activeOwners);
        if (sectionClass != null) {
            for (PlayerData data : PlayerController.getAllLoaded()) {
                if (data.getPDSectionIfLoaded(sectionClass) != null) {
                    owners.add(ProgressOwner.player(data.getUniqueId()));
                }
            }
        }
        return owners;
    }

    private @Nullable QuestState activeState(ProgressOwner progressOwner, QuestId id) {
        Map<String, QuestState> states = states(progressOwner);
        QuestState state = states == null ? null : states.get(id.toString());
        return state != null && state.getStatus() == QuestStatus.ACTIVE ? state : null;
    }

    /** The status a state stands at now: a cooldown that ended reads AVAILABLE. Null for no state. */
    private @Nullable QuestStatus effectiveStatus(@Nullable QuestState state) {
        if (state == null) {
            return null;
        }
        if (state.getStatus() != QuestStatus.COOLDOWN) {
            return state.getStatus();
        }
        QuestDefinition definition = definitions.get(QuestId.parse(state.getQuestId(), nameOf(owner)));
        if (definition == null) {
            return state.getStatus();
        }
        Instant now = ECClock.now();
        RepeatPolicy repeat = definition.getRepeat();
        Duration cooldown = repeat.getCooldown();
        Window window = repeat.getWindow();
        boolean cooled = cooldown == null || now.toEpochMilli() - state.getLastCompletedAt() >= cooldown.toMillis();
        boolean turned = true;
        if (window != null) {
            Instant start = window.currentStart(now);
            turned = start != null && start.toEpochMilli() > state.getLastCompletedAt();
        }
        return cooled && turned ? QuestStatus.AVAILABLE : QuestStatus.COOLDOWN;
    }

    private List<QuestId> missingRequires(ProgressOwner progressOwner, QuestDefinition definition) {
        List<QuestId> missing = new ArrayList<>();
        for (QuestId required : definition.getRequires()) {
            QuestEngine engine = required.getOwner().equals(definition.getId().getOwner()) ? this : find(required.getOwner());
            Optional<QuestState> state = engine == null ? Optional.<QuestState>empty() : engine.getState(progressOwner, required);
            if (!state.isPresent() || state.get().getTimes() < 1) {
                missing.add(required);
            }
        }
        return missing;
    }

    /** The open engine of the plugin named {@code pluginName}, ignoring case; null when it has none. */
    public static @Nullable QuestEngine find(String pluginName) {
        for (QuestEngine engine : ENGINES.values()) {
            if (nameOf(engine.owner).equalsIgnoreCase(pluginName)) {
                return engine;
            }
        }
        return null;
    }

    private static QuestParams resolveParams(QuestDefinition definition, QuestParams given) {
        if (definition.getParams().isEmpty()) {
            return given;
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, QuestDefinition.Param> param : definition.getParams().entrySet()) {
            String value = given.get(param.getKey());
            if (value == null) {
                List<String> choices = param.getValue().getValues();
                value = param.getValue().isRandom() ? choices.get(ThreadLocalRandom.current().nextInt(choices.size())) : choices.get(0);
            }
            values.put(param.getKey(), value);
        }
        for (Map.Entry<String, String> extra : given.asMap().entrySet()) {
            values.putIfAbsent(extra.getKey(), extra.getValue());
        }
        return QuestParams.of(values);
    }

    private interface Presentation {
        void show(FPlayer viewer, QuestPresenter presenter);
    }

    /** Shows a moment to the players {@code progressOwner} concerns; a failing presenter is logged, the quest moves on. */
    private void present(ProgressOwner progressOwner, Presentation presentation) {
        QuestPresenter shown = presenter;
        for (FPlayer viewer : audienceOf(progressOwner)) {
            try {
                presentation.show(viewer, shown);
            } catch (RuntimeException failed) {
                owner.getLog().warning("The quest presenter of {} failed for {}: {}", nameOf(owner), viewer.getName(), failed.toString());
            }
        }
    }

    /** The player of a player's quest, the online members of a group's; nobody for the other owners. */
    private List<FPlayer> audienceOf(ProgressOwner progressOwner) {
        if (progressOwner.isKind(ProgressOwner.PLAYER)) {
            FPlayer player = playerOf(progressOwner);
            return player == null ? Collections.<FPlayer>emptyList() : Collections.singletonList(player);
        }
        if (!progressOwner.isKind(ProgressOwner.GROUP)) {
            return Collections.emptyList();
        }
        List<FPlayer> online = new ArrayList<>();
        for (UUID member : GroupLookup.get().getMembers(GroupId.parse(progressOwner.getId()))) {
            FPlayer player = playerOf(ProgressOwner.player(member));
            if (player != null) {
                online.add(player);
            }
        }
        return online;
    }

    private @Nullable FPlayer playerOf(ProgressOwner progressOwner) {
        if (!progressOwner.isKind(ProgressOwner.PLAYER)) {
            return null;
        }
        try {
            FPlayer player = EverNifeCore.getPlatform().getPlayer(UUID.fromString(progressOwner.getId()));
            return player != null && player.isOnline() ? player : null;
        } catch (RuntimeException unanswered) {
            return null; //a platform that cannot look players up: the context goes without one
        }
    }

    private Caller caller(QuestDefinition definition, @Nullable String stageId, @Nullable String objectiveId) {
        String origin = definition.getId().getName() + (stageId == null ? "" : "/stage:" + stageId) + (objectiveId == null ? "" : "/objective:" + objectiveId);
        return Caller.of(owner, origin);
    }

    private ExecutionContext context(ProgressOwner progressOwner, QuestDefinition definition, @Nullable QuestState state, @Nullable FPlayer player,
                                     @Nullable ActionContext action, @Nullable Stage stage, @Nullable Objective objective) {
        ExecutionContext.Builder builder = ExecutionContext.builder(caller(definition, stage == null ? null : stage.getId(),
                objective == null ? null : objective.getId())).owner(progressOwner).player(player).action(action);
        if (state != null) {
            builder.attribute("quest", state.copy());
            for (Map.Entry<String, String> param : state.getParams().entrySet()) {
                builder.variable(param.getKey(), param.getValue());
            }
        }
        return builder.build();
    }

    private static void apply(Effect effect, ExecutionContext ctx) {
        Effects.apply(ctx, effect);
    }

    private static <T extends IECEvent> void post(Class<T> type, Supplier<? extends T> event) {
        ECEventBus.global().postIfListened(type, event);
    }

    private static boolean cancelled(ECCancellable event) {
        if (!ECEventBus.global().hasListeners(((IECEvent) event).getClass())) {
            return false;
        }
        ECEventBus.global().post((IECEvent) event);
        return event.isCancelled();
    }

    private static <T> CompletableFuture<T> done(T value) {
        return CompletableFuture.completedFuture(value);
    }

    private static CompletableFuture<Void> failed(String why) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        future.completeExceptionally(new IllegalStateException(why));
        return future;
    }

    private String unknown(QuestId id) {
        return "The QuestEngine of " + nameOf(owner) + " has no quest '" + id + "'. Its quests are: " + definitions.keySet() + ".";
    }

    private static String notLoaded(ProgressOwner progressOwner, @Nullable QuestId id) {
        return "The quests of " + progressOwner + " are not loaded (an offline player has none in memory)" + (id == null ? "" : ", so '" + id
                + "' cannot change now") + ". Try again once the player is online.";
    }

    private static String nameOf(ECPluginData owner) {
        return owner.getMetaInfo().getName();
    }

    @Override
    public String toString() {
        return "QuestEngine(" + nameOf(owner) + ", " + definitions.size() + " quest(s))";
    }
}
