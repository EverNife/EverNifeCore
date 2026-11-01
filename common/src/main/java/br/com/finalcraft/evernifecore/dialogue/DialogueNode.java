package br.com.finalcraft.evernifecore.dialogue;

import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.region.RegionId;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * One step of a dialogue: the speaker's line and the options the player answers with. A node without options - or
 * with an option that {@link DialogueOption#isEnd() ends} - is where a talk can end.
 */
public final class DialogueNode {

    private final String id;
    private final JsonNode text;
    private final Map<String, DialogueOption> options;
    private final @Nullable String remember;
    private final List<Effect> effectsOnEnter;

    private DialogueNode(Builder builder) {
        this.id = builder.id;
        this.text = Objects.requireNonNull(builder.text, () -> "The node '" + builder.id + "' has no text: give it the speaker's line.");
        this.options = Collections.unmodifiableMap(new LinkedHashMap<>(builder.options));
        this.remember = builder.remember;
        this.effectsOnEnter = Collections.unmodifiableList(new ArrayList<>(builder.effectsOnEnter));
    }

    /** @throws IllegalArgumentException when {@code id} is not {@code [a-z0-9_-]{1,64}} */
    public static Builder builder(String id) {
        return new Builder(RegionId.requireName("dialogue node", id));
    }

    public String getId() {
        return id;
    }

    /** The line as written; {@link Dialogue#getText(DialogueNode, br.com.finalcraft.evernifecore.logic.ExecutionContext)} resolves it. */
    JsonNode getRawText() {
        return text;
    }

    /** By id, in the order written. Empty = a terminal node: showing it ends the dialogue with {@link DialogueEndCause#END}. */
    public Map<String, DialogueOption> getOptions() {
        return options;
    }

    public @Nullable DialogueOption getOption(String id) {
        return id == null ? null : options.get(id.toLowerCase(Locale.ROOT));
    }

    /** Whether reaching this node is the end of the talk: it has no option to answer with. */
    public boolean isTerminal() {
        return options.isEmpty();
    }

    /** The custom variable ({@code plugin:name}, a {@code String}) a pick here writes the option id into; null = no memory. */
    public @Nullable String getRemember() {
        return remember;
    }

    /** Run each time the node is shown, before the player answers. */
    public List<Effect> getEffectsOnEnter() {
        return effectsOnEnter;
    }

    @Override
    public String toString() {
        return "DialogueNode{" + id + ", options=" + options.keySet() + "}";
    }

    public static final class Builder {

        private final String id;
        private JsonNode text;
        private final Map<String, DialogueOption> options = new LinkedHashMap<>();
        private String remember;
        private final List<Effect> effectsOnEnter = new ArrayList<>();

        private Builder(String id) {
            this.id = id;
        }

        public Builder text(String text) {
            this.text = DialogueTexts.of(text);
            return this;
        }

        public Builder text(Map<String, String> byLocale) {
            this.text = DialogueTexts.of(byLocale);
            return this;
        }

        public Builder text(JsonNode text) {
            this.text = DialogueTexts.of(text);
            return this;
        }

        /** @throws IllegalArgumentException when the node already has an option with that id */
        public Builder option(DialogueOption option) {
            if (options.putIfAbsent(option.getId(), option) != null) {
                throw new IllegalArgumentException("The node '" + id + "' has two options called '" + option.getId()
                        + "': the option id is what remember writes and what a click names, so give each its own.");
            }
            return this;
        }

        public Builder option(DialogueOption.Builder option) {
            return option(option.build());
        }

        public Builder remember(@Nullable String customVariableId) {
            this.remember = customVariableId == null ? null : customVariableId.toLowerCase(Locale.ROOT);
            return this;
        }

        public Builder onEnter(Effect... effects) {
            this.effectsOnEnter.addAll(Arrays.asList(effects));
            return this;
        }

        public Builder onEnter(List<Effect> effects) {
            this.effectsOnEnter.addAll(effects);
            return this;
        }

        public DialogueNode build() {
            return new DialogueNode(this);
        }
    }
}
