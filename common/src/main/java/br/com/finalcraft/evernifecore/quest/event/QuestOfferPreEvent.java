package br.com.finalcraft.evernifecore.quest.event;

import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.quest.QuestEngine;
import br.com.finalcraft.evernifecore.quest.QuestId;
import br.com.finalcraft.evernifecore.quest.QuestState;

/** The quest is about to be offered. Cancelling it keeps it unoffered. */
public class QuestOfferPreEvent extends QuestEvent implements ECCancellable {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(QuestOfferPreEvent.class);
    }

    public QuestOfferPreEvent(QuestEngine engine, QuestId questId, ProgressOwner owner, Caller caller, QuestState state) {
        super(engine, questId, owner, caller, state);
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
