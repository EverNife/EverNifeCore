package br.com.finalcraft.evernifecore.booster;

import br.com.finalcraft.evernifecore.fancytext.FancyText;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/** The multiplier of one gain for one context, the boosters it came from and the join that made it. Immutable. */
public final class BoosterResult {

    private final String variableId;
    private final BigDecimal multiplier;
    private final List<Booster> parcels;
    private final BoosterJoin join;

    BoosterResult(String variableId, BigDecimal multiplier, List<Booster> parcels, BoosterJoin join) {
        this.variableId = variableId;
        this.multiplier = multiplier;
        this.parcels = Collections.unmodifiableList(parcels);
        this.join = join;
    }

    public String getVariableId() {
        return variableId;
    }

    /** {@code ONE} when no booster counts. */
    public BigDecimal getMultiplier() {
        return multiplier;
    }

    /** The active boosters joined, in source order. */
    public List<Booster> getParcels() {
        return parcels;
    }

    public BoosterJoin getJoin() {
        return join;
    }

    /** {@code money x1.75 (sum-bonus cap 3): x1.5 from ..., +0.25 from ...}, one parcel per line. */
    public FancyText explain() {
        StringBuilder text = new StringBuilder(variableId).append(" x").append(multiplier.stripTrailingZeros().toPlainString())
                .append(" (").append(join).append(")");
        if (parcels.isEmpty()) {
            text.append(": no booster");
        }
        for (Booster parcel : parcels) {
            text.append("\n - ").append(parcel);
        }
        return FancyText.of(text.toString());
    }

    @Override
    public String toString() {
        return explain().getText();
    }
}
