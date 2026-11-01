package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.EffectList;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.RepeatPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A quest as its author wrote it: a graph of {@link Stage}s whose objectives count actions or watch a state, with the
 * gate to start it, what fails it, how often it repeats and what each transition applies. Built with {@link #builder}
 * or read by {@link QuestDefinitions}; the build checks the graph and fails with a message that names the fix.
 * Immutable and shared by every instance - per-instance values are {@link QuestParams}.
 */
public final class QuestDefinition {

    private final QuestId id;
    private final int version;
    private final DefinitionPolicy policy;
    private final String name;
    private final String description;
    private final QuestMeta meta;
    private final String ownerKind;
    private final @Nullable String ownerProvider;
    private final Gate gate;
    private final Set<QuestId> requires;
    private final List<Stage> stages;
    private final List<Condition> failWhen;
    private final RepeatPolicy repeat;
    private final @Nullable Duration timeLimit;
    private final @Nullable Instant expiresAt;
    private final boolean abandonable;
    private final Map<String, Param> params;
    private final EffectList onStart;
    private final EffectList onComplete;
    private final EffectList onFail;
    private final EffectList onAbandon;
    private final Reward reward;
    private final @Nullable Instant windowStart;
    private final @Nullable Instant windowEnd;
    private final @Nullable JsonNode hytaleNative;

    private QuestDefinition(Builder builder) {
        this.id = builder.id;
        this.version = builder.version;
        this.policy = builder.policy;
        this.name = builder.name;
        this.description = builder.description;
        this.meta = builder.meta;
        this.ownerKind = builder.ownerKind;
        this.ownerProvider = builder.ownerProvider;
        this.gate = builder.gate;
        this.requires = Collections.unmodifiableSet(new LinkedHashSet<>(builder.requires));
        this.stages = Collections.unmodifiableList(new ArrayList<>(builder.stages));
        this.failWhen = Collections.unmodifiableList(new ArrayList<>(builder.failWhen));
        this.repeat = builder.repeat;
        this.timeLimit = builder.timeLimit;
        this.expiresAt = builder.expiresAt;
        this.abandonable = builder.abandonable;
        this.params = Collections.unmodifiableMap(new LinkedHashMap<>(builder.params));
        this.onStart = builder.onStart;
        this.onComplete = builder.onComplete;
        this.onFail = builder.onFail;
        this.onAbandon = builder.onAbandon;
        this.reward = builder.reward;
        this.windowStart = builder.windowStart;
        this.windowEnd = builder.windowEnd;
        this.hytaleNative = builder.hytaleNative;
    }

    public static Builder builder(QuestId id) {
        return new Builder(id);
    }

    /** {@code owner:name}; the owner is the engine's plugin. */
    public QuestId getId() {
        return id;
    }

    /** Raised by the author on a change; a higher version applies {@link #getPolicy()} to the open instances. */
    public int getVersion() {
        return version;
    }

    public DefinitionPolicy getPolicy() {
        return policy;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public QuestMeta getMeta() {
        return meta;
    }

    /** The {@link ProgressOwner} kind that owns an instance: {@code player} (the default), {@code global}, {@code group}... */
    public String getOwnerKind() {
        return ownerKind;
    }

    /** The provider that answers the owner, e.g. {@code finalparty:party} for a group; null elsewhere. */
    public @Nullable String getOwnerProvider() {
        return ownerProvider;
    }

    /** What starting it requires and takes. */
    public Gate getGate() {
        return gate;
    }

    /** Quests the owner must have completed before this one is available. */
    public Set<QuestId> getRequires() {
        return requires;
    }

    /** The first one is the entry. */
    public List<Stage> getStages() {
        return stages;
    }

    public @Nullable Stage getStage(String stageId) {
        for (Stage stage : stages) {
            if (stage.getId().equals(stageId)) {
                return stage;
            }
        }
        return null;
    }

    /** Checked while active: any of them that passes fails the quest ({@link FailCause#CONDITION}). */
    public List<Condition> getFailWhen() {
        return failWhen;
    }

    public RepeatPolicy getRepeat() {
        return repeat;
    }

    /** Active longer than this fails it ({@link FailCause#TIME_LIMIT}); null for no limit. */
    public @Nullable Duration getTimeLimit() {
        return timeLimit;
    }

    /** Available or offered past this expires it; null for never. */
    public @Nullable Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isAbandonable() {
        return abandonable;
    }

    /** The {@code hytale-native} map as written, read by the Hytale platform's objectives bridge; null when absent. */
    public @Nullable JsonNode getHytaleNative() {
        return hytaleNative;
    }

    /** The declared params; their values are frozen per instance in {@link QuestState#getParams()}. */
    public Map<String, Param> getParams() {
        return params;
    }

    public EffectList getOnStart() {
        return onStart;
    }

    public EffectList getOnComplete() {
        return onComplete;
    }

    public EffectList getOnFail() {
        return onFail;
    }

    public EffectList getOnAbandon() {
        return onAbandon;
    }

    /** What completing it pays, rule by rule, through {@code Rewards.pay}; {@link Reward#NONE} for nothing. */
    public Reward getReward() {
        return reward;
    }

    /** From when it can start; null for always. */
    public @Nullable Instant getWindowStart() {
        return windowStart;
    }

    /**
     * When its window closes: it no longer starts, and an instance still active then pays its {@code on-window-end}
     * rules and expires. Null for never.
     */
    public @Nullable Instant getWindowEnd() {
        return windowEnd;
    }

    /** Every objective of every stage, in order. */
    public List<Objective> getObjectives() {
        List<Objective> all = new ArrayList<>();
        for (Stage stage : stages) {
            all.addAll(stage.getObjectives().getObjectives());
        }
        return all;
    }

    /** The objective {@code objectiveId} of any stage; null for none. */
    public @Nullable Objective getObjective(String objectiveId) {
        for (Objective objective : getObjectives()) {
            if (objective.getId().equals(objectiveId)) {
                return objective;
            }
        }
        return null;
    }

    /**
     * How far {@code state} is through its current stage, 0 to 1: the counted share of the goals of its counting
     * objectives (a done objective counts whole). 1 once completed, 0 without a current stage.
     */
    public double getStageProgress(QuestState state) {
        if (state.getStatus() == QuestStatus.COMPLETED) {
            return 1;
        }
        Stage stage = state.getStageId() == null ? null : getStage(state.getStageId());
        if (stage == null) {
            return 0;
        }
        double goals = 0;
        double counted = 0;
        for (Objective objective : stage.getObjectives().getObjectives()) {
            if (objective.isHidden() || objective.getMode() != ProgressMode.COUNT) {
                continue;
            }
            ObjectiveState progress = state.getObjectives().get(objective.getId());
            goals += objective.getGoal();
            counted += progress != null && progress.isDone() ? objective.getGoal() : Math.min(objective.getGoal(), state.getCount(objective.getId()));
        }
        return goals == 0 ? 0 : counted / goals;
    }

    @Override
    public String toString() {
        return id + " v" + version;
    }

    /** A declared param: its values and whether a start without one picks at random or the first. */
    public static final class Param {

        private final List<String> values;
        private final boolean random;

        public Param(List<String> values, boolean random) {
            if (values.isEmpty()) {
                throw new IllegalArgumentException("A quest param needs at least one value: values: [fire, water].");
            }
            this.values = Collections.unmodifiableList(new ArrayList<>(values));
            this.random = random;
        }

        public List<String> getValues() {
            return values;
        }

        public boolean isRandom() {
            return random;
        }
    }

    public static final class Builder {

        private static final EffectList NONE = Effects.of(Collections.emptyList());

        private final QuestId id;
        private int version = 1;
        private DefinitionPolicy policy = DefinitionPolicy.KEEP_BY_ID;
        private String name;
        private String description = "";
        private QuestMeta meta = QuestMeta.NONE;
        private String ownerKind = "player";
        private @Nullable String ownerProvider;
        private Gate gate = Gate.open();
        private final Set<QuestId> requires = new LinkedHashSet<>();
        private final List<Stage> stages = new ArrayList<>();
        private final List<Condition> failWhen = new ArrayList<>();
        private RepeatPolicy repeat = RepeatPolicy.once();
        private @Nullable Duration timeLimit;
        private @Nullable Instant expiresAt;
        private boolean abandonable = true;
        private final Map<String, Param> params = new LinkedHashMap<>();
        private EffectList onStart = NONE;
        private EffectList onComplete = NONE;
        private EffectList onFail = NONE;
        private EffectList onAbandon = NONE;
        private Reward reward = Reward.NONE;
        private @Nullable Instant windowStart;
        private @Nullable Instant windowEnd;
        private @Nullable JsonNode hytaleNative;

        private Builder(QuestId id) {
            this.id = Objects.requireNonNull(id, "id");
            this.name = id.getName();
        }

        public Builder version(int version) {
            this.version = version;
            return this;
        }

        public Builder policy(DefinitionPolicy policy) {
            this.policy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public Builder name(String name) {
            this.name = Objects.requireNonNull(name, "name");
            return this;
        }

        public Builder description(String description) {
            this.description = Objects.requireNonNull(description, "description");
            return this;
        }

        public Builder meta(QuestMeta meta) {
            this.meta = Objects.requireNonNull(meta, "meta");
            return this;
        }

        public Builder owner(String kind, @Nullable String provider) {
            this.ownerKind = Objects.requireNonNull(kind, "kind");
            this.ownerProvider = provider;
            return this;
        }

        public Builder gate(Gate gate) {
            this.gate = Objects.requireNonNull(gate, "gate");
            return this;
        }

        public Builder requires(QuestId... quests) {
            Collections.addAll(requires, quests);
            return this;
        }

        public Builder stage(Stage stage) {
            stages.add(Objects.requireNonNull(stage, "stage"));
            return this;
        }

        public Builder failWhen(Condition condition) {
            failWhen.add(condition);
            return this;
        }

        public Builder repeat(RepeatPolicy repeat) {
            this.repeat = Objects.requireNonNull(repeat, "repeat");
            return this;
        }

        public Builder timeLimit(@Nullable Duration timeLimit) {
            this.timeLimit = timeLimit;
            return this;
        }

        public Builder expiresAt(@Nullable Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder abandonable(boolean abandonable) {
            this.abandonable = abandonable;
            return this;
        }

        public Builder hytaleNative(@Nullable JsonNode hytaleNative) {
            this.hytaleNative = hytaleNative;
            return this;
        }

        public Builder param(String name, Param param) {
            params.put(name, param);
            return this;
        }

        public Builder onStart(EffectList effects) {
            this.onStart = effects;
            return this;
        }

        public Builder onComplete(EffectList effects) {
            this.onComplete = effects;
            return this;
        }

        public Builder onFail(EffectList effects) {
            this.onFail = effects;
            return this;
        }

        public Builder onAbandon(EffectList effects) {
            this.onAbandon = effects;
            return this;
        }

        public Builder reward(Reward reward) {
            this.reward = Objects.requireNonNull(reward, "reward");
            return this;
        }

        /** The window it runs in; either end may be null (open). */
        public Builder window(@Nullable Instant start, @Nullable Instant end) {
            if (start != null && end != null && !end.isAfter(start)) {
                throw new IllegalArgumentException("Quest '" + id + "': its window ends (" + end + ") before it starts (" + start + ")."
                        + " Write window: {from: ..., to: ...} with 'to' after 'from'.");
            }
            this.windowStart = start;
            this.windowEnd = end;
            return this;
        }

        /**
         * @throws IllegalArgumentException when the quest has no stage, repeats a stage or objective id, points a
         *                                  {@code then} at a stage it lacks, or has no path from its first stage to COMPLETE
         */
        public QuestDefinition build() {
            if (stages.isEmpty()) {
                throw new IllegalArgumentException("Quest '" + id + "' has no stage: a quest needs at least one stage with one objective.");
            }
            List<String> stageIds = new ArrayList<>();
            Set<String> objectiveIds = new HashSet<>();
            for (Stage stage : stages) {
                if (stageIds.contains(stage.getId())) {
                    throw new IllegalArgumentException("Quest '" + id + "' has two stages with the id '" + stage.getId() + "': stage ids are"
                            + " stable keys of the progress, so each one is unique.");
                }
                stageIds.add(stage.getId());
                for (Objective objective : stage.getObjectives().getObjectives()) {
                    if (!objectiveIds.add(objective.getId())) {
                        throw new IllegalArgumentException("Quest '" + id + "' has two objectives with the id '" + objective.getId() + "': objective"
                                + " ids are stable keys of the progress, so each one is unique in the quest.");
                    }
                }
            }
            for (Stage stage : stages) {
                requireTarget(stage, "then", stage.getThen(), stageIds);
                for (Objective objective : stage.getObjectives().getObjectives()) {
                    requireTarget(stage, "objective '" + objective.getId() + "' then", objective.getThen(), stageIds);
                }
            }
            if (!reachesComplete()) {
                throw new IllegalArgumentException("Quest '" + id + "' never completes: no path from its first stage '" + stages.get(0).getId()
                        + "' reaches COMPLETE (stages " + stageIds + "). Point a then at COMPLETE, or let the last stage end the quest.");
            }
            return new QuestDefinition(this);
        }

        private void requireTarget(Stage stage, String what, @Nullable Next next, List<String> stageIds) {
            if (next != null && next.getKind() == Next.Kind.GOTO && !stageIds.contains(next.getStageId())) {
                throw new IllegalArgumentException("Quest '" + id + "', stage '" + stage.getId() + "': " + what + " names the stage '"
                        + next.getStageId() + "', which this quest does not have.\n  Stages: " + stageIds
                        + ". Point then at one of them, or at COMPLETE / FAIL.");
            }
        }

        private boolean reachesComplete() {
            Set<Integer> seen = new HashSet<>();
            Deque<Integer> todo = new ArrayDeque<>();
            todo.add(0);
            while (!todo.isEmpty()) {
                int index = todo.poll();
                if (!seen.add(index)) {
                    continue;
                }
                Stage stage = stages.get(index);
                List<Next> exits = new ArrayList<>();
                exits.add(stage.getThen());
                for (Objective objective : stage.getObjectives().getObjectives()) {
                    if (objective.getThen() != null) {
                        exits.add(objective.getThen());
                    }
                }
                for (Next exit : exits) {
                    switch (exit.getKind()) {
                        case COMPLETE:
                            return true;
                        case NEXT_IN_LIST:
                            if (index + 1 >= stages.size()) {
                                return true;
                            }
                            todo.add(index + 1);
                            break;
                        case GOTO:
                            todo.add(indexOf(exit.getStageId()));
                            break;
                        default:
                            break;
                    }
                }
            }
            return false;
        }

        private int indexOf(String stageId) {
            for (int i = 0; i < stages.size(); i++) {
                if (stages.get(i).getId().equals(stageId)) {
                    return i;
                }
            }
            return -1;
        }
    }
}
