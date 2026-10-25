package br.com.finalcraft.evernifecore.logic;

import java.math.BigDecimal;

/** One share of {@link ExecutionContext#getMultiplier}: a booster, a rank bonus. Registered with {@link Multipliers#register}. */
public interface MultiplierProvider {

    /** The factor this provider applies to {@code variableId} for {@code ctx}; {@code ONE} when it does not apply. */
    BigDecimal getMultiplier(ExecutionContext ctx, String variableId);
}
