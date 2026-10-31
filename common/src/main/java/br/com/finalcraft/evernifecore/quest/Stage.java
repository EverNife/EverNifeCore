package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.logic.EffectList;
import br.com.finalcraft.evernifecore.logic.Effects;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.Objects;

/** A node of a quest's graph: its objectives, what closing it applies and where it leads. Immutable. */
public final class Stage {

    private final String id;
    private final ObjectiveGroup objectives;
    private final EffectList onComplete;
    private final Next then;

    public Stage(String id, ObjectiveGroup objectives, @Nullable EffectList onComplete, @Nullable Next then) {
        this.id = Objects.requireNonNull(id, "id");
        this.objectives = Objects.requireNonNull(objectives, "objectives");
        this.onComplete = onComplete == null ? Effects.of(Collections.emptyList()) : onComplete;
        this.then = then == null ? Next.NEXT_IN_LIST : then;
    }

    /** Stable: progress is kept under it across definition versions. */
    public String getId() {
        return id;
    }

    public ObjectiveGroup getObjectives() {
        return objectives;
    }

    public @Nullable Objective getObjective(String objectiveId) {
        for (Objective objective : objectives.getObjectives()) {
            if (objective.getId().equals(objectiveId)) {
                return objective;
            }
        }
        return null;
    }

    public EffectList getOnComplete() {
        return onComplete;
    }

    public Next getThen() {
        return then;
    }

    @Override
    public String toString() {
        return id + objectives.getObjectives();
    }
}
