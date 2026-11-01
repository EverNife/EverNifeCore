package br.com.finalcraft.evernifecore.dialogue;

import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.region.RegionId;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One answer the player can give on a node: its text, the {@link Gate} that decides whether they may give it, the
 * effects giving it runs, and where the talk goes next. A failing gate draws the option greyed out with every failure
 * (or hides it, {@link #isHidden()}); picking it pays the gate, then runs the effects.
 */
public final class DialogueOption {

    private final String id;
    private final JsonNode text;
    private final Gate gate;
    private final List<Effect> effects;
    private final @Nullable String goTo;
    private final boolean end;
    private final boolean hidden;

    private DialogueOption(Builder builder) {
        this.id = builder.id;
        this.text = Objects.requireNonNull(builder.text, () -> "The option '" + builder.id + "' has no text: give it the line the player reads.");
        this.gate = builder.gate;
        this.effects = Collections.unmodifiableList(new ArrayList<>(builder.effects));
        this.goTo = builder.goTo;
        this.end = builder.end;
        this.hidden = builder.hidden;
    }

    /** @throws IllegalArgumentException when {@code id} is not {@code [a-z0-9_-]{1,64}} */
    public static Builder builder(String id) {
        return new Builder(RegionId.requireName("dialogue option", id));
    }

    public String getId() {
        return id;
    }

    /** The text as written: a string, a list of lines or a map of locale to text. {@link Dialogue#getText} resolves it. */
    JsonNode getRawText() {
        return text;
    }

    /** Checked on every draw ({@code check}) and paid on the pick ({@code pay}); {@link Gate#open()} when none was given. */
    public Gate getGate() {
        return gate;
    }

    /** Run after the gate was paid, in order. */
    public List<Effect> getEffects() {
        return effects;
    }

    /** The node the talk moves to; null with {@link #isEnd()} false keeps the player on the same node. */
    public @Nullable String getGoto() {
        return goTo;
    }

    /** Whether picking it ends the dialogue with {@link DialogueEndCause#END}, after its effects. */
    public boolean isEnd() {
        return end;
    }

    /** Whether a failing gate hides the option instead of drawing it greyed out. */
    public boolean isHidden() {
        return hidden;
    }

    @Override
    public String toString() {
        return "DialogueOption{" + id + (goTo != null ? " -> " + goTo : "") + (end ? ", end" : "") + "}";
    }

    public static final class Builder {

        private final String id;
        private JsonNode text;
        private Gate gate = Gate.open();
        private final List<Effect> effects = new ArrayList<>();
        private String goTo;
        private boolean end;
        private boolean hidden;

        private Builder(String id) {
            this.id = id;
        }

        /** The line, the same for every language. */
        public Builder text(String text) {
            this.text = DialogueTexts.of(text);
            return this;
        }

        /** The line per locale ({@code pt-BR}, {@code EN_US}...); the first entry is the author's language. */
        public Builder text(Map<String, String> byLocale) {
            this.text = DialogueTexts.of(byLocale);
            return this;
        }

        /** The line as a config node: a string, a list of lines or a map of locale to text. */
        public Builder text(JsonNode text) {
            this.text = DialogueTexts.of(text);
            return this;
        }

        public Builder gate(Gate gate) {
            this.gate = Objects.requireNonNull(gate, "gate");
            return this;
        }

        public Builder effects(Effect... effects) {
            return effects(Arrays.asList(effects));
        }

        public Builder effects(List<Effect> effects) {
            this.effects.addAll(effects);
            return this;
        }

        /** The node picking it leads to. */
        public Builder goTo(@Nullable String node) {
            this.goTo = node == null ? null : RegionId.requireName("dialogue node", node);
            return this;
        }

        /** Picking it ends the dialogue. */
        public Builder end() {
            return end(true);
        }

        public Builder end(boolean end) {
            this.end = end;
            return this;
        }

        /** A failing gate hides it instead of greying it out. */
        public Builder hidden() {
            return hidden(true);
        }

        public Builder hidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }

        public DialogueOption build() {
            return new DialogueOption(this);
        }
    }
}
