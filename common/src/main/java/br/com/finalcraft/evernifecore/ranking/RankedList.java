package br.com.finalcraft.evernifecore.ranking;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.MessagePlaceholders;
import br.com.finalcraft.evernifecore.pageviewer.PageViewer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.time.ECClock;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * The data half of any ordered list: where the entries come from (a plain or a {@link CompletableFuture} source), how
 * many are kept, in which order, which values each entry answers, and how long one read stays good for. The chat page
 * ({@link PageViewer}), the placeholders ({@link RankedPlaceholders}) and any screen read the SAME
 * {@link RankedSnapshot}, so the expensive half - reading and ordering - is paid once whoever asks.
 *
 * <pre>{@code
 * RankedList<FJPlayerData> top = RankedList.of(plugin, "miner-level", FJPlayerData.class)
 *         .sourceAsync(() -> PlayerController.querySections(FJPlayerData.class, Query.all(), options))
 *         .maxEntries(100)
 *         .orderBy(data -> data.getLevel()).descending()
 *         .cache(CachePolicy.ttl(Duration.ofSeconds(60)))
 *         .addValue("player", data -> UUIDsController.getNameFromUUID(data.getUniqueId()))
 *         .build();
 * }</pre>
 */
public final class RankedList<T> {

    // Keys every list answers for on its own, unless the owner declared one of them itself.
    public static final String NUMBER_KEY = "number";
    public static final String VALUE_KEY = "value";
    public static final String PLAYER_KEY = "player";

    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,64}");
    private static final CachePolicy DEFAULT_CACHE_POLICY = CachePolicy.ttl(Duration.ofSeconds(5));

    private final @Nullable String id;
    private final @Nullable ECPluginData owner;
    private final Class<T> target;
    private final boolean aboutPlayers;
    private final Supplier<CompletableFuture<List<T>>> source;
    private final boolean asyncSource;
    private final Function<List<T>, Integer> maxEntries;
    private final @Nullable Comparator<RankedEntry<T>> order;
    private final @Nullable Function<T, ?> orderExtractor;
    private final CachePolicy cachePolicy;
    private final Map<String, Declaration<T>> values;

    private final Object lock = new Object();

    // Published by reference and never mutated: a reader holding one reads one whole list.
    private volatile @Nullable RankedSnapshot<T> cached;
    // Everything below is guarded by 'lock'.
    private @Nullable CompletableFuture<RankedSnapshot<T>> inFlight;
    private @Nullable CompletableFuture<RankedSnapshot<T>> lastFailure;
    // Stands in for the failed read when the policy is asked whether to try the source again: a source that is down is
    // tried once per lifetime of a snapshot, not once per reader.
    private @Nullable RankedSnapshot<T> failureProbe;
    private long generation;

    private volatile @Nullable PageViewer<T> pageViewer;

    private RankedList(BuilderImp<T> builder) {
        this.id = builder.id;
        this.owner = builder.owner;
        this.target = builder.target;
        this.aboutPlayers = FPlayer.class.isAssignableFrom(target) || IPlayerData.class.isAssignableFrom(target);
        this.source = builder.source;
        this.asyncSource = builder.asyncSource;
        this.maxEntries = builder.maxEntries;
        this.orderExtractor = builder.orderExtractor;
        this.order = builder.orderExtractor == null ? null : orderOf(builder.descending);
        this.cachePolicy = builder.cachePolicy;
        this.values = declarationsOf(builder.values);
    }

    // ------------------------------------------------------------------
    //  Identity
    // ------------------------------------------------------------------

    /**
     * A list other code can name: id {@code <owner>:<name>} (owner = the plugin's name lower-cased, name in
     * {@code [a-z0-9_-]{1,64}}, case-insensitive), registered in {@link RankedLists} on {@code build()}. Building the
     * same id again replaces the registration - that is what a plugin reload looks like.
     */
    public static <T> Builder<T> of(ECPluginData owner, String name, Class<T> target) {
        Objects.requireNonNull(owner, "owner");
        String lowered = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (!NAME.matcher(lowered).matches()) {
            throw new IllegalArgumentException("The ranked list name '" + name + "' of " + owner.getMetaInfo().getName()
                    + " is not a valid name: use 1 to 64 of a-z, 0-9, '_' and '-' (it becomes the id "
                    + "'<plugin>:<name>' typed in /ecpage and in placeholders). A list that depends on a command "
                    + "argument has no stable name - build it with RankedList.anonymous(type) instead.");
        }
        BuilderImp<T> builder = new BuilderImp<>(target);
        builder.owner = owner;
        builder.id = owner.getMetaInfo().getName().toLowerCase(Locale.ROOT) + ":" + lowered;
        return builder;
    }

    /** A list built for one invocation (it depends on a command argument): no id, never registered, no placeholders. */
    public static <T> Builder<T> anonymous(Class<T> target) {
        return new BuilderImp<>(target);
    }

    /** {@code <owner>:<name>}, or {@code null} for an anonymous list. */
    public @Nullable String getId() {
        return id;
    }

    public @Nullable ECPluginData getOwner() {
        return owner;
    }

    public Class<T> getTarget() {
        return target;
    }

    /** Whether the entries are players ({@link FPlayer} or {@link IPlayerData}) - what {@code ${player}} and positions need. */
    public boolean isAboutPlayers() {
        return aboutPlayers;
    }

    /** Whether the list declared an order; without one it keeps what the source returned. */
    public boolean isOrdered() {
        return orderExtractor != null;
    }

    /** The chat renderer the last {@code PageViewer.of(this).build()} attached - what {@code /ecpage <id>} sends. */
    public @Nullable PageViewer<T> getPageViewer() {
        return pageViewer;
    }

    /** Called by {@code PageViewer.Builder.build()}; the newest renderer wins. */
    public void setPageViewer(@Nullable PageViewer<T> pageViewer) {
        this.pageViewer = pageViewer;
    }

    // ------------------------------------------------------------------
    //  The cached read
    // ------------------------------------------------------------------

    /**
     * The current read. Already complete when the cache policy still vouches for the cached snapshot; otherwise ONE
     * read is started and every caller arriving meanwhile shares it. A plain source is read on the CALLING thread; an
     * async source never blocks the caller. A read that fails completes exceptionally, keeps the previous snapshot
     * (still served by {@link #snapshotIfPresent()}) and is not retried until the policy would have expired a
     * snapshot built at the moment of the failure.
     */
    public CompletableFuture<RankedSnapshot<T>> snapshot() {
        RankedSnapshot<T> current = cached;
        if (cachePolicy.isValid(current)) {
            return CompletableFuture.completedFuture(current);
        }
        synchronized (lock) {
            current = cached;
            if (cachePolicy.isValid(current)) {
                return CompletableFuture.completedFuture(current);
            }
            if (inFlight != null) {
                return inFlight;
            }
            if (lastFailure != null && cachePolicy.isValid(failureProbe)) {
                return current != null ? CompletableFuture.completedFuture(current) : lastFailure;
            }
            return startRead();
        }
    }

    /** The cached snapshot, valid or not, or {@code null} when none is held. Never reads, never blocks, any thread. */
    public @Nullable RankedSnapshot<T> snapshotIfPresent() {
        return cached;
    }

    /** Reads now whatever the policy says - sharing a read already in flight. */
    public CompletableFuture<RankedSnapshot<T>> refresh() {
        synchronized (lock) {
            return inFlight != null ? inFlight : startRead();
        }
    }

    /**
     * Drops the cached read, so the next {@link #snapshot()} reads again. Under the same lock a read publishes with:
     * a read already in flight still answers its callers, but can no longer put what it built in the cache - with
     * {@link CachePolicy#manual()} that stale read would otherwise be served forever.
     */
    public void invalidate() {
        synchronized (lock) {
            cached = null;
            inFlight = null;
            lastFailure = null;
            failureProbe = null;
            generation++;
        }
    }

    // Called holding 'lock'. A plain source runs inside it, so concurrent readers wait for this read and share it.
    private CompletableFuture<RankedSnapshot<T>> startRead() {
        long readGeneration = generation;
        CompletableFuture<RankedSnapshot<T>> result = new CompletableFuture<>();
        inFlight = result;

        CompletableFuture<List<T>> read;
        try {
            read = source.get();
            if (read == null) {
                throw new IllegalStateException("The source of the ranked list " + describe() + " returned a null "
                        + (asyncSource ? "future" : "list") + "; return an empty list when there is nothing to rank.");
            }
        } catch (Throwable failure) {
            read = new CompletableFuture<>();
            read.completeExceptionally(failure);
        }

        read.whenComplete((entries, failure) -> {
            RankedSnapshot<T> built = null;
            Throwable error = failure;
            if (error == null) {
                try {
                    built = buildSnapshot(entries);
                } catch (Throwable buildFailure) {
                    error = buildFailure;
                }
            }
            synchronized (lock) {
                if (inFlight == result) {
                    inFlight = null;
                }
                if (generation == readGeneration) {
                    if (error == null) {
                        //held only if the policy would vouch for it again: a list of ten thousand entries under
                        //none() is ten thousand entries of memory nobody may serve
                        cached = cachePolicy.isValid(built) ? built : null;
                        lastFailure = null;
                        failureProbe = null;
                    } else {
                        lastFailure = result;
                        failureProbe = new RankedSnapshot<>(Collections.<RankedEntry<T>>emptyList(), 0, ECClock.now().toEpochMilli());
                    }
                }
            }
            if (error == null) {
                result.complete(built);
            } else {
                result.completeExceptionally(error);
            }
        });
        return result;
    }

    private RankedSnapshot<T> buildSnapshot(List<T> entries) {
        //decided against the RAW list, so a ceiling that depends on how much came back reads what it asked about
        int ceiling = Math.max(0, maxEntries.apply(entries));

        List<RankedEntry<T>> ordered = new ArrayList<>(entries.size());
        for (T entry : entries) {
            ordered.add(new RankedEntry<>(entry, orderExtractor == null ? null : orderExtractor.apply(entry), 0));
        }
        if (order != null) {
            ordered.sort(order);
        }

        int kept = Math.min(ceiling, ordered.size());
        List<RankedEntry<T>> numbered = new ArrayList<>(kept);
        for (int index = 0; index < kept; index++) {
            numbered.add(ordered.get(index).at(index + 1));
        }
        return new RankedSnapshot<>(numbered, ordered.size(), ECClock.now().toEpochMilli());
    }

    private String describe() {
        return id != null ? id : "<anonymous " + target.getSimpleName() + ">";
    }

    // ------------------------------------------------------------------
    //  Values
    // ------------------------------------------------------------------

    /** Every key {@link #valueOf} answers: the declared ones plus {@code number}, {@code value} and, for players, {@code player}. */
    public Set<String> getValueKeys() {
        return values.keySet();
    }

    /**
     * The value {@code key} (case-insensitive) of one entry, memoized on the entry: N readers still cost one call.
     * {@code null} for a key this list does not answer.
     */
    public @Nullable Object valueOf(RankedEntry<T> entry, String key) {
        Declaration<T> declaration = values.get(key.toLowerCase(Locale.ROOT));
        if (declaration == null) {
            return null;
        }
        return entry.resolveOnce(declaration, () -> declaration.function.apply(entry));
    }

    private static final class Declaration<T> {
        final Function<RankedEntry<T>, Object> function;

        Declaration(Function<RankedEntry<T>, Object> function) {
            this.function = function;
        }
    }

    private Map<String, Declaration<T>> declarationsOf(Map<String, Function<T, Object>> declared) {
        Map<String, Declaration<T>> map = new LinkedHashMap<>();
        for (Map.Entry<String, Function<T, Object>> value : declared.entrySet()) {
            Function<T, Object> function = value.getValue();
            map.put(value.getKey().toLowerCase(Locale.ROOT), new Declaration<T>(entry -> function.apply(entry.getObject())));
        }
        //the automatic answers are defaults: a key the owner declared meant it
        map.putIfAbsent(NUMBER_KEY, new Declaration<T>(RankedEntry::getPosition));
        map.putIfAbsent(VALUE_KEY, new Declaration<T>(orderExtractor != null
                ? RankedEntry::getOrderValue
                //no order: what the entry says about itself beats handing the reader a raw token
                : RankedEntry::getObject));
        if (aboutPlayers) {
            map.putIfAbsent(PLAYER_KEY, new Declaration<T>(entry -> nameOf(entry.getObject())));
        }
        return Collections.unmodifiableMap(map);
    }

    static String nameOf(Object object) {
        if (object instanceof FPlayer) return ((FPlayer) object).getName();
        if (object instanceof IPlayerData) return ((IPlayerData) object).getName();
        return String.valueOf(object);
    }

    // ------------------------------------------------------------------
    //  Order
    // ------------------------------------------------------------------

    private static <T> Comparator<RankedEntry<T>> orderOf(boolean descending) {
        Comparator<RankedEntry<T>> byValue = (left, right) -> compareValues(left.getOrderValue(), right.getOrderValue());
        //the direction asked for, and no other inversion on top of it
        return descending ? byValue.reversed() : byValue;
    }

    /**
     * Numbers compare numerically whatever boxes they arrived in, constants of one enum in declaration order, two
     * values of the very same {@link Comparable} type the way that type says, and anything else as text,
     * case-insensitively. Every branch answers the same for both operands or is not taken: a comparison that holds one
     * way and not the other is what a sort past 32 entries reports as "Comparison method violates its general
     * contract!". A null sorts first ascending.
     */
    @SuppressWarnings("unchecked")
    static int compareValues(@Nullable Object left, @Nullable Object right) {
        if (left == right) return 0;
        if (left == null) return -1;
        if (right == null) return 1;

        if (left instanceof Number && right instanceof Number) {
            return Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue());
        }
        if (left instanceof CharSequence && right instanceof CharSequence) {
            return String.CASE_INSENSITIVE_ORDER.compare(left.toString(), right.toString());
        }
        //asked of the declaring enum, not of the value's class: a constant with a body is a subclass of its enum
        if (left instanceof Enum && right instanceof Enum
                && ((Enum<?>) left).getDeclaringClass() == ((Enum<?>) right).getDeclaringClass()) {
            return Integer.compare(((Enum<?>) left).ordinal(), ((Enum<?>) right).ordinal());
        }
        if (left instanceof Comparable && left.getClass() == right.getClass()) {
            return ((Comparable<Object>) left).compareTo(right);
        }
        return String.CASE_INSENSITIVE_ORDER.compare(String.valueOf(left), String.valueOf(right));
    }

    // ------------------------------------------------------------------
    //  Builder
    // ------------------------------------------------------------------

    public interface Builder<T> {

        /** A source read on the calling thread - for data already in memory. */
        StepLimit<T> source(Supplier<List<T>> source);

        /** A source that answers later - a backend query. The caller never blocks; one read is in flight at a time. */
        StepLimit<T> sourceAsync(Supplier<CompletableFuture<List<T>>> source);
    }

    public interface StepLimit<T> {

        /** Keeps at most {@code maxEntries}; what the source returned beyond it is counted, not kept. */
        StepOrder<T> maxEntries(int maxEntries);

        /** The ceiling decided at each read, against the RAW list the source returned. */
        StepOrder<T> maxEntries(Function<List<T>, Integer> maxEntries);

        /** Every entry the source returned, however many. Say it out loud: it is a real risk. */
        StepOrder<T> unlimitedEntries();
    }

    public interface StepOrder<T> extends Finish<T> {

        /**
         * Orders by the value extracted here, which is also what {@code ${value}} answers. A list with no order keeps
         * what the source returned, and {@code ${value}} is the entry itself.
         */
        Direction<T> orderBy(Function<T, ?> extractor);
    }

    public interface Direction<T> {

        Finish<T> ascending();

        Finish<T> descending();
    }

    public interface Finish<T> {

        /** How long one read stays good for. Defaults to {@code ttl(5s)}. */
        Finish<T> cache(CachePolicy policy);

        /**
         * Declares {@code ${key}} (case-insensitive) for every entry, resolved once per entry per snapshot and shared by
         * every reader and placeholder. The bare name - {@code "kills"}, never {@code "%kills%"} or {@code "${kills}"}.
         * {@code number}, {@code value} and {@code player} are answered automatically unless declared here.
         */
        Finish<T> addValue(String key, Function<T, Object> function);

        /** Builds the list and, when it has an id, registers it in {@link RankedLists} (replacing one of the same id). */
        RankedList<T> build();
    }

    private static final class BuilderImp<T> implements Builder<T>, StepLimit<T>, StepOrder<T>, Direction<T> {

        private final Class<T> target;
        private @Nullable String id;
        private @Nullable ECPluginData owner;
        private Supplier<CompletableFuture<List<T>>> source;
        private boolean asyncSource;
        private Function<List<T>, Integer> maxEntries;
        private @Nullable Function<T, ?> orderExtractor;
        private boolean descending;
        private CachePolicy cachePolicy = DEFAULT_CACHE_POLICY;
        private final Map<String, Function<T, Object>> values = new LinkedHashMap<>();

        private BuilderImp(Class<T> target) {
            this.target = Objects.requireNonNull(target, "target");
        }

        @Override
        public StepLimit<T> source(Supplier<List<T>> source) {
            Objects.requireNonNull(source, "source");
            this.source = () -> {
                List<T> entries = source.get();
                return entries == null ? null : CompletableFuture.completedFuture(entries);
            };
            this.asyncSource = false;
            return this;
        }

        @Override
        public StepLimit<T> sourceAsync(Supplier<CompletableFuture<List<T>>> source) {
            this.source = Objects.requireNonNull(source, "source");
            this.asyncSource = true;
            return this;
        }

        @Override
        public StepOrder<T> maxEntries(int maxEntries) {
            this.maxEntries = entries -> maxEntries;
            return this;
        }

        @Override
        public StepOrder<T> maxEntries(Function<List<T>, Integer> maxEntries) {
            this.maxEntries = Objects.requireNonNull(maxEntries, "maxEntries");
            return this;
        }

        @Override
        public StepOrder<T> unlimitedEntries() {
            this.maxEntries = entries -> Integer.MAX_VALUE;
            return this;
        }

        @Override
        public Direction<T> orderBy(Function<T, ?> extractor) {
            this.orderExtractor = Objects.requireNonNull(extractor, "extractor");
            return this;
        }

        @Override
        public Finish<T> ascending() {
            this.descending = false;
            return this;
        }

        @Override
        public Finish<T> descending() {
            this.descending = true;
            return this;
        }

        @Override
        public Finish<T> cache(CachePolicy policy) {
            this.cachePolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        @Override
        public Finish<T> addValue(String key, Function<T, Object> function) {
            MessagePlaceholders.warnOnceIfDelimited(key);
            values.put(key, Objects.requireNonNull(function, "function"));
            return this;
        }

        @Override
        public RankedList<T> build() {
            RankedList<T> list = new RankedList<>(this);
            if (list.id != null) {
                RankedLists.register(list);
            }
            return list;
        }
    }
}
