package br.com.finalcraft.evernifecore.dialogue;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.Conditions;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.npc.NpcRef;
import br.com.finalcraft.evernifecore.region.RegionId;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Reads the YAML tree of a dialogue into its builder; every refusal names the dialogue, the node, the option and the key. */
final class DialogueYaml {

    private static final Set<String> DIALOGUE_KEYS = keys("speaker", "entry", "timeout", "fail-when", "triggers", "nodes");
    private static final Set<String> NODE_KEYS = keys("text", "remember", "on-enter", "options");
    private static final Set<String> OPTION_KEYS = keys("id", "text", "gate", "effects", "goto", "end", "hidden");

    private DialogueYaml() {
    }

    static Dialogue.Builder read(Dialogue.Builder dialogue, JsonNode root, ZoneId zone) {
        String where = "The dialogue " + dialogue.getId();
        requireObject(root, where);
        checkKeys(root, DIALOGUE_KEYS, where);
        ECPluginData owner = dialogue.getOwner();
        guard(where + " key 'speaker'", () -> root.has("speaker") ? dialogue.speaker(root.get("speaker")) : dialogue);
        guard(where + " key 'entry'", () -> root.has("entry") ? dialogue.entry(root.get("entry").asText()) : dialogue);
        if (root.has("timeout")) {
            guard(where + " key 'timeout'", () -> dialogue.timeout(Duration.ofMillis(FCTimeFrame.fromConfigString(root.get("timeout").asText()).getMillis())));
        }
        for (JsonNode condition : list(root.get("fail-when"))) {
            guard(where + " key 'fail-when'", () -> dialogue.failWhen(Conditions.parse(owner, condition, zone)));
        }
        for (JsonNode trigger : list(root.get("triggers"))) {
            guard(where + " key 'triggers'", () -> dialogue.trigger(trigger(trigger)));
        }
        JsonNode nodes = root.get("nodes");
        if (nodes == null || !nodes.isObject()) {
            throw new DialogueDefinitionException(where + " has no 'nodes' map. Write each node under nodes: <id>: {text: ..., options: ...}.");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = nodes.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            dialogue.node(node(owner, where, field.getKey(), field.getValue(), zone));
        }
        return dialogue;
    }

    private static DialogueTrigger trigger(JsonNode node) {
        if (node.isObject() && node.size() == 1) {
            if (node.has("npc")) {
                return DialogueTrigger.npc(NpcRef.parse(node.get("npc").asText()));
            }
            if (node.has("region-enter")) {
                return DialogueTrigger.regionEnter(RegionId.parse(node.get("region-enter").asText()));
            }
        }
        throw new IllegalArgumentException("a trigger is '- npc: <provider:id>' or '- region-enter: <owner:name>', not " + node);
    }

    private static DialogueNode node(ECPluginData owner, String dialogueWhere, String id, JsonNode node, ZoneId zone) {
        String where = dialogueWhere + " node '" + id + "'";
        requireObject(node, where);
        checkKeys(node, NODE_KEYS, where);
        DialogueNode.Builder builder = guard(where, () -> DialogueNode.builder(id));
        guard(where + " key 'text'", () -> builder.text(node.get("text")));
        if (node.has("remember")) {
            builder.remember(node.get("remember").asText());
        }
        guard(where + " key 'on-enter'", () -> builder.onEnter(Effects.parseList(owner, node.get("on-enter"), zone).getEffects()));
        JsonNode options = node.get("options");
        if (options != null && options.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = options.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                builder.option(option(owner, where, field.getKey(), field.getValue(), zone));
            }
        } else if (options != null && options.isArray()) {
            for (JsonNode option : options) {
                JsonNode optionId = option.get("id");
                if (optionId == null || !optionId.isValueNode()) {
                    throw new DialogueDefinitionException(where + " lists an option without an id: in a list each option says"
                            + " its id ('- {id: bye, text: ...}'), or write options as a map of id to option.");
                }
                builder.option(option(owner, where, optionId.asText(), option, zone));
            }
        } else if (options != null && !options.isNull()) {
            throw new DialogueDefinitionException(where + " key 'options' is " + options + ": write a map of id to option, a list of"
                    + " options with an id each, or [] for a node that ends the talk.");
        }
        return builder.build();
    }

    private static DialogueOption option(ECPluginData owner, String nodeWhere, String id, JsonNode node, ZoneId zone) {
        String where = nodeWhere + " option '" + id + "'";
        requireObject(node, where);
        checkKeys(node, OPTION_KEYS, where);
        DialogueOption.Builder builder = guard(where, () -> DialogueOption.builder(id));
        guard(where + " key 'text'", () -> builder.text(node.get("text")));
        guard(where + " key 'gate'", () -> builder.gate(Gate.parse(owner, node.get("gate"), zone)));
        guard(where + " key 'effects'", () -> builder.effects(Effects.parseList(owner, node.get("effects"), zone).getEffects()));
        if (node.has("goto")) {
            guard(where + " key 'goto'", () -> builder.goTo(node.get("goto").asText()));
        }
        builder.end(node.path("end").asBoolean(false));
        builder.hidden(node.path("hidden").asBoolean(false));
        return builder.build();
    }

    private static <T> T guard(String where, Supplier<T> read) {
        try {
            return read.get();
        } catch (DialogueDefinitionException already) {
            throw already;
        } catch (RuntimeException unreadable) {
            throw new DialogueDefinitionException(where + " does not read: " + unreadable.getMessage(), unreadable);
        }
    }

    private static void requireObject(JsonNode node, String where) {
        if (node == null || !node.isObject()) {
            throw new DialogueDefinitionException(where + " is " + node + ", and it has to be a map of keys.");
        }
    }

    private static void checkKeys(JsonNode node, Set<String> known, String where) {
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!known.contains(name)) {
                throw new DialogueDefinitionException(where + " has the key '" + name + "', which a dialogue does not read. Known here: "
                        + String.join(", ", known) + ".");
            }
        }
    }

    private static List<JsonNode> list(JsonNode node) {
        if (node == null || node.isNull()) {
            return Collections.emptyList();
        }
        List<JsonNode> items = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(items::add);
        } else {
            items.add(node);
        }
        return items;
    }

    private static Set<String> keys(String... names) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(names)));
    }
}
