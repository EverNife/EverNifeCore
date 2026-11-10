package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.Variables;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A kind of payment a plugin declares in code - a raid stage, an arena wave, a race position - with the marks its
 * participants can carry and the {@code {variables}} its texts can read. A {@link RewardScene} is built under one; a
 * rule read with {@link RewardRule#parse(ECPluginData, RewardOccasion, JsonNode)} may write only what it gives.
 * Immutable; any thread.
 */
public final class RewardOccasion {

    /** What every reward text reads, whatever the occasion: {@code ExecutionContext.resolve} answers them. */
    public static final List<String> ALWAYS_ACCEPTED = Collections.unmodifiableList(Arrays.asList("player", "player-uuid", "owner", "caller"));

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_.:-]+)}");

    private final ECPluginData plugin;
    private final String id;
    private final Set<String> marks;
    private final Set<String> variables;

    private RewardOccasion(Builder builder) {
        this.plugin = builder.plugin;
        this.id = builder.id;
        this.marks = Collections.unmodifiableSet(new LinkedHashSet<>(builder.marks));
        this.variables = Collections.unmodifiableSet(new LinkedHashSet<>(builder.variables));
    }

    /** @param id the occasion's name within {@code plugin}, e.g. {@code raid} */
    public static Builder builder(ECPluginData plugin, String id) {
        return new Builder(plugin, id);
    }

    public ECPluginData getPlugin() {
        return plugin;
    }

    public String getId() {
        return id;
    }

    /** The marks a participant may carry ({@code alive}, {@code mvp}), in declaration order. Read-only. */
    public Set<String> getMarks() {
        return marks;
    }

    /** The {@code {variables}} a scene of this occasion fills ({@code stage}, {@code wave}), in declaration order. Read-only. */
    public Set<String> getVariables() {
        return variables;
    }

    /** Whether a text of {@code caller} under this occasion may write {@code {key}}: declared, always accepted, or a registered variable. */
    public boolean acceptsVariable(ECPluginData caller, String key) {
        return variables.contains(key) || ALWAYS_ACCEPTED.contains(key) || Variables.find(caller, key).isPresent();
    }

    /** @throws TypeRegistryException naming the first {@code {key}} in a text of {@code node} this occasion does not accept */
    void checkVariables(ECPluginData caller, JsonNode node) throws TypeRegistryException {
        if (node == null) {
            return;
        }
        if (node.isTextual()) {
            Matcher matcher = PLACEHOLDER.matcher(node.asText());
            while (matcher.find()) {
                String key = matcher.group(1);
                if (!acceptsVariable(caller, key)) {
                    throw new TypeRegistryException("The reward text '" + node.asText() + "' reads {" + key + "}, which the occasion '" + id
                            + "' does not give. It gives " + accepted() + " and every registered variable. Fix the name, or declare"
                            + " the variable on the occasion with RewardOccasion.builder(plugin, \"" + id + "\").variables(\"" + key + "\").");
                }
            }
            return;
        }
        for (JsonNode child : node) {
            checkVariables(caller, child);
        }
    }

    private String accepted() {
        Set<String> all = new LinkedHashSet<>(variables);
        all.addAll(ALWAYS_ACCEPTED);
        StringBuilder text = new StringBuilder();
        for (String key : all) {
            text.append(text.length() == 0 ? "" : ", ").append('{').append(key).append('}');
        }
        return text.toString();
    }

    @Override
    public String toString() {
        return "RewardOccasion{" + plugin.getMetaInfo().getName() + ":" + id + ", marks=" + marks + ", variables=" + variables + "}";
    }

    public static final class Builder {
        private final ECPluginData plugin;
        private final String id;
        private final Set<String> marks = new LinkedHashSet<>();
        private final Set<String> variables = new LinkedHashSet<>();

        private Builder(ECPluginData plugin, String id) {
            this.plugin = Objects.requireNonNull(plugin, "plugin");
            this.id = Objects.requireNonNull(id, "id").trim().toLowerCase(Locale.ROOT);
            if (this.id.isEmpty()) {
                throw new IllegalArgumentException("A RewardOccasion needs an id - its name within the plugin, e.g. \"raid\".");
            }
        }

        /** Adds marks a participant may carry. */
        public Builder marks(String... marks) {
            this.marks.addAll(Arrays.asList(marks));
            return this;
        }

        /**
         * Adds the {@code {variables}} a scene fills.
         *
         * @throws IllegalArgumentException for a name every reward text already reads ({@link #ALWAYS_ACCEPTED})
         */
        public Builder variables(String... variables) {
            for (String variable : variables) {
                if (ALWAYS_ACCEPTED.contains(variable)) {
                    throw new IllegalArgumentException("The occasion '" + id + "' declares the variable {" + variable + "}, which every reward"
                            + " text already reads (" + ALWAYS_ACCEPTED + "): pick another name. A participant's {player} is the name"
                            + " given to RewardScene.Builder.participant.");
                }
                this.variables.add(variable);
            }
            return this;
        }

        public RewardOccasion build() {
            return new RewardOccasion(this);
        }
    }
}
