package br.com.finalcraft.evernifecore.quest.event;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.quest.QuestEngine;
import br.com.finalcraft.evernifecore.quest.QuestId;
import br.com.finalcraft.evernifecore.quest.QuestState;
import jakarta.annotation.Nullable;

import java.util.UUID;

/** An objective progressed. */
public class ObjectiveProgressEvent extends QuestEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(ObjectiveProgressEvent.class);
    }

    private final String objectiveId;
    private final long delta;
    private final UUID actor;

    public ObjectiveProgressEvent(QuestEngine engine, QuestId questId, ProgressOwner owner, Caller caller, QuestState state, String objectiveId, long delta, @Nullable UUID actor) {
        super(engine, questId, owner, caller, state);
        this.objectiveId = objectiveId;
        this.delta = delta;
        this.actor = actor;
    }

    /** The objective that progressed. */
    public String getObjectiveId() {
        return objectiveId;
    }

    /** What was added to its count. */
    public long getDelta() {
        return delta;
    }

    /** The player whose action it was; null for progress through the API. */
    public @Nullable UUID getActor() {
        return actor;
    }
}
