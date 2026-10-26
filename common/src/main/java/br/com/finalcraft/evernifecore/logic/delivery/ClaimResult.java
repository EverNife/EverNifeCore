package br.com.finalcraft.evernifecore.logic.delivery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** What one claim did: the deliveries handed out whole, the ones still pending (in part or waiting a plugin), the ones dropped expired. Immutable. */
public final class ClaimResult {

    private final List<Delivery> delivered;
    private final List<Delivery> kept;
    private final List<Delivery> expired;

    ClaimResult(List<Delivery> delivered, List<Delivery> kept, List<Delivery> expired) {
        this.delivered = Collections.unmodifiableList(new ArrayList<>(delivered));
        this.kept = Collections.unmodifiableList(new ArrayList<>(kept));
        this.expired = Collections.unmodifiableList(new ArrayList<>(expired));
    }

    static ClaimResult empty() {
        return new ClaimResult(Collections.<Delivery>emptyList(), Collections.<Delivery>emptyList(), Collections.<Delivery>emptyList());
    }

    public List<Delivery> getDelivered() {
        return delivered;
    }

    /** Still pending after the claim, as they now stand. */
    public List<Delivery> getKept() {
        return kept;
    }

    public List<Delivery> getExpired() {
        return expired;
    }

    @Override
    public String toString() {
        return "ClaimResult{delivered=" + delivered.size() + ", kept=" + kept.size() + ", expired=" + expired.size() + "}";
    }
}
