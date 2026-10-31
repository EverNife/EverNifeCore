package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.Conditions;
import br.com.finalcraft.evernifecore.logic.Costs;
import br.com.finalcraft.evernifecore.logic.EffectList;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.logic.RepeatPolicy;
import br.com.finalcraft.evernifecore.time.Window;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import br.com.finalcraft.everylibs.util.FCTimeUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads quest definitions from YAML or a {@link JsonNode}; every type key ({@code type: break-block}) is a key of a
 * {@code TypeRegistry}. A definition that does not read throws an {@link IllegalArgumentException} naming the quest,
 * the key and the fix. Any thread.
 */
public final class QuestDefinitions {

    /** The top-level keys a quest reads. */
    public static final List<String> KEYS = Collections.unmodifiableList(Arrays.asList("id", "version", "on-version-change", "name",
            "description", "meta", "owner", "gate", "requires", "stages", "fail-when", "repeat", "time-limit", "expires-at", "abandonable",
            "params", "rewards", "window", "on-start", "on-complete", "on-fail", "on-abandon", "hytale-native"));

    private static final List<String> STAGE_KEYS = Arrays.asList("id", "objectives", "on-complete", "then");
    private static final YAMLMapper YAML = new YAMLMapper();

    private QuestDefinitions() {
    }

    /** {@link #parse(ECPluginData, JsonNode, ZoneId)} of a YAML text, in the server's zone. */
    public static QuestDefinition parseYaml(ECPluginData owner, String yaml) {
        JsonNode node;
        try {
            node = YAML.readTree(yaml);
        } catch (IOException e) {
            throw new IllegalArgumentException("A quest of " + owner.getMetaInfo().getName() + " is not valid YAML: " + e.getMessage(), e);
        }
        return parse(owner, node, Window.getServerZone());
    }

    /**
     * One quest of {@code owner}; its id is {@code <owner>:<id>}. {@code zone} reads a window or a date without its own.
     *
     * @throws IllegalArgumentException naming the quest, the key and what to write instead
     */
    public static QuestDefinition parse(ECPluginData owner, JsonNode node, ZoneId zone) {
        if (node == null || !node.isObject() || !node.hasNonNull("id")) {
            throw new IllegalArgumentException("A quest is a map with at least an id and its stages, e.g. {id: mine-iron, stages: [...]}"
                    + " - this one is " + node + ".");
        }
        String ownerName = owner.getMetaInfo().getName();
        QuestId id = QuestId.parse(node.get("id").asText(), ownerName);
        String where = "Quest '" + id + "'";
        try {
            for (Iterator<String> keys = node.fieldNames(); keys.hasNext(); ) {
                String key = keys.next();
                if (!KEYS.contains(key)) {
                    throw new IllegalArgumentException(where + " has no key '" + key + "'. A quest reads: " + KEYS + ".");
                }
            }
            QuestDefinition.Builder builder = QuestDefinition.builder(id);
            if (node.has("version")) {
                builder.version(node.get("version").asInt(1));
            }
            if (node.has("on-version-change")) {
                String policy = node.get("on-version-change").asText();
                try {
                    builder.policy(DefinitionPolicy.valueOf(policy.trim().toUpperCase(Locale.ROOT).replace('-', '_')));
                } catch (IllegalArgumentException invalid) {
                    throw new IllegalArgumentException(where + ": on-version-change '" + policy + "' is not one - write keep-by-id or reset.");
                }
            }
            if (node.has("name")) {
                builder.name(node.get("name").asText());
            }
            if (node.has("description")) {
                builder.description(node.get("description").asText());
            }
            if (node.has("meta")) {
                builder.meta(meta(where, node.get("meta")));
            }
            if (node.has("owner")) {
                JsonNode ownerNode = node.get("owner");
                String kind = ownerNode.isTextual() ? ownerNode.asText() : ownerNode.path("type").asText("player");
                builder.owner(kind.trim().toLowerCase(Locale.ROOT), ownerNode.hasNonNull("provider") ? ownerNode.get("provider").asText() : null);
            }
            if (node.has("gate")) {
                builder.gate(Gate.parse(owner, node.get("gate"), zone));
            }
            for (JsonNode required : list(node.get("requires"))) {
                builder.requires(QuestId.parse(required.asText(), ownerName));
            }
            for (JsonNode stage : list(node.get("stages"))) {
                builder.stage(stage(owner, where, stage, zone));
            }
            for (JsonNode condition : list(node.get("fail-when"))) {
                builder.failWhen(Conditions.parse(owner, condition, zone));
            }
            if (node.has("repeat")) {
                try {
                    builder.repeat(RepeatPolicy.parse(node.get("repeat"), zone));
                } catch (IllegalArgumentException invalid) {
                    throw new IllegalArgumentException(where + ": " + invalid.getMessage(), invalid);
                }
            }
            if (node.has("time-limit")) {
                builder.timeLimit(duration(where, "time-limit", node.get("time-limit")));
            }
            if (node.has("expires-at")) {
                builder.expiresAt(instant(where, node.get("expires-at"), zone));
            }
            if (node.has("abandonable")) {
                builder.abandonable(node.get("abandonable").asBoolean(true));
            }
            if (node.has("params")) {
                for (Iterator<Map.Entry<String, JsonNode>> params = node.get("params").fields(); params.hasNext(); ) {
                    Map.Entry<String, JsonNode> param = params.next();
                    List<String> values = new ArrayList<>();
                    for (JsonNode value : list(param.getValue().get("values"))) {
                        values.add(value.asText());
                    }
                    String pick = param.getValue().path("pick").asText("random");
                    builder.param(param.getKey(), new QuestDefinition.Param(values, !"first".equalsIgnoreCase(pick)));
                }
            }
            builder.onStart(effects(owner, node.get("on-start"), zone));
            builder.onComplete(effects(owner, node.get("on-complete"), zone));
            builder.onFail(effects(owner, node.get("on-fail"), zone));
            builder.onAbandon(effects(owner, node.get("on-abandon"), zone));
            return builder.build();
        } catch (IllegalArgumentException | TypeRegistryException failure) {
            if (failure instanceof IllegalArgumentException && failure.getMessage() != null && failure.getMessage().contains(id.toString())) {
                throw failure;
            }
            throw new IllegalArgumentException(where + ": " + failure.getMessage(), failure);
        }
    }

    private static Stage stage(ECPluginData owner, String where, JsonNode node, ZoneId zone) {
        if (!node.isObject() || !node.hasNonNull("id")) {
            throw new IllegalArgumentException(where + " has a stage without an id: every stage has a stable id, e.g. {id: mine, objectives: [...]}.");
        }
        String stageId = node.get("id").asText();
        for (Iterator<String> keys = node.fieldNames(); keys.hasNext(); ) {
            String key = keys.next();
            if (!STAGE_KEYS.contains(key)) {
                throw new IllegalArgumentException(where + ", stage '" + stageId + "' has no key '" + key + "'. A stage reads: " + STAGE_KEYS + ".");
            }
        }
        JsonNode written = node.get("objectives");
        ObjectiveGroup group;
        if (written != null && written.isObject()) {
            List<Objective> items = objectives(owner, where, stageId, written.get("items"), zone);
            String mode = written.path("mode").asText("all").trim().toLowerCase(Locale.ROOT);
            switch (mode) {
                case "all":
                    group = ObjectiveGroup.all(items);
                    break;
                case "any":
                    group = ObjectiveGroup.any(items);
                    break;
                case "at-least":
                    group = ObjectiveGroup.atLeast(written.path("count").asInt(1), items);
                    break;
                default:
                    throw new IllegalArgumentException(where + ", stage '" + stageId + "': objectives mode '" + mode + "' is not one - write all,"
                            + " any or at-least (with count).");
            }
        } else {
            List<Objective> items = objectives(owner, where, stageId, written, zone);
            if (items.isEmpty()) {
                throw new IllegalArgumentException(where + ", stage '" + stageId + "' has no objective: a stage needs at least one.");
            }
            group = ObjectiveGroup.all(items);
        }
        return new Stage(stageId, group, effects(owner, node.get("on-complete"), zone),
                node.hasNonNull("then") ? Next.parse(node.get("then").asText()) : null);
    }

    private static List<Objective> objectives(ECPluginData owner, String where, String stageId, @Nullable JsonNode nodes, ZoneId zone) {
        List<Objective> objectives = new ArrayList<>();
        for (JsonNode node : list(nodes)) {
            if (!node.isObject() || !node.hasNonNull("id")) {
                throw new IllegalArgumentException(where + ", stage '" + stageId + "' has an objective without an id: every objective has a"
                        + " stable id, e.g. {id: ore, type: break-block, block: \"minecraft:iron_ore\", goal: 32}.");
            }
            String objectiveId = node.get("id").asText();
            try {
                Objective.Builder builder = Objective.builder(objectiveId, owner, node);
                if (node.has("mode")) {
                    String mode = node.get("mode").asText().trim().toUpperCase(Locale.ROOT).replace('-', '_');
                    builder.mode(ProgressMode.valueOf(mode));
                }
                if (node.has("goal")) {
                    builder.goal(node.get("goal").asLong());
                }
                for (JsonNode condition : list(node.get("conditions"))) {
                    builder.condition(Conditions.parse(owner, condition, zone));
                }
                for (JsonNode cost : list(node.get("costs"))) {
                    builder.cost(Costs.parse(owner, cost, zone));
                }
                if (node.hasNonNull("then")) {
                    builder.then(Next.parse(node.get("then").asText()));
                }
                builder.onMatch(effects(owner, node.get("on-match"), zone));
                builder.hidden(node.path("hidden").asBoolean(false));
                if (node.has("description")) {
                    builder.description(node.get("description").asText());
                }
                for (JsonNode platform : list(node.get("platforms"))) {
                    builder.platforms(platform.asText());
                }
                objectives.add(builder.build());
            } catch (IllegalArgumentException | TypeRegistryException failure) {
                throw new IllegalArgumentException(where + ", objective '" + objectiveId + "': " + failure.getMessage(), failure);
            }
        }
        return objectives;
    }

    private static QuestMeta meta(String where, JsonNode node) {
        Set<String> tags = new LinkedHashSet<>();
        for (JsonNode tag : list(node.get("tags"))) {
            tags.add(tag.asText());
        }
        Map<String, JsonNode> extra = new LinkedHashMap<>();
        if (node.has("extra")) {
            node.get("extra").fields().forEachRemaining(field -> extra.put(field.getKey(), field.getValue()));
        }
        String visibility = node.path("visibility").asText("visible");
        QuestMeta.Visibility read;
        try {
            read = QuestMeta.Visibility.valueOf(visibility.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(where + ": meta.visibility '" + visibility + "' is not one - write visible, hidden or secret.");
        }
        return new QuestMeta(node.path("category").asText(""), node.hasNonNull("icon") ? node.get("icon").asText() : null,
                node.path("order").asInt(0), read, tags, extra);
    }

    private static EffectList effects(ECPluginData owner, @Nullable JsonNode node, ZoneId zone) {
        if (node == null || node.isNull()) {
            return Effects.of(Collections.emptyList());
        }
        return Effects.parseList(owner, node, zone);
    }

    private static Duration duration(String where, String key, JsonNode node) {
        String text = node.asText().trim();
        try {
            return Duration.ofMillis(text.matches("\\d+") ? Long.parseLong(text) * 1000L : FCTimeUtil.toMillis(text));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(where + ": " + key + " '" + text + "' is not a duration - write e.g. 30m, 1h30m or 90s.");
        }
    }

    private static Instant instant(String where, JsonNode node, ZoneId zone) {
        String text = node.asText().trim();
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException notIso) {
            try {
                return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")).atZone(zone).toInstant();
            } catch (DateTimeParseException invalid) {
                throw new IllegalArgumentException(where + ": expires-at '" + text + "' is not a date - write \"2026-11-01 20:00\" (server zone)"
                        + " or an ISO instant like 2026-11-01T23:00:00Z.");
            }
        }
    }

    private static List<JsonNode> list(@Nullable JsonNode node) {
        List<JsonNode> items = new ArrayList<>();
        if (node == null || node.isNull()) {
            return items;
        }
        if (node.isArray()) {
            node.forEach(items::add);
        } else {
            items.add(node);
        }
        return items;
    }

    /**
     * The stable text paths of {@code definition} for a language overlay: {@code quests.<name>.name},
     * {@code quests.<name>.description} and one {@code quests.<name>.stages.<stage>.objectives.<objective>.description}
     * per objective.
     */
    public static List<String> textPaths(QuestDefinition definition) {
        String base = "quests." + definition.getId().getName();
        List<String> paths = new ArrayList<>();
        paths.add(base + ".name");
        paths.add(base + ".description");
        for (Stage stage : definition.getStages()) {
            for (Objective objective : stage.getObjectives().getObjectives()) {
                paths.add(base + ".stages." + stage.getId() + ".objectives." + objective.getId() + ".description");
            }
        }
        return paths;
    }
}
