package br.com.finalcraft.evernifecore.quest.placeholder;

import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.placeholder.manipulation.ManipulationContext;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.quest.Objective;
import br.com.finalcraft.evernifecore.quest.PoolDraw;
import br.com.finalcraft.evernifecore.quest.QuestDefinition;
import br.com.finalcraft.evernifecore.quest.QuestEngine;
import br.com.finalcraft.evernifecore.quest.QuestId;
import br.com.finalcraft.evernifecore.quest.QuestPool;
import br.com.finalcraft.evernifecore.quest.QuestState;
import br.com.finalcraft.evernifecore.quest.QuestStatus;
import br.com.finalcraft.evernifecore.region.placeholder.RegionPlaceholders;
import br.com.finalcraft.evernifecore.time.ECClock;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The quest placeholders, served on any {@link RegexReplacer} a plugin hands over:
 * <pre>{@code
 * QuestPlaceholders.register(PAPIIntegration.createPlaceholderIntegration(pluginData, "finalquests", IPlayerData.class),
 *         QuestPlaceholderScope.ownedBy(pluginData));
 * }</pre>
 * About one quest, {@code quest_[quest]_<op>}: {@code status}, {@code stage}, {@code percent} (of the current stage),
 * {@code times}, {@code cooldown} and {@code time_left} (seconds), {@code name}, {@code description},
 * {@code progress_[objective]}, {@code goal_[objective]}, {@code param_[key]}. The tracked quest, {@code tracked_<op>}
 * with the same ops plus {@code id}. Counts: {@code quests_active_count}, {@code quests_completed_count}. A pool,
 * {@code pool_[pool]_<n>} (the n-th quest drawn), {@code pool_[pool]_count}, {@code pool_[pool]_reroll_in}.
 *
 * <p>The core serves them under {@value #CORE_PREFIX} with {@link QuestPlaceholderScope#everyEngine()}, for debugging.
 * What does not exist answers {@code ""}. They only read snapshots - a pool shows its draw, never draws - so any thread may
 * resolve them.</p>
 */
public final class QuestPlaceholders {

    /** The prefix the core serves every quest under. */
    public static final String CORE_PREFIX = RegionPlaceholders.CORE_PREFIX;

    private QuestPlaceholders() {
    }

    /** Adds the quest placeholders to the core's replacer of {@value #CORE_PREFIX}, over every engine. */
    public static <P extends IPlayerData> void registerCore(RegexReplacer<P> coreReplacer) {
        register(coreReplacer, QuestPlaceholderScope.everyEngine());
    }

    /** Adds every quest placeholder to {@code replacer}, seeing the engines of {@code scope}. */
    public static <P extends IPlayerData> void register(RegexReplacer<P> replacer, QuestPlaceholderScope scope) {
        replacer.addManipulator("quest_[quest]_{op}", (data, ctx) -> {
            UUID viewer = viewerOf(data);
            QuestId id = scope.find(ctx.getArgs("[quest]").getValue());
            return viewer == null || id == null ? "" : answer(scope, id, viewer, ctx.getString("{op}"));
        });
        replacer.addManipulator("tracked_{op}", (data, ctx) -> {
            UUID viewer = viewerOf(data);
            if (viewer == null) {
                return "";
            }
            for (QuestEngine engine : scope.engines()) {
                QuestId tracked = engine.getTracked(viewer);
                if (tracked != null) {
                    String op = ctx.getString("{op}");
                    return op.equalsIgnoreCase("id") ? scope.idOf(tracked) : answer(scope, tracked, viewer, op);
                }
            }
            return "";
        });
        replacer.addParser("quests_active_count", "How many quests you have active", data -> count(scope, data, QuestStatus.ACTIVE));
        replacer.addParser("quests_completed_count", "How many quests you completed at least once", data -> count(scope, data, null));
        replacer.addManipulator("pool_[pool]_{op}", (data, ctx) -> {
            UUID viewer = viewerOf(data);
            return viewer == null ? "" : pool(scope, ctx, viewer);
        });
    }

    private static @Nullable UUID viewerOf(@Nullable IPlayerData data) {
        return data == null ? null : data.getUniqueId();
    }

    private static Object answer(QuestPlaceholderScope scope, QuestId id, UUID viewer, String op) {
        QuestEngine engine = scope.engineOf(id);
        QuestDefinition definition = engine == null ? null : engine.get(id);
        ProgressOwner owner = definition == null ? null : engine.getOwnerFor(viewer, id);
        if (owner == null) {
            return "";
        }
        Optional<QuestState> found = engine.getState(owner, id);
        QuestState state = found.orElse(null);
        String lower = op.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "status":
                return engine.getStatus(owner, id).name();
            case "stage":
                return state == null || state.getStageId() == null ? "" : state.getStageId();
            case "percent":
                return state == null ? 0 : (int) Math.floor(definition.getStageProgress(state) * 100);
            case "times":
                return state == null ? 0 : state.getTimes();
            case "cooldown": {
                Duration cooldown = definition.getRepeat().getCooldown();
                if (state == null || cooldown == null || engine.getStatus(owner, id) != QuestStatus.COOLDOWN) {
                    return 0;
                }
                return Math.max(0, (cooldown.toMillis() - (ECClock.now().toEpochMilli() - state.getLastCompletedAt())) / 1000);
            }
            case "time_left": {
                Duration limit = definition.getTimeLimit();
                if (state == null || limit == null || state.getStatus() != QuestStatus.ACTIVE) {
                    return "";
                }
                return Math.max(0, (limit.toMillis() - (ECClock.now().toEpochMilli() - state.getStartedAt())) / 1000);
            }
            case "name":
                return definition.getName();
            case "description":
                return definition.getDescription();
            default:
                break;
        }
        String key = bracketed(op, "progress_");
        if (key != null) {
            return definition.getObjective(key) == null ? "" : state == null ? 0 : state.getCount(key);
        }
        key = bracketed(op, "goal_");
        if (key != null) {
            Objective objective = definition.getObjective(key);
            return objective == null ? "" : objective.getGoal();
        }
        key = bracketed(op, "param_");
        if (key != null) {
            String value = state == null ? null : state.getParams().get(key);
            return value == null ? "" : value;
        }
        return "";
    }

    /** {@code [x]} after {@code prefix} in {@code op}; null when {@code op} is not that shape. */
    private static @Nullable String bracketed(String op, String prefix) {
        if (op.length() > prefix.length() + 2 && op.regionMatches(true, 0, prefix, 0, prefix.length())
                && op.charAt(prefix.length()) == '[' && op.endsWith("]")) {
            return op.substring(prefix.length() + 1, op.length() - 1);
        }
        return null;
    }

    /** ACTIVE: open now; null: completed at least once. */
    private static Object count(QuestPlaceholderScope scope, @Nullable IPlayerData data, @Nullable QuestStatus status) {
        UUID viewer = viewerOf(data);
        if (viewer == null) {
            return "";
        }
        int count = 0;
        for (QuestEngine engine : scope.engines()) {
            for (QuestState state : engine.getStates(ProgressOwner.player(viewer))) {
                if (status != null ? state.getStatus() == status : state.getTimes() > 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private static Object pool(QuestPlaceholderScope scope, ManipulationContext ctx, UUID viewer) {
        String name = ctx.getArgs("[pool]").getValue();
        for (QuestEngine engine : scope.engines()) {
            QuestPool pool = name == null ? null : engine.getPool(name);
            if (pool == null) {
                continue;
            }
            PoolDraw draw = pool.getDraw(ProgressOwner.player(viewer));
            String op = ctx.getString("{op}").toLowerCase(Locale.ROOT);
            if (op.equals("count")) {
                return draw == null ? 0 : draw.getDrawn().size();
            }
            if (op.equals("reroll_in")) {
                Duration cooldown = pool.getRerollCooldown();
                if (draw == null || cooldown == null || draw.getRerolledAt() <= 0) {
                    return 0;
                }
                return Math.max(0, (cooldown.toMillis() - (ECClock.now().toEpochMilli() - draw.getRerolledAt())) / 1000);
            }
            try {
                int n = Integer.parseInt(op);
                if (draw == null || n < 1 || n > draw.getDrawn().size()) {
                    return "";
                }
                return scope.idOf(QuestId.parse(draw.getDrawn().get(n - 1), engine.getOwner().getMetaInfo().getName()));
            } catch (IllegalArgumentException notAnIndex) {
                return "";
            }
        }
        return "";
    }
}
