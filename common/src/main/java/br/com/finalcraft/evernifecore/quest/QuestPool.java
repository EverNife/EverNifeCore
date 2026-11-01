package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.logic.GateResult;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.time.ECClock;
import br.com.finalcraft.evernifecore.time.Window;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToIntFunction;

/**
 * A daily (weekly, ...) draw of quests per owner: {@code draw} of the pool's quests, picked by weight, renewed when the
 * window turns, keeping the last {@code noRepeatLast} drawn out of the next draw. An owner may pay to reroll what it
 * has not started or finished. Opened by {@link QuestEngine#pool}; the draws live where the owner's quests live.
 *
 * <pre>{@code
 * QuestPool daily = engine.pool("daily-mission", p -> p
 *         .fromTag("daily")
 *         .draw(3)
 *         .window(Window.daily(LocalTime.of(4, 0), zone))
 *         .noRepeatLast(5)
 *         .reroll(Gate.parse(plugin, rerollGateNode, zone), Duration.ofHours(1)));
 * List<QuestId> today = daily.current(ProgressOwner.player(uuid));
 * }</pre>
 *
 * Any thread: an owner's draw changes under that owner's lock in the engine.
 */
public final class QuestPool {

    private final QuestEngine engine;
    private final String name;
    private final @Nullable String tag;
    private final Set<QuestId> ids;
    private final int draw;
    private final @Nullable Window window;
    private final ToIntFunction<QuestDefinition> weight;
    private final int noRepeatLast;
    private final Gate rerollGate;
    private final @Nullable Duration rerollCooldown;

    private QuestPool(QuestEngine engine, String name, Builder builder) {
        this.engine = engine;
        this.name = name;
        this.tag = builder.tag;
        this.ids = Collections.unmodifiableSet(new LinkedHashSet<>(builder.ids));
        this.draw = builder.draw;
        this.window = builder.window;
        this.weight = builder.weight;
        this.noRepeatLast = builder.noRepeatLast;
        this.rerollGate = builder.rerollGate;
        this.rerollCooldown = builder.rerollCooldown;
    }

    static QuestPool build(QuestEngine engine, String name, Builder builder) {
        if (builder.tag == null && builder.ids.isEmpty()) {
            throw new IllegalArgumentException("Pool '" + name + "' draws from nothing: give it fromTag(\"daily\") or from(questIds) -"
                    + " in YAML, from-tag: daily or from: [quest-a, quest-b].");
        }
        return new QuestPool(engine, name, builder);
    }

    public String getName() {
        return name;
    }

    public int getDraw() {
        return draw;
    }

    public @Nullable Window getWindow() {
        return window;
    }

    public int getNoRepeatLast() {
        return noRepeatLast;
    }

    /** The quests the pool draws from now: the registered ones with its tag, or named in it. */
    public List<QuestDefinition> getCandidates() {
        List<QuestDefinition> candidates = new ArrayList<>();
        for (QuestDefinition definition : engine.getAll()) {
            if (ids.contains(definition.getId()) || (tag != null && definition.getMeta().getTags().contains(tag))) {
                candidates.add(definition);
            }
        }
        return candidates;
    }

    /**
     * The owner's quests of the current window, drawing them first when the window turned (or there is no draw yet).
     * Empty for a player whose quests are not loaded - nothing is drawn offline.
     */
    public List<QuestId> current(ProgressOwner owner) {
        synchronized (engine.lane(owner)) {
            Map<String, PoolDraw> draws = engine.poolDraws(owner);
            if (draws == null) {
                return Collections.emptyList();
            }
            PoolDraw current = draws.get(name);
            String key = windowKey(ECClock.now());
            if (current == null || !key.equals(current.getWindowKey())) {
                PoolDraw next = current == null ? new PoolDraw() : current;
                next.setWindowKey(key);
                List<QuestId> picked = pick(draw, new LinkedHashSet<>(next.getHistory()), Collections.<String>emptySet());
                next.setDrawn(toStrings(picked));
                remember(next, picked);
                draws.put(name, next);
                engine.markPoolDirty(owner, name);
                current = next;
            }
            return toIds(current.getDrawn());
        }
    }

    /** A snapshot of the owner's draw; null when it has none or is not loaded. Draws nothing. */
    public @Nullable PoolDraw getDraw(ProgressOwner owner) {
        synchronized (engine.lane(owner)) {
            Map<String, PoolDraw> draws = engine.poolDraws(owner);
            PoolDraw current = draws == null ? null : draws.get(name);
            return current == null ? null : current.copy();
        }
    }

    /** {@link #reroll(ProgressOwner, FPlayer)} of the player's own draw, paid by them. */
    public RerollResult reroll(FPlayer player) {
        return reroll(ProgressOwner.player(player.getUniqueId()), player);
    }

    /**
     * Redraws what the owner has neither active nor finished in this window, for the reroll gate paid by {@code payer}:
     * the gate is checked (every failure listed), the reroll cooldown respected, the costs taken, and the new quests
     * never repeat the ones just dropped or the history.
     */
    public RerollResult reroll(ProgressOwner owner, @Nullable FPlayer payer) {
        synchronized (engine.lane(owner)) {
            current(owner);
            Map<String, PoolDraw> draws = engine.poolDraws(owner);
            PoolDraw current = draws == null ? null : draws.get(name);
            if (current == null) {
                return RerollResult.refused("The quests of " + owner + " are not loaded, so pool '" + name + "' cannot reroll now.");
            }
            Instant now = ECClock.now();
            if (rerollCooldown != null && current.getRerolledAt() > 0 && now.toEpochMilli() - current.getRerolledAt() < rerollCooldown.toMillis()) {
                long left = rerollCooldown.toMillis() - (now.toEpochMilli() - current.getRerolledAt());
                return RerollResult.refused("Pool '" + name + "' rerolls again in " + Duration.ofMillis(left).getSeconds() + "s.");
            }
            ExecutionContext ctx = ExecutionContext.builder(Caller.of(engine.getOwner(), "pool:" + name + "/reroll")).owner(owner).player(payer).build();
            GateResult checked = rerollGate.check(ctx);
            if (!checked.isPassed()) {
                return RerollResult.denied(checked);
            }
            GateResult paid = rerollGate.pay(ctx);
            if (!paid.isPassed()) {
                return RerollResult.denied(paid);
            }
            List<QuestId> kept = new ArrayList<>();
            Set<String> dropped = new LinkedHashSet<>();
            Instant windowStart = window == null ? null : window.currentStart(now);
            for (QuestId id : toIds(current.getDrawn())) {
                if (engine.isTakenInWindow(owner, id, windowStart)) {
                    kept.add(id);
                } else {
                    dropped.add(id.toString());
                }
            }
            Set<String> excluded = new LinkedHashSet<>(current.getHistory());
            excluded.addAll(dropped);
            List<QuestId> picked = pick(draw - kept.size(), excluded, new LinkedHashSet<>(toStrings(kept)));
            List<QuestId> drawn = new ArrayList<>(kept);
            drawn.addAll(picked);
            current.setDrawn(toStrings(drawn));
            current.setRerolledAt(now.toEpochMilli());
            remember(current, picked);
            engine.markPoolDirty(owner, name);
            return RerollResult.done(drawn);
        }
    }

    /** The sweep: a draw whose window turned is drawn again. Under the owner's lock. */
    void refresh(ProgressOwner owner) {
        Map<String, PoolDraw> draws = engine.poolDraws(owner);
        if (draws != null && draws.containsKey(name)) {
            current(owner);
        }
    }

    private String windowKey(Instant now) {
        Instant start = window == null ? null : window.currentStart(now);
        return start == null ? "always" : String.valueOf(start.toEpochMilli());
    }

    private void remember(PoolDraw draw, List<QuestId> picked) {
        List<String> history = new ArrayList<>(draw.getHistory());
        history.addAll(toStrings(picked));
        while (history.size() > noRepeatLast) {
            history.remove(0);
        }
        draw.setHistory(history);
    }

    /**
     * Up to {@code count} quests by weight, never one of {@code taken}; the ones in {@code excluded} only when nothing
     * else is left, oldest exclusion first.
     */
    private List<QuestId> pick(int count, Set<String> excluded, Set<String> taken) {
        List<QuestDefinition> fresh = new ArrayList<>();
        List<QuestDefinition> fallback = new ArrayList<>();
        for (QuestDefinition candidate : getCandidates()) {
            String id = candidate.getId().toString();
            if (taken.contains(id)) {
                continue;
            }
            (excluded.contains(id) ? fallback : fresh).add(candidate);
        }
        List<String> order = new ArrayList<>(excluded);
        fallback.sort((a, b) -> Integer.compare(order.indexOf(a.getId().toString()), order.indexOf(b.getId().toString())));
        List<QuestId> picked = new ArrayList<>();
        while (picked.size() < count && !fresh.isEmpty()) {
            picked.add(fresh.remove(weightedIndex(fresh)).getId());
        }
        for (int i = 0; picked.size() < count && i < fallback.size(); i++) {
            picked.add(fallback.get(i).getId());
        }
        return picked;
    }

    private int weightedIndex(List<QuestDefinition> from) {
        int total = 0;
        for (QuestDefinition definition : from) {
            total += Math.max(1, weight.applyAsInt(definition));
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (int i = 0; i < from.size(); i++) {
            roll -= Math.max(1, weight.applyAsInt(from.get(i)));
            if (roll < 0) {
                return i;
            }
        }
        return from.size() - 1;
    }

    private List<QuestId> toIds(List<String> written) {
        List<QuestId> ids = new ArrayList<>();
        for (String id : written) {
            ids.add(QuestId.parse(id, engine.getOwner().getMetaInfo().getName()));
        }
        return ids;
    }

    private static List<String> toStrings(List<QuestId> ids) {
        List<String> written = new ArrayList<>();
        for (QuestId id : ids) {
            written.add(id.toString());
        }
        return written;
    }

    @Override
    public String toString() {
        return "QuestPool(" + name + ", draw " + draw + (window == null ? "" : ", " + window) + ")";
    }

    /** How a pool draws; see {@link QuestEngine#pool}. */
    public static final class Builder {

        private @Nullable String tag;
        private final Set<QuestId> ids = new LinkedHashSet<>();
        private int draw = 1;
        private @Nullable Window window;
        private ToIntFunction<QuestDefinition> weight = Builder::metaWeight;
        private int noRepeatLast;
        private Gate rerollGate = Gate.open();
        private @Nullable Duration rerollCooldown;

        Builder() {
        }

        /** Draws from the registered quests whose {@code meta.tags} hold {@code tag}. */
        public Builder fromTag(String tag) {
            this.tag = Objects.requireNonNull(tag, "tag");
            return this;
        }

        /** Draws (also) from these quests. */
        public Builder from(QuestId... quests) {
            Collections.addAll(ids, quests);
            return this;
        }

        /** How many per owner per window; at least 1. */
        public Builder draw(int count) {
            if (count < 1) {
                throw new IllegalArgumentException("A pool draws at least 1 quest, not " + count + ".");
            }
            this.draw = count;
            return this;
        }

        /** When the draw renews; none = once, never renewed. */
        public Builder window(@Nullable Window window) {
            this.window = window;
            return this;
        }

        /** Each quest's weight; by default {@code meta.extra.weight}, else 1. */
        public Builder weight(ToIntFunction<QuestDefinition> weight) {
            this.weight = Objects.requireNonNull(weight, "weight");
            return this;
        }

        /** How many of the latest drawn quests stay out of the next draws. */
        public Builder noRepeatLast(int count) {
            this.noRepeatLast = Math.max(0, count);
            return this;
        }

        /** What a reroll requires and takes, and the least time between two rerolls (null for none). */
        public Builder reroll(Gate gate, @Nullable Duration cooldown) {
            this.rerollGate = Objects.requireNonNull(gate, "gate");
            this.rerollCooldown = cooldown;
            return this;
        }

        private static int metaWeight(QuestDefinition definition) {
            return definition.getMeta().getExtra("weight", 1);
        }
    }
}
