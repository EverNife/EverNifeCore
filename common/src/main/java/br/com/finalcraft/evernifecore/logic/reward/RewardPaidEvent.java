package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One member was paid by {@link Rewards#pay}: its effects applied (online) or kept as a pending delivery (offline). A
 * forfeited share posts nothing. Posted once the member's effects ran, on the thread they ran on.
 */
public class RewardPaidEvent extends ECEvent implements IECEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(RewardPaidEvent.class);
    }

    private final Caller caller;
    private final ProgressOwner owner;
    private final UUID member;
    private final BigDecimal share;
    private final RewardRule rule;
    private final boolean delivered;

    public RewardPaidEvent(Caller caller, ProgressOwner owner, UUID member, BigDecimal share, RewardRule rule, boolean delivered) {
        this.caller = caller;
        this.owner = owner;
        this.member = member;
        this.share = share;
        this.rule = rule;
        this.delivered = delivered;
    }

    public Caller getCaller() {
        return caller;
    }

    /** Whose reward it was. */
    public ProgressOwner getOwner() {
        return owner;
    }

    public UUID getMember() {
        return member;
    }

    /** What the rule's effects were multiplied by for this member. */
    public BigDecimal getShare() {
        return share;
    }

    public RewardRule getRule() {
        return rule;
    }

    /** Whether the member was offline and the share waits as a pending delivery. */
    public boolean isDelivered() {
        return delivered;
    }
}
