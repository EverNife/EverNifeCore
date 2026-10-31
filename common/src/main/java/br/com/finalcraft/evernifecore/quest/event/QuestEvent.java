package br.com.finalcraft.evernifecore.quest.event;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.quest.QuestEngine;
import br.com.finalcraft.evernifecore.quest.QuestId;
import br.com.finalcraft.evernifecore.quest.QuestState;

import java.util.Objects;

/**
 * Something happened to one owner's instance of one quest. Posted on the global bus only when somebody listens, on the
 * thread of whatever made the change (the action's, a command's, the sweep's). {@link #getState()} is a snapshot taken
 * when the event was built; changing it changes nothing.
 */
public abstract class QuestEvent extends ECEvent implements IECEvent {

    private final QuestEngine engine;
    private final QuestId questId;
    private final ProgressOwner owner;
    private final Caller caller;
    private final QuestState state;

    protected QuestEvent(QuestEngine engine, QuestId questId, ProgressOwner owner, Caller caller, QuestState state) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.questId = Objects.requireNonNull(questId, "questId");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.caller = Objects.requireNonNull(caller, "caller");
        this.state = Objects.requireNonNull(state, "state");
    }

    /** The engine that holds the quest; its owner is the plugin. */
    public QuestEngine getEngine() {
        return engine;
    }

    public QuestId getQuestId() {
        return questId;
    }

    /** Who the instance belongs to: a player, a group, the server... */
    public ProgressOwner getOwner() {
        return owner;
    }

    /** {@code <plugin>:<quest>[/stage:<stage>][/objective:<id>]} - what the audit and a trace name. */
    public Caller getCaller() {
        return caller;
    }

    public QuestState getState() {
        return state;
    }
}
