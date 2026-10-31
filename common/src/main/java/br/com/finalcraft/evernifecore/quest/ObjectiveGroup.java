package br.com.finalcraft.evernifecore.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/** The objectives of a stage and how many of them close it: all, any, or at least N. Immutable. */
public final class ObjectiveGroup {

    public enum Mode { ALL, ANY, AT_LEAST }

    private final Mode mode;
    private final int count;
    private final List<Objective> objectives;

    private ObjectiveGroup(Mode mode, int count, List<Objective> objectives) {
        if (objectives.isEmpty()) {
            throw new IllegalArgumentException("A stage needs at least one objective.");
        }
        this.mode = mode;
        this.count = count;
        this.objectives = Collections.unmodifiableList(new ArrayList<>(objectives));
    }

    public static ObjectiveGroup all(List<Objective> objectives) {
        return new ObjectiveGroup(Mode.ALL, objectives.size(), objectives);
    }

    public static ObjectiveGroup any(List<Objective> objectives) {
        return new ObjectiveGroup(Mode.ANY, 1, objectives);
    }

    /** @throws IllegalArgumentException when {@code count} is not between 1 and the number of objectives */
    public static ObjectiveGroup atLeast(int count, List<Objective> objectives) {
        if (count < 1 || count > objectives.size()) {
            throw new IllegalArgumentException("at-least " + count + " of " + objectives.size() + " objectives can never close the stage:"
                    + " write a count between 1 and " + objectives.size() + ".");
        }
        return new ObjectiveGroup(Mode.AT_LEAST, count, objectives);
    }

    public Mode getMode() {
        return mode;
    }

    /** How many done objectives close the stage. */
    public int getCount() {
        return count;
    }

    public List<Objective> getObjectives() {
        return objectives;
    }

    /** Whether the objectives {@code done} accepts close this group. */
    public boolean isClosedBy(Predicate<Objective> done) {
        int closed = 0;
        for (Objective objective : objectives) {
            if (done.test(objective)) {
                closed++;
            }
        }
        return closed >= count;
    }
}
