package br.com.finalcraft.evernifecore.quest.event;

import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.quest.QuestEngine;
import br.com.finalcraft.evernifecore.quest.QuestId;
import br.com.finalcraft.evernifecore.quest.QuestState;
import jakarta.annotation.Nullable;

import java.util.UUID;

/** An objective is about to progress. Cancelling it counts nothing; {@link #setDelta} changes what counts. */
public class ObjectiveProgressPreEvent extends QuestEvent implements ECCancellable {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(ObjectiveProgressPreEvent.class);
    }

    private final String objectiveId;
    private volatile long delta;
    private final UUID actor;

    public ObjectiveProgressPreEvent(QuestEngine engine, QuestId questId, ProgressOwner owner, Caller caller, QuestState state, String objectiveId, long delta, @Nullable UUID actor) {
        super(engine, questId, owner, caller, state);
        this.objectiveId = objectiveId;
        this.delta = delta;
        this.actor = actor;
    }

    /** The objective about to progress. */
    public String getObjectiveId() {
        return objectiveId;
    }

    /** What will be added to its count. */
    public long getDelta() {
        return delta;
    }

    public void setDelta(long delta) {
        this.delta = delta;
    }

    /** The player whose action it was; null for progress through the API. */
    public @Nullable UUID getActor() {
        return actor;
    }

    private volatile boolean cancelled;

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
