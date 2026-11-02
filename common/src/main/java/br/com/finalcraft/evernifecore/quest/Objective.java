package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.Cost;
import br.com.finalcraft.evernifecore.logic.EffectList;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.types.PendingDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One thing a stage asks for. Its filter is kept as written: a quest with params reads it again per parameter value
 * ({@code {type}} substituted first), so one definition serves every instance. A type nobody registered yet leaves the
 * objective pending - it does not count until the type arrives. Immutable.
 */
public final class Objective {

    private final String id;
    private final @Nullable JsonNode rawFilter;
    private final @Nullable ECPluginData caller;
    private final PendingDefinition<ObjectiveFilter> filter;
    private final long goal;
    private final ProgressMode mode;
    private final List<Condition> conditions;
    private final List<Cost> costs;
    private final @Nullable Next then;
    private final EffectList onMatch;
    private final boolean hidden;
    private final String description;
    private final Set<String> platforms;
    private final @Nullable ProvenanceFilter provenance;
    private final Map<QuestParams, PendingDefinition<ObjectiveFilter>> byParams = new ConcurrentHashMap<>();

    private Objective(Builder builder) {
        this.id = builder.id;
        this.rawFilter = builder.rawFilter;
        this.caller = builder.caller;
        this.filter = builder.filter;
        this.goal = builder.goal;
        this.mode = builder.mode;
        this.conditions = Collections.unmodifiableList(new ArrayList<>(builder.conditions));
        this.costs = Collections.unmodifiableList(new ArrayList<>(builder.costs));
        this.then = builder.then;
        this.onMatch = builder.onMatch;
        this.hidden = builder.hidden;
        this.description = builder.description;
        this.platforms = Collections.unmodifiableSet(new LinkedHashSet<>(builder.platforms));
        this.provenance = builder.provenance;
    }

    /** An objective over a filter already parsed ({@code ObjectiveTypes.registry().parseLater(...)}). */
    public static Builder builder(String id, PendingDefinition<ObjectiveFilter> filter) {
        return new Builder(id, filter, null, null);
    }

    /** An objective read from {@code rawFilter}, read again for every parameter value of a quest with params. */
    static Builder builder(String id, ECPluginData caller, JsonNode rawFilter) {
        return new Builder(id, ObjectiveTypes.registry().parseLater(caller, rawFilter), caller, rawFilter);
    }

    public String getId() {
        return id;
    }

    /** The filter as written, before any parameter; null for an objective built in code. */
    public @Nullable JsonNode getRawFilter() {
        return rawFilter;
    }

    /** The filter read with no parameter substituted. */
    public PendingDefinition<ObjectiveFilter> getFilter() {
        return filter;
    }

    /**
     * The filter for an instance whose params are {@code params}; empty while its type is not registered. Read once per
     * distinct value and kept. Any thread.
     */
    public @Nullable ObjectiveFilter getFilter(QuestParams params) {
        if (params.isEmpty() || rawFilter == null || caller == null) {
            return filter.get().orElse(null);
        }
        return byParams.computeIfAbsent(params, values -> ObjectiveTypes.registry().parseLater(caller, substitute(rawFilter, values)))
                .get().orElse(null);
    }

    /** The type this objective waits for; null once it is read. */
    public @Nullable String getPendingType() {
        return filter.getMissingType();
    }

    /** {@code node} with every text value's {@code {param}} replaced. */
    static JsonNode substitute(JsonNode node, QuestParams params) {
        if (node.isTextual()) {
            return TextNode.valueOf(params.apply(node.asText()));
        }
        if (node.isObject()) {
            ObjectNode copy = ((ObjectNode) node).objectNode();
            for (Iterator<Map.Entry<String, JsonNode>> fields = node.fields(); fields.hasNext(); ) {
                Map.Entry<String, JsonNode> field = fields.next();
                copy.set(field.getKey(), substitute(field.getValue(), params));
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = ((ArrayNode) node).arrayNode();
            for (JsonNode item : node) {
                copy.add(substitute(item, params));
            }
            return copy;
        }
        return node;
    }

    /** The count that completes it; ignored by {@link ProgressMode#EACH_MATCH}. */
    public long getGoal() {
        return goal;
    }

    public ProgressMode getMode() {
        return mode;
    }

    /** Tested for every match (world, game mode, held item...); a failing one makes the match not count. */
    public List<Condition> getConditions() {
        return conditions;
    }

    /**
     * Taken for every match that passed the conditions and the {@code ObjectiveProgressPreEvent} ({@code QuestEngine.progress}
     * included); a cost that does not come out makes the match not count.
     */
    public List<Cost> getCosts() {
        return costs;
    }

    /** Inside an {@code any} group: where the stage goes when this objective is the one that closed it. */
    public @Nullable Next getThen() {
        return then;
    }

    /** {@link ProgressMode#EACH_MATCH}: applied for every match, with {@code {delta}} in the context. */
    public EffectList getOnMatch() {
        return onMatch;
    }

    public boolean isHidden() {
        return hidden;
    }

    public String getDescription() {
        return description;
    }

    /** The platforms it counts on; empty = all. Skipped on another platform, it counts as done there. */
    public Set<String> getPlatforms() {
        return platforms;
    }

    /** The origin tests a match passes before it counts; null = any origin. */
    public @Nullable ProvenanceFilter getProvenance() {
        return provenance;
    }

    /** Whether this platform skips it ({@link #getPlatforms()}). */
    public boolean isSkippedHere() {
        return !platforms.isEmpty() && !platforms.contains(EverNifeCore.getPlatform().getPlatformProviderId());
    }

    @Override
    public String toString() {
        return id + "(" + (filter.isResolved() ? filter.get().orElse(null) : "pending " + filter.getMissingType()) + ")";
    }

    public static final class Builder {

        private final String id;
        private final PendingDefinition<ObjectiveFilter> filter;
        private final @Nullable ECPluginData caller;
        private final @Nullable JsonNode rawFilter;
        private long goal = 1;
        private boolean goalWritten;
        private ProgressMode mode = ProgressMode.COUNT;
        private final List<Condition> conditions = new ArrayList<>();
        private final List<Cost> costs = new ArrayList<>();
        private @Nullable Next then;
        private EffectList onMatch = Effects.of(Collections.emptyList());
        private boolean hidden;
        private String description = "";
        private final Set<String> platforms = new LinkedHashSet<>();
        private @Nullable ProvenanceFilter provenance;

        private Builder(String id, PendingDefinition<ObjectiveFilter> filter, @Nullable ECPluginData caller, @Nullable JsonNode rawFilter) {
            this.id = Objects.requireNonNull(id, "id");
            this.filter = Objects.requireNonNull(filter, "filter");
            this.caller = caller;
            this.rawFilter = rawFilter;
        }

        public Builder goal(long goal) {
            this.goal = goal;
            this.goalWritten = true;
            return this;
        }

        public Builder mode(ProgressMode mode) {
            this.mode = Objects.requireNonNull(mode, "mode");
            return this;
        }

        public Builder condition(Condition condition) {
            conditions.add(condition);
            return this;
        }

        public Builder cost(Cost cost) {
            costs.add(cost);
            return this;
        }

        public Builder then(@Nullable Next then) {
            this.then = then;
            return this;
        }

        public Builder onMatch(EffectList onMatch) {
            this.onMatch = Objects.requireNonNull(onMatch, "onMatch");
            return this;
        }

        public Builder hidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }

        public Builder description(String description) {
            this.description = Objects.requireNonNull(description, "description");
            return this;
        }

        public Builder platforms(String... platformIds) {
            Collections.addAll(platforms, platformIds);
            return this;
        }

        public Builder provenance(@Nullable ProvenanceFilter provenance) {
            this.provenance = provenance;
            return this;
        }

        /** @throws IllegalArgumentException for a goal below 1, or a goal written on a pay-every-match objective */
        public Objective build() {
            if (mode == ProgressMode.EACH_MATCH && goalWritten) {
                throw new IllegalArgumentException("Objective '" + id + "' has both a goal and mode each-match: count until the goal OR pay every"
                        + " match - 'pay every 10' is goal 10 with repeat unlimited.");
            }
            if (mode == ProgressMode.COUNT && goal < 1) {
                throw new IllegalArgumentException("Objective '" + id + "' has goal " + goal + ": a goal is a count of at least 1.");
            }
            return new Objective(this);
        }
    }
}
