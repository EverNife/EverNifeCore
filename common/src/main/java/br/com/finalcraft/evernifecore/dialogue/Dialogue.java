package br.com.finalcraft.evernifecore.dialogue;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.locale.LocaleOverlay;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.CustomVariables;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.region.RegionId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A conversation as a tree: nodes holding the speaker's line and the options the player answers with, each option
 * gated, carrying effects and pointing at the next node. Immutable and already checked once built ({@link Builder#build()}
 * or {@link #parse}): every reference resolves and every node can reach an end. {@link Dialogues} registers it and starts
 * sessions of it; the core keeps nothing of it on disk.
 *
 * <p>Its texts go through the owner's {@link LocaleOverlay} under the definition {@link #getOverlayId()}: a text may
 * carry its languages inline ({@code {pt-BR: ..., en-US: ...}}) and a translator adds others in the overlay files.</p>
 */
public final class Dialogue {

    /** How long a session waits for an answer when the dialogue says nothing. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);
    /** The node a session starts on when the dialogue says nothing. */
    public static final String DEFAULT_ENTRY = "start";

    private final ECPluginData owner;
    private final DialogueId id;
    private final @Nullable JsonNode speaker;
    private final String entryNode;
    private final Map<String, DialogueNode> nodes;
    private final Duration timeout;
    private final List<Condition> failWhen;
    private final List<DialogueTrigger> triggers;

    private Dialogue(Builder builder) {
        this.owner = builder.owner;
        this.id = builder.id;
        this.speaker = builder.speaker;
        this.entryNode = builder.entry;
        this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.nodes));
        this.timeout = builder.timeout;
        this.failWhen = Collections.unmodifiableList(new ArrayList<>(builder.failWhen));
        this.triggers = Collections.unmodifiableList(new ArrayList<>(builder.triggers));
    }

    /** A dialogue of {@code owner} called {@code name}; {@link Builder#build()} checks it. */
    public static Builder builder(ECPluginData owner, String name) {
        return new Builder(owner, DialogueId.of(owner, name));
    }

    /**
     * Reads the dialogue {@code name} of {@code owner} from its YAML tree (the root of the plugin's file); {@code zone} is
     * what a window without its own zone is read in.
     *
     * @throws DialogueDefinitionException naming the key that does not read, or what {@link Builder#build()} refuses
     */
    public static Dialogue parse(ECPluginData owner, String name, JsonNode root, ZoneId zone) {
        return DialogueYaml.read(builder(owner, name), root, zone).build();
    }

    public ECPluginData getOwner() {
        return owner;
    }

    public DialogueId getId() {
        return id;
    }

    /** The definition id of its texts in the owner's {@link LocaleOverlay}: {@code dialogue:<name>}. */
    public String getOverlayId() {
        return "dialogue:" + id.getName();
    }

    public String getEntryNode() {
        return entryNode;
    }

    /** By id, in the order written. */
    public Map<String, DialogueNode> getNodes() {
        return nodes;
    }

    public @Nullable DialogueNode getNode(String nodeId) {
        return nodeId == null ? null : nodes.get(nodeId.toLowerCase(Locale.ROOT));
    }

    /** How long a session waits for an answer before it ends with {@link DialogueEndCause#TIMEOUT}; zero = forever. */
    public Duration getTimeout() {
        return timeout;
    }

    /** A session ends with {@link DialogueEndCause#FAIL_WHEN} when one of these fails, asked when one of its sources fires. */
    public List<Condition> getFailWhen() {
        return failWhen;
    }

    /** What starts it by itself; empty = only the API and the {@code start-dialogue} effect do. */
    public List<DialogueTrigger> getTriggers() {
        return triggers;
    }

    /** Who talks, for {@code ctx}'s player and locale; empty when the dialogue names no speaker. */
    public String getSpeaker(ExecutionContext ctx) {
        return speaker == null ? "" : text("speaker", ctx);
    }

    /** The speaker's line on {@code node}, for {@code ctx}'s player and locale, its {@code {variables}} filled. */
    public String getText(DialogueNode node, ExecutionContext ctx) {
        return text("nodes." + node.getId() + ".text", ctx);
    }

    /** The text of {@code option} of {@code node}, for {@code ctx}'s player and locale, its {@code {variables}} filled. */
    public String getText(DialogueNode node, DialogueOption option, ExecutionContext ctx) {
        return text("nodes." + node.getId() + ".options." + option.getId() + ".text", ctx);
    }

    private String text(String path, ExecutionContext ctx) {
        LocaleOverlay overlay = LocaleOverlay.ofIfPresent(owner);
        if (overlay == null || !overlay.isRegistered(getOverlayId())) {
            throw new IllegalStateException("The dialogue " + id + " is not registered, so its texts are not in the LocaleOverlay of "
                    + owner.getMetaInfo().getName() + " yet. Register it first: Dialogues.of(plugin).register(dialogue).");
        }
        return ctx.resolve(overlay.text(getOverlayId(), path, ctx.getLocale().toString()).getText());
    }

    /** Every text of the dialogue at its overlay path, the tree {@link Dialogues#register} hands the overlay. */
    ObjectNode overlayTree() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        if (speaker != null) {
            root.set("speaker", speaker.deepCopy());
        }
        ObjectNode nodesNode = root.putObject("nodes");
        for (DialogueNode node : nodes.values()) {
            ObjectNode nodeNode = nodesNode.putObject(node.getId());
            nodeNode.set("text", node.getRawText().deepCopy());
            ObjectNode optionsNode = nodeNode.putObject("options");
            for (DialogueOption option : node.getOptions().values()) {
                optionsNode.putObject(option.getId()).set("text", option.getRawText().deepCopy());
            }
        }
        return root;
    }

    @Override
    public String toString() {
        return "Dialogue{" + id + ", nodes=" + nodes.keySet() + "}";
    }

    public static final class Builder {

        private final ECPluginData owner;
        private final DialogueId id;
        private JsonNode speaker;
        private String entry = DEFAULT_ENTRY;
        private final Map<String, DialogueNode> nodes = new LinkedHashMap<>();
        private Duration timeout = DEFAULT_TIMEOUT;
        private final List<Condition> failWhen = new ArrayList<>();
        private final List<DialogueTrigger> triggers = new ArrayList<>();

        private Builder(ECPluginData owner, DialogueId id) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.id = id;
        }

        public DialogueId getId() {
            return id;
        }

        public ECPluginData getOwner() {
            return owner;
        }

        public Builder speaker(String speaker) {
            this.speaker = DialogueTexts.of(speaker);
            return this;
        }

        public Builder speaker(Map<String, String> byLocale) {
            this.speaker = DialogueTexts.of(byLocale);
            return this;
        }

        public Builder speaker(@Nullable JsonNode speaker) {
            this.speaker = speaker == null ? null : DialogueTexts.of(speaker);
            return this;
        }

        public Builder entry(String node) {
            this.entry = RegionId.requireName("dialogue node", node);
            return this;
        }

        /** Zero waits forever. */
        public Builder timeout(Duration timeout) {
            if (timeout.isNegative()) {
                throw new IllegalArgumentException("The timeout of the dialogue " + id + " is negative (" + timeout + "): give it how long"
                        + " a session waits for an answer, or zero to wait forever.");
            }
            this.timeout = timeout;
            return this;
        }

        public Builder failWhen(Condition... conditions) {
            this.failWhen.addAll(Arrays.asList(conditions));
            return this;
        }

        public Builder trigger(DialogueTrigger trigger) {
            this.triggers.add(Objects.requireNonNull(trigger, "trigger"));
            return this;
        }

        /** @throws DialogueDefinitionException when the dialogue already has a node with that id */
        public Builder node(DialogueNode node) {
            if (nodes.putIfAbsent(node.getId(), node) != null) {
                throw new DialogueDefinitionException("The dialogue " + id + " has two nodes called '" + node.getId()
                        + "': a goto could not tell them apart. Rename one.");
            }
            return this;
        }

        public Builder node(DialogueNode.Builder node) {
            return node(node.build());
        }

        /**
         * The dialogue, checked: the entry node exists, every goto names a node, every {@code remember} names a custom
         * {@code String} variable, and every node reaches an end (a node without options, or an option that ends) -
         * a loop that only leads back into itself traps the player until the timeout.
         *
         * @throws DialogueDefinitionException naming the dialogue, the node, the option and the way out
         */
        public Dialogue build() {
            if (nodes.isEmpty()) {
                throw new DialogueDefinitionException("The dialogue " + id + " has no node. Give it at least the entry node '" + entry
                        + "' with the speaker's first line.");
            }
            if (!nodes.containsKey(entry)) {
                throw new DialogueDefinitionException("The dialogue " + id + " starts on the node '" + entry + "' but no node is called '"
                        + entry + "'. Nodes defined: " + String.join(", ", nodes.keySet()) + ". Add the node or set entry to one of these.");
            }
            for (DialogueNode node : nodes.values()) {
                for (DialogueOption option : node.getOptions().values()) {
                    if (option.getGoto() != null && !nodes.containsKey(option.getGoto())) {
                        throw new DialogueDefinitionException("The dialogue " + id + " has option '" + option.getId() + "' on node '"
                                + node.getId() + "' with goto '" + option.getGoto() + "' but no node is called '" + option.getGoto()
                                + "'. Nodes defined: " + String.join(", ", nodes.keySet()) + ". Add the node or point the option at one of these.");
                    }
                }
                checkRemember(node);
            }
            checkEveryNodeEnds();
            return new Dialogue(this);
        }

        private void checkRemember(DialogueNode node) {
            String remember = node.getRemember();
            if (remember == null) {
                return;
            }
            Optional<CustomVariables.CustomVariable<?>> variable = CustomVariables.find(remember);
            if (!variable.isPresent()) {
                throw new DialogueDefinitionException("The dialogue " + id + " remembers the answer of node '" + node.getId() + "' in '"
                        + remember + "', but no plugin defined that custom variable. Define it before the dialogue loads: "
                        + "Variables.custom(plugin, storage).define(\"<name>\", String.class, \"\") - the pick writes the option id into it.");
            }
            if (variable.get().getType() != String.class) {
                throw new DialogueDefinitionException("The dialogue " + id + " remembers the answer of node '" + node.getId() + "' in '"
                        + remember + "', which holds a " + variable.get().getType().getSimpleName() + ": the pick writes the option id, a"
                        + " text. Point remember at a custom variable defined as String.class.");
            }
        }

        //a node can end when it is terminal or an option of it ends; then, backwards, when an option leads to one that can
        private void checkEveryNodeEnds() {
            Set<String> ending = new LinkedHashSet<>();
            for (DialogueNode node : nodes.values()) {
                if (node.isTerminal() || node.getOptions().values().stream().anyMatch(DialogueOption::isEnd)) {
                    ending.add(node.getId());
                }
            }
            Set<String> reaches = new LinkedHashSet<>(ending);
            boolean grew = true;
            while (grew) {
                grew = false;
                for (DialogueNode node : nodes.values()) {
                    if (!reaches.contains(node.getId())
                            && node.getOptions().values().stream().anyMatch(option -> option.getGoto() != null && reaches.contains(option.getGoto()))) {
                        reaches.add(node.getId());
                        grew = true;
                    }
                }
            }
            List<String> trapped = nodes.keySet().stream().filter(node -> !reaches.contains(node)).collect(Collectors.toList());
            if (!trapped.isEmpty()) {
                throw new DialogueDefinitionException("The dialogue " + id + " has a loop with no way out: nodes " + trapped
                        + " only lead back into themselves. Give one of them an option with 'end: true' or a goto to a node that ends"
                        + (ending.isEmpty() ? " - there is none yet: add a node without options." : " (" + String.join(", ", ending) + ")."));
            }
        }
    }
}
