package br.com.finalcraft.evernifecore.cooldown;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.cooldown.player.PlayerCooldownsLocal;
import br.com.finalcraft.evernifecore.cooldown.player.PlayerCooldownsNetwork;
import br.com.finalcraft.evernifecore.cooldown.server.ServerCooldowns;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.logic.RepeatPolicy;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.time.ECClock;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.time.Window;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The one handle over a counter of "may it happen again?": a {@link CooldownEntry} read against a {@link RepeatPolicy}
 * and kept in agreement with the storage it lives in by its {@link Route}. Two vocabularies on one class: the policy's
 * ({@link #canUse()}, {@link #tryUse()}, {@link #giveBack()}, {@link #getUsesLeft()}, {@link #explain(Locale)}) and the
 * bare cooldown's ({@link #isInCooldown()}, {@link #startWith(long)}, {@link #warnPlayer(FCommandSender)}), which over
 * {@link RepeatPolicy#unlimited()} is the whole story.
 *
 * <p>The entry is the value and this object only a view of it: several handles may share one entry, and an entry may
 * outlive every handle over it. Time is read from {@link ECClock}.</p>
 */
public final class Cooldown {

    /**
     * Internal: where a handle's entry lives. Told after every mutation able to reach storage, with the mutation clock
     * already stamped: hold the entry while it is persistent, drop it once it is not.
     */
    public interface Route {

        void store(Cooldown cooldown);

        /** A replicated route: its entries are born persistent, since a non-persistent one would never propagate. */
        default boolean isReplicated() {
            return false;
        }
    }

    private final String identifier;
    private final CooldownEntry entry;
    private final RepeatPolicy policy;
    private final @Nullable Route route;

    /**
     * Internal: the routes build handles through this. A persistent policy or a replicated route marks the entry
     * persistent without stamping it nor telling the route, so the row only grows once something is actually used.
     */
    public Cooldown(String identifier, CooldownEntry entry, RepeatPolicy policy, @Nullable Route route) {
        this.identifier = Objects.requireNonNull(identifier, "identifier");
        this.entry = Objects.requireNonNull(entry, "entry");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.route = route;
        if (route != null && (policy.isPersistent() || route.isReplicated())) {
            entry.setPersist(true);
        }
    }

    public String getIdentifier() {
        return identifier;
    }

    public RepeatPolicy getPolicy() {
        return policy;
    }

    /**
     * The value this handle reads and writes. Mutating it directly bypasses the mutation clock and the route, so a
     * change made that way is neither stored nor visible to a merge - go through this handle's own mutators instead.
     */
    public CooldownEntry getEntry() {
        return entry;
    }

    /** A handle over the same entry and route under another policy. */
    public Cooldown withPolicy(RepeatPolicy policy) {
        return new Cooldown(identifier, entry, policy, route);
    }

    // ------------------------------------------------------------------
    // The policy vocabulary
    // ------------------------------------------------------------------

    /** Whether a use would be taken now: the gap has passed and the limit has uses left. */
    public boolean canUse() {
        synchronized (entry) {
            long now = now();
            return !isInCooldownAt(now) && usesLeftAt(now) > 0;
        }
    }

    /** The uses left in the current window (forever without one); {@link Integer#MAX_VALUE} when unlimited. */
    public int getUsesLeft() {
        synchronized (entry) {
            return usesLeftAt(now());
        }
    }

    /**
     * How long until {@link #canUse()}: zero when it already can, the gap left, or the time to the window's end when the
     * uses ran out. A limit without a window never frees: {@link ChronoUnit#FOREVER}'s duration.
     */
    public Duration getRemaining() {
        synchronized (entry) {
            long now = now();
            long gap = isInCooldownAt(now) ? entry.getTimeStart() + entry.getTimeDuration() - now : 0L;
            if (usesLeftAt(now) > 0) {
                return Duration.ofMillis(gap);
            }
            if (entry.getWindowEnd() == 0) {
                return ChronoUnit.FOREVER.getDuration();
            }
            return Duration.ofMillis(Math.max(gap, entry.getWindowEnd() - now));
        }
    }

    /** Takes one use: false, and nothing taken, when the policy refuses. */
    public boolean tryUse() {
        return tryUse(1);
    }

    /** Takes {@code uses} uses at once, all or nothing: false, and nothing taken, when not all of them fit. */
    public boolean tryUse(int uses) {
        if (uses < 1) {
            throw new IllegalArgumentException("tryUse(" + uses + ") takes nothing: ask for at least 1 use.");
        }
        synchronized (entry) {
            long now = now();
            if (isInCooldownAt(now) || usesLeftAt(now) < uses) {
                return false;
            }
            countUses(now, uses);
            stamp(now);
            touch();
            return true;
        }
    }

    /** Gives back one use taken by {@link #tryUse()} - the refund of {@link RepeatPolicy#asCost(String)} - and closes the gap it opened. */
    public void giveBack() {
        synchronized (entry) {
            long now = now();
            if (policy.isLimited() && usesAt(now) > 0) {
                entry.setUses(usesAt(now) - 1);
            }
            if (policy.getCooldown() != null) {
                entry.setTimeStart(0);
            }
            touch();
        }
    }

    /** The admin verb: forgets the uses, the window and the gap, as if never used. */
    public void reset() {
        synchronized (entry) {
            entry.setUses(0);
            entry.setWindowStart(0);
            entry.setWindowEnd(0);
            stop();
        }
    }

    /** The last use, empty when never used (or stopped). */
    public Optional<Instant> getLastUseAt() {
        long start = entry.getTimeStart();
        return start == 0 ? Optional.<Instant>empty() : Optional.of(Instant.ofEpochMilli(start));
    }

    /** Adopts a last use from elsewhere (a plugin migrating its own date): the gap counts from {@code lastUseAt}. */
    public Cooldown seed(Instant lastUseAt) {
        synchronized (entry) {
            stamp(lastUseAt.toEpochMilli());
            touch();
        }
        return this;
    }

    /** Why it cannot be used now, or what is left: "wait 4m 12s more", "3 of 3 used until 04:00". */
    public FancyText explain(Locale locale) {
        boolean pt = locale != null && "pt".equals(locale.getLanguage());
        synchronized (entry) {
            long now = now();
            if (isInCooldownAt(now)) {
                String left = "§6" + FCTimeFrame.of(entry.getTimeStart() + entry.getTimeDuration() - now).toConfigString();
                return FancyText.of(pt ? "§cEspere mais " + left + "§c." : "§cWait " + left + "§c more.");
            }
            if (!policy.isLimited()) {
                return FancyText.of(pt ? "§aPronto." : "§aReady.");
            }
            int limit = policy.getLimit();
            int left = usesLeftAt(now);
            if (left > 0) {
                return FancyText.of(pt ? "§a" + left + " de " + limit + " restantes." : "§a" + left + " of " + limit + " left.");
            }
            if (entry.getWindowEnd() == 0) {
                return FancyText.of(pt ? "§c" + limit + " de " + limit + " usados." : "§c" + limit + " of " + limit + " used.");
            }
            String until = untilText(now);
            return FancyText.of(pt ? "§c" + limit + " de " + limit + " usados até " + until + "." : "§c" + limit + " of " + limit + " used until " + until + ".");
        }
    }

    // ------------------------------------------------------------------
    // The bare cooldown vocabulary
    // ------------------------------------------------------------------

    public long getStart() {
        return entry.getTimeStart();
    }

    public long getDuration() {
        return entry.getTimeDuration();
    }

    public Cooldown setDuration(long timeDuration) {
        synchronized (entry) {
            entry.setTimeDuration(timeDuration);
            touch();
        }
        return this;
    }

    public boolean isPersistent() {
        return entry.isPersist();
    }

    public Cooldown setPersist(boolean persist) {
        synchronized (entry) {
            boolean hadStoredRow = entry.isPersist();
            entry.setPersist(persist);
            touch(hadStoredRow);
        }
        return this;
    }

    public long getTimeLeft() {
        return getTimeLeft(entry.getTimeDuration(), TimeUnit.MILLISECONDS);
    }

    public long getTimeLeft(long secondsToWait) {
        return getTimeLeft(secondsToWait, TimeUnit.SECONDS);
    }

    public long getTimeLeft(long value, TimeUnit timeUnit) {
        return timeLeftAt(now(), timeUnit.toMillis(value));
    }

    public FCTimeFrame getFCTimeFrame() {
        return FCTimeFrame.of(this.getTimeLeft());
    }

    public FCTimeFrame getFCTimeFrame(long customTimeInSeconds) {
        return FCTimeFrame.of(this.getTimeLeft(customTimeInSeconds));
    }

    public FCTimeFrame getFCTimeFrame(long customTime, TimeUnit timeUnit) {
        return FCTimeFrame.of(this.getTimeLeft(customTime, timeUnit));
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§cYou need to wait more ${discursive_time}§c to do this!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§cVocê precisa esperar mais ${discursive_time}§c para fazer isto!")
    private static LocaleMessage YOU_NEED_TO_WAIT_MORE_TIME;

    @FCLocale(lang = LocaleType.EN_US, text = "§cYou already used this ${limit} of ${limit} times, until ${until}§c!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§cVocê já usou isto ${limit} de ${limit} vezes, até ${until}§c!")
    private static LocaleMessage YOU_USED_IT_UNTIL;

    @FCLocale(lang = LocaleType.EN_US, text = "§cYou already used this ${limit} of ${limit} times!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§cVocê já usou isto ${limit} de ${limit} vezes!")
    private static LocaleMessage YOU_USED_IT_ALL;

    /** Tells {@code commandSender} why it cannot be used now: the gap left, or the uses that ran out. */
    public Cooldown warnPlayer(FCommandSender commandSender) {
        synchronized (entry) {
            long now = now();
            if (!isInCooldownAt(now) && usesLeftAt(now) == 0) {
                if (entry.getWindowEnd() == 0) {
                    YOU_USED_IT_ALL.addPlaceholder("limit", policy.getLimit()).send(commandSender);
                } else {
                    YOU_USED_IT_UNTIL.addPlaceholder("limit", policy.getLimit()).addPlaceholder("until", "§6" + untilText(now)).send(commandSender);
                }
                return this;
            }
        }
        YOU_NEED_TO_WAIT_MORE_TIME
                .addPlaceholder("discursive_time", getFCTimeFrame().getFormattedDiscursive("§6", "§c"))
                .send(commandSender);
        return this;
    }

    public Cooldown warnPlayer(FCommandSender commandSender, long customTimeInSeconds) {
        YOU_NEED_TO_WAIT_MORE_TIME
                .addPlaceholder("discursive_time", getFCTimeFrame(customTimeInSeconds).getFormattedDiscursive("§6", "§c"))
                .send(commandSender);
        return this;
    }

    public Cooldown warnPlayer(FCommandSender commandSender, long customTime, TimeUnit timeUnit) {
        YOU_NEED_TO_WAIT_MORE_TIME
                .addPlaceholder("discursive_time", getFCTimeFrame(customTime, timeUnit).getFormattedDiscursive("§6", "§c"))
                .send(commandSender);
        return this;
    }

    /** Whether the gap since the last use has not passed yet - not the limit, which {@link #canUse()} adds. */
    public boolean isInCooldown() {
        return isInCooldown(entry.getTimeDuration(), TimeUnit.MILLISECONDS);
    }

    public boolean isInCooldown(long customWaitTimeInSeconds) {
        return isInCooldown(customWaitTimeInSeconds, TimeUnit.SECONDS);
    }

    public boolean isInCooldown(long customWaitTime, TimeUnit timeUnit) {
        long wait = timeUnit.toMillis(customWaitTime);
        return entry.getTimeStart() != 0 && wait > 0 && timeLeftAt(now(), wait) >= 1;
    }

    //Convenience method, since cooldowns are almost always based on seconds rather than millis
    public Cooldown startWith(long timeInSeconds) {
        return startWith(timeInSeconds, TimeUnit.SECONDS);
    }

    /** Stamps one use with that gap: a {@link #tryUse()} whose answer is ignored, for the code that checked first. */
    public Cooldown startWith(long value, TimeUnit timeUnit) {
        synchronized (entry) {
            //the duration goes straight onto the entry so that start() stays the single mutation
            entry.setTimeDuration(timeUnit.toMillis(value));
            start();
        }
        return this;
    }

    /** Stamps one use with the gap already set, counting it against a limit. */
    public Cooldown start() {
        synchronized (entry) {
            long now = now();
            if (usesLeftAt(now) > 0) {
                countUses(now, 1);
            }
            entry.setTimeStart(now);
            touch();
        }
        return this;
    }

    public Cooldown stop() {
        synchronized (entry) {
            boolean hadStoredRow = entry.isPersist();
            entry.setTimeStart(0);
            entry.setPersist(false);
            touch(hadStoredRow);
        }
        return this;
    }

    // ------------------------------------------------------------------
    // Factories
    // ------------------------------------------------------------------

    /** The server's own LOCAL cooldown {@code identifier}, kept in {@code Cooldowns.yml} while persistent. */
    public static Cooldown of(String identifier) {
        return Cooldowns.server(identifier);
    }

    /**
     * The server's NETWORK cooldown {@code identifier}: a handle over its row on the shared backend, born persistent.
     * Synchronous - the collection is warm. Collapses to the local {@link #of(String)} when the network storage is not
     * bootstrapped: one server is then the whole network.
     */
    public static Cooldown network(String identifier) {
        ServerCooldowns store = ServerCooldowns.get();
        return store == null ? of(identifier) : store.resolve(identifier, RepeatPolicy.unlimited());
    }

    /**
     * {@code uuid}'s LOCAL cooldown {@code identifier}, over the player's {@link PlayerCooldownsLocal} row. Async because
     * the row may have to be read on a cache miss; {@code .join()} is a cache hit for an online player. Not persistent
     * by default - {@code .setPersist(true)} makes it outlive a restart.
     */
    public static CompletableFuture<Cooldown> of(UUID uuid, String identifier) {
        return of(uuid, identifier, RepeatPolicy.unlimited());
    }

    /** {@code uuid}'s LOCAL counter {@code identifier} under {@code policy}; persistent when the policy is. */
    public static CompletableFuture<Cooldown> of(UUID uuid, String identifier, RepeatPolicy policy) {
        return PlayerController.getPDSection(uuid, PlayerCooldownsLocal.class)
                .thenApply(section -> section.cooldown(identifier, policy));
    }

    /**
     * {@code uuid}'s NETWORK cooldown {@code identifier}, over the human's account-shared {@link PlayerCooldownsNetwork}
     * row, seen by every server. Born persistent; async like {@link #of(UUID, String)}.
     */
    public static CompletableFuture<Cooldown> network(UUID uuid, String identifier) {
        return network(uuid, identifier, RepeatPolicy.unlimited());
    }

    /** {@code uuid}'s NETWORK counter {@code identifier} under {@code policy}. */
    public static CompletableFuture<Cooldown> network(UUID uuid, String identifier, RepeatPolicy policy) {
        return PlayerController.getAccountSection(uuid, PlayerCooldownsNetwork.class)
                .thenApply(section -> section.cooldown(identifier, policy));
    }

    /** A handle kept in memory only: never stored, never listed - one per command, a parser's throttle. */
    public static Cooldown inMemory(String identifier) {
        return new Cooldown(identifier, new CooldownEntry(), RepeatPolicy.unlimited(), null);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private static long now() {
        return ECClock.now().toEpochMilli();
    }

    private long timeLeftAt(long now, long durationMillis) {
        return entry.getTimeStart() + durationMillis - now;
    }

    private boolean isInCooldownAt(long now) {
        return entry.getTimeStart() != 0 && entry.getTimeDuration() > 0 && timeLeftAt(now, entry.getTimeDuration()) >= 1;
    }

    private long windowStartAt(long now) {
        Instant start = policy.getWindow().currentStart(Instant.ofEpochMilli(now));
        return start == null ? 0L : start.toEpochMilli();
    }

    /** The uses counted in the current window; a window that turned since the last use counts none. */
    private int usesAt(long now) {
        if (!policy.isLimited()) {
            return 0;
        }
        if (policy.getWindow() != null && entry.getWindowStart() != windowStartAt(now)) {
            return 0;
        }
        return entry.getUses();
    }

    private int usesLeftAt(long now) {
        return policy.isLimited() ? Math.max(0, policy.getLimit() - usesAt(now)) : Integer.MAX_VALUE;
    }

    /** Counts {@code uses} against a limit, opening the current window first when the last use was in an older one. */
    private void countUses(long now, int uses) {
        if (!policy.isLimited()) {
            return;
        }
        int used = usesAt(now);
        Window window = policy.getWindow();
        if (window != null && entry.getWindowStart() != windowStartAt(now)) {
            Instant end = window.nextBoundary(Instant.ofEpochMilli(now));
            entry.setWindowStart(windowStartAt(now));
            entry.setWindowEnd(end == null ? 0L : end.toEpochMilli());
        }
        entry.setUses(used + uses);
    }

    private void stamp(long at) {
        entry.setTimeStart(at);
        Duration gap = policy.getCooldown();
        if (gap != null) {
            entry.setTimeDuration(gap.toMillis());
        }
    }

    private String untilText(long now) {
        ZonedDateTime end = Instant.ofEpochMilli(entry.getWindowEnd()).atZone(policy.getWindow() != null ? policy.getWindow().getZone() : Window.getServerZone());
        boolean today = end.toLocalDate().equals(LocalDate.from(Instant.ofEpochMilli(now).atZone(end.getZone())));
        return end.format(DateTimeFormatter.ofPattern(today ? "HH:mm" : "yyyy-MM-dd HH:mm"));
    }

    /** Ends a mutation that cannot have changed the persist flag. */
    private void touch() {
        touch(false);
    }

    /**
     * Ends every mutation: stamps the mutation clock, then tells the route while there is a stored row to keep in
     * agreement. A cooldown that neither is nor was persistent has no row anywhere, so the route is not told - that is
     * what keeps a memory-only cooldown from ever reaching a backend.
     *
     * @param hadStoredRow the persist flag as it stood BEFORE the mutation - a cooldown that has just lost it still has
     *                     a row to drop
     */
    private void touch(boolean hadStoredRow) {
        entry.setUpdatedAt(now());
        if (route != null && (entry.isPersist() || hadStoredRow)) {
            route.store(this);
        }
    }

    @Override
    public String toString() {
        return "Cooldown{" + identifier + ", " + entry + ", " + policy + "}";
    }
}
