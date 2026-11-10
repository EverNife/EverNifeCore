package br.com.finalcraft.evernifecore.logic.reward;

import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Who took part in one payment of a {@link RewardOccasion} and what the texts read about it: each participant (uuid,
 * the name the plugin knows it by, its contribution, its marks) and the occasion's variables ({@code stage = 5}).
 * Given to {@link RewardContext.Builder#scene}, its participants are the rule's candidates - members of no owner
 * included - and each payment's {@code ExecutionContext} reads the variables and the participant's name as
 * {@code {player}}. Immutable; any thread.
 */
public final class RewardScene {

    private final RewardOccasion occasion;
    private final Map<UUID, Participant> participants;
    private final Map<String, String> variables;

    private RewardScene(Builder builder) {
        this.occasion = builder.occasion;
        this.participants = Collections.unmodifiableMap(new LinkedHashMap<>(builder.participants));
        this.variables = Collections.unmodifiableMap(new LinkedHashMap<>(builder.variables));
    }

    public static Builder builder(RewardOccasion occasion) {
        return new Builder(occasion);
    }

    public RewardOccasion getOccasion() {
        return occasion;
    }

    /** In the order they were added. Read-only. */
    public Collection<Participant> getParticipants() {
        return participants.values();
    }

    /** Null when {@code uuid} did not take part. */
    public @Nullable Participant getParticipant(UUID uuid) {
        return participants.get(uuid);
    }

    /** The occasion's variables this scene filled, by name. Read-only. */
    public Map<String, String> getVariables() {
        return variables;
    }

    /** Every participant's contribution, in their order. */
    Map<UUID, BigDecimal> getContributions() {
        Map<UUID, BigDecimal> contributions = new LinkedHashMap<>();
        for (Participant participant : participants.values()) {
            contributions.put(participant.getUniqueId(), participant.getContribution());
        }
        return contributions;
    }

    @Override
    public String toString() {
        return "RewardScene{" + occasion.getId() + ", " + participants.size() + " participants, variables=" + variables + "}";
    }

    /** One participant of a scene. Immutable. */
    public static final class Participant {
        private final UUID uniqueId;
        private final String name;
        private final BigDecimal contribution;
        private final Set<String> marks;

        private Participant(UUID uniqueId, String name, BigDecimal contribution, Set<String> marks) {
            this.uniqueId = uniqueId;
            this.name = name;
            this.contribution = contribution;
            this.marks = Collections.unmodifiableSet(marks);
        }

        public UUID getUniqueId() {
            return uniqueId;
        }

        /** What {@code {player}} reads for this participant, online or not. */
        public String getName() {
            return name;
        }

        public BigDecimal getContribution() {
            return contribution;
        }

        /** Read-only. */
        public Set<String> getMarks() {
            return marks;
        }

        public boolean hasMark(String mark) {
            return marks.contains(mark);
        }

        @Override
        public String toString() {
            return name + "(" + uniqueId + ", " + contribution.toPlainString() + ", " + marks + ")";
        }
    }

    public static final class Builder {
        private final RewardOccasion occasion;
        private final Map<UUID, Participant> participants = new LinkedHashMap<>();
        private final Map<String, String> variables = new LinkedHashMap<>();

        private Builder(RewardOccasion occasion) {
            this.occasion = Objects.requireNonNull(occasion, "occasion");
        }

        /**
         * Adds a participant.
         *
         * @param name         what {@code {player}} reads for it - it wins over the last name the server saw
         * @param contribution zero or more
         * @param marks        each one declared by the occasion
         * @throws IllegalArgumentException for a uuid already added, a blank name, a negative contribution or an undeclared mark
         */
        public Builder participant(UUID uuid, String name, BigDecimal contribution, String... marks) {
            Objects.requireNonNull(uuid, "uuid");
            if (participants.containsKey(uuid)) {
                throw new IllegalArgumentException("The scene of '" + occasion.getId() + "' already has the participant " + uuid + " ("
                        + participants.get(uuid).getName() + "): add each participant once, with all its marks.");
            }
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("The participant " + uuid + " of '" + occasion.getId() + "' has no name: {player} reads"
                        + " it - pass the name the plugin knows it by.");
            }
            if (contribution == null || contribution.signum() < 0) {
                throw new IllegalArgumentException("The contribution of " + name + " is " + contribution + ": a contribution is zero or more.");
            }
            Set<String> written = new LinkedHashSet<>(Arrays.asList(marks));
            for (String mark : written) {
                if (!occasion.getMarks().contains(mark)) {
                    throw new IllegalArgumentException("The participant " + name + " carries the mark '" + mark + "', which the occasion '"
                            + occasion.getId() + "' does not declare. It declares " + occasion.getMarks() + ": fix the mark, or add it"
                            + " with RewardOccasion.builder(...).marks(\"" + mark + "\").");
                }
            }
            participants.put(uuid, new Participant(uuid, name, contribution, written));
            return this;
        }

        /**
         * Fills {@code {name}}; {@code value} is written as text.
         *
         * @throws IllegalArgumentException when the occasion does not declare {@code name}
         */
        public Builder variable(String name, Object value) {
            if (!occasion.getVariables().contains(name)) {
                throw new IllegalArgumentException("The scene of '" + occasion.getId() + "' fills {" + name + "}, which the occasion does not"
                        + " declare. It declares " + occasion.getVariables() + ": fix the name, or add it with"
                        + " RewardOccasion.builder(...).variables(\"" + name + "\").");
            }
            variables.put(name, String.valueOf(Objects.requireNonNull(value, "value")));
            return this;
        }

        public RewardScene build() {
            return new RewardScene(this);
        }
    }
}
