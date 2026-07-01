package br.com.finalcraft.evernifecore.playerdata.storage;

import br.com.finalcraft.everydatabase.manager.cache.CacheOptions;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;

import java.time.Duration;
import java.util.Objects;

/**
 * The developer-declared cache lifecycle of a PDSection - the EverNifeCore-side policy that maps to
 * an EveryDatabase {@link CacheOptions}, plus the framework-only behaviors the store does not model
 * (evict-on-quit, timer-driven purge) which the controller layers over evict/preloadAll/getAll.
 *
 * <p>Four modes:</p>
 * <ul>
 *   <li>{@link #resident()} - the DEFAULT: {@code always()} + unbounded. The working set is the
 *       loaded set; a cell is never time-evicted and stays the canonical live instance. Suitable
 *       for the small, id-only sections this framework expects (see the HARD size rule below).</li>
 *   <li>{@link #lru(int)} - {@code always()} + a bounded LRU (the store keeps at most {@code maxSize}
 *       hottest cells). A safety bound for a section that legitimately spans more players than fit
 *       in memory.</li>
 *   <li>{@link #ttl(Duration)} - EveryDatabase {@code ttl(duration)} freshness AND the framework
 *       schedules {@link br.com.finalcraft.everydatabase.manager.CachingManager#purgeExpired()} on a
 *       timer for this manager (ttl alone bounds freshness, not memory - it does not evict).</li>
 *   <li>{@link #workingSet()} - {@code always()} + unbounded, but the controller EVICTS a player's
 *       cell after a short grace TTL once they quit (a resident cache scoped to online players).</li>
 * </ul>
 *
 * <p><b>HARD size rule:</b> large data does NOT belong in a PDSection. A section holds only the id
 * of a robust entity (guild, analytics, inventory blob) that lives in its own collection - that is
 * what makes {@code resident()} safe by default. If a resident manager grows past
 * {@value #RESIDENT_WARN_THRESHOLD} cached cells the framework logs a DEBUG warning (it never cuts):
 * either the section is carrying data it shouldn't, or it should declare {@link #lru(int)}/{@link #ttl(Duration)}.</p>
 */
public final class SectionCachePolicy {

    /** Cached-size at which a {@code resident} manager triggers the DEBUG "too large" warning. */
    public static final int RESIDENT_WARN_THRESHOLD = 100_000;

    /** Default grace after a quit before a {@code workingSet} cell is evicted (reconnect + in-flight save window). */
    public static final Duration DEFAULT_WORKING_SET_GRACE = Duration.ofSeconds(60);

    /** How eagerly a section's cache is warmed. Default {@link #NONE} (lazy - load on demand). */
    public enum Warmup {
        /** Lazy: nothing is pre-loaded; each cell loads on its first read. The default. */
        NONE,
        /** Warm the entire collection at bind time ({@code preloadAll}). */
        ALL
    }

    enum Mode {
        RESIDENT,
        LRU,
        TTL,
        WORKING_SET
    }

    private final Mode mode;
    private final int maxSize;        // LRU only
    private final Duration ttl;       // TTL only
    private final Duration grace;     // WORKING_SET only

    private SectionCachePolicy(Mode mode, int maxSize, Duration ttl, Duration grace) {
        this.mode = mode;
        this.maxSize = maxSize;
        this.ttl = ttl;
        this.grace = grace;
    }

    /** Resident (the default): {@code always()} + unbounded - the loaded set stays cached. */
    public static SectionCachePolicy resident() {
        return new SectionCachePolicy(Mode.RESIDENT, CacheOptions.UNBOUNDED, null, null);
    }

    /** Bounded LRU: {@code always()} keeping at most {@code maxSize} hottest cells. */
    public static SectionCachePolicy lru(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("lru(maxSize) requires a positive bound, got " + maxSize);
        }
        return new SectionCachePolicy(Mode.LRU, maxSize, null, null);
    }

    /** TTL freshness + a scheduled {@code purgeExpired()} to release expired cells from memory. */
    public static SectionCachePolicy ttl(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive, got " + ttl);
        }
        return new SectionCachePolicy(Mode.TTL, CacheOptions.UNBOUNDED, ttl, null);
    }

    /** Working set: resident while online, evicted a short grace after the player quits (default 60s). */
    public static SectionCachePolicy workingSet() {
        return new SectionCachePolicy(Mode.WORKING_SET, CacheOptions.UNBOUNDED, null, DEFAULT_WORKING_SET_GRACE);
    }

    /** Working set with an explicit reconnect grace. */
    public static SectionCachePolicy workingSet(Duration grace) {
        Objects.requireNonNull(grace, "grace");
        if (grace.isNegative()) {
            throw new IllegalArgumentException("grace must not be negative, got " + grace);
        }
        return new SectionCachePolicy(Mode.WORKING_SET, CacheOptions.UNBOUNDED, null, grace);
    }

    Mode getMode() {
        return mode;
    }

    public boolean isResident() {
        return mode == Mode.RESIDENT;
    }

    public boolean isWorkingSet() {
        return mode == Mode.WORKING_SET;
    }

    public boolean isTtl() {
        return mode == Mode.TTL;
    }

    /** Grace after quit before a working-set cell is evicted; {@link Duration#ZERO} when not applicable. */
    public Duration getWorkingSetGrace() {
        return grace != null ? grace : Duration.ZERO;
    }

    /** The TTL freshness window, or {@code null} when this is not a TTL policy. */
    public Duration getTtl() {
        return ttl;
    }

    /** The EveryDatabase store options this policy maps to (freshness + LRU capacity). */
    public CacheOptions toCacheOptions() {
        switch (mode) {
            case LRU:
                return CacheOptions.builder().policy(CachePolicy.always()).maxSize(maxSize).build();
            case TTL:
                return CacheOptions.of(CachePolicy.ttl(ttl));
            case RESIDENT:
            case WORKING_SET:
            default:
                return CacheOptions.of(CachePolicy.always());   // unbounded, resident
        }
    }

    @Override
    public String toString() {
        switch (mode) {
            case LRU:         return "SectionCachePolicy{LRU maxSize=" + maxSize + "}";
            case TTL:         return "SectionCachePolicy{TTL=" + ttl.getSeconds() + "s}";
            case WORKING_SET: return "SectionCachePolicy{WORKING_SET grace=" + getWorkingSetGrace().getSeconds() + "s}";
            case RESIDENT:
            default:          return "SectionCachePolicy{RESIDENT}";
        }
    }
}
