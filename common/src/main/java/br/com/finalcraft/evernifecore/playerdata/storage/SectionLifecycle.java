package br.com.finalcraft.evernifecore.playerdata.storage;

import java.time.Duration;

/**
 * When a PDSection's cell ENTERS memory and when it LEAVES - the whole cache lifecycle of a section
 * in one descriptor.
 *
 * <p>There is no separate "hot-load" or "warmup" knob: those described the same two questions from
 * different angles. A lifecycle answers both:</p>
 *
 * <table>
 *   <caption>The four modes</caption>
 *   <tr><th>Mode</th><th>Enters</th><th>Leaves</th></tr>
 *   <tr><td>{@link #LAZY}</td><td>first effective access</td><td>an idle grace after the owner goes offline</td></tr>
 *   <tr><td>{@link #ONLINE}</td><td>the player's login</td><td>an idle grace after the owner goes offline</td></tr>
 *   <tr><td>{@link #RESIDENT}</td><td>the player's login</td><td>never</td></tr>
 *   <tr><td>{@link #PRELOADED}</td><td>the whole collection, at bind time</td><td>never</td></tr>
 * </table>
 *
 * <p>{@code LAZY} and {@code ONLINE} differ ONLY in when the first read happens: the login load and
 * a plugin's {@code getPDSection} run the very same resolution. Loading at login costs one seeded
 * transient default per logging-in player (cache-only, no write), which is why {@code LAZY} is the
 * default and {@code ONLINE} is what you pick for data read on the join tick itself.</p>
 *
 * <p>Memory is bounded by the release rule, not by a freshness policy: a cell of an offline owner
 * goes away by itself. An extra hard ceiling is available through
 * {@code PDSectionConfiguration.Builder#maxCached(int)}.</p>
 */
public enum SectionLifecycle {

    /** Nothing is loaded until someone asks; released once the owner has been offline for the grace. */
    LAZY(false, false, true),
    /** Loaded at the owner's login; released once the owner has been offline for the grace. */
    ONLINE(true, false, true),
    /** Loaded at the owner's login and never released. */
    RESIDENT(true, false, false),
    /** The whole collection is loaded at bind time and never released. */
    PRELOADED(true, true, false);

    /**
     * Default time a cell survives after its owner stops being online. An hour: a section holds an id,
     * not a payload, so keeping a few of them through a dinner break costs almost nothing, while a
     * short grace turns every reconnect into a fresh backend read. Admins who need the memory back
     * sooner lower it in storage.yml ({@code playerdata.default-idle-grace-seconds}, or per section).
     */
    public static final Duration DEFAULT_IDLE_GRACE = Duration.ofHours(1);

    /** Cached-size at which a never-releasing section triggers the "too large" DEBUG warning. */
    public static final int NEVER_RELEASED_WARN_THRESHOLD = 100_000;

    private final boolean loadsOnLogin;
    private final boolean preloadsAtBind;
    private final boolean releasesWhenIdle;

    SectionLifecycle(boolean loadsOnLogin, boolean preloadsAtBind, boolean releasesWhenIdle) {
        this.loadsOnLogin = loadsOnLogin;
        this.preloadsAtBind = preloadsAtBind;
        this.releasesWhenIdle = releasesWhenIdle;
    }

    /** Whether the login pipeline resolves this section for the player who just logged in. */
    public boolean loadsOnLogin() {
        return loadsOnLogin;
    }

    /** Whether binding the section pre-loads its entire collection. */
    public boolean preloadsAtBind() {
        return preloadsAtBind;
    }

    /** Whether a cell is released once its owner has been offline for the configured grace. */
    public boolean releasesWhenIdle() {
        return releasesWhenIdle;
    }
}
