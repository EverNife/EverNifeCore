package br.com.finalcraft.evernifecore.menu;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What {@link Transactions#sellAll} sold: one result per offer the inventory matched, and the sums. Immutable. */
public final class SellAllResult {

    private final List<TransactionResult> results;

    SellAllResult(List<TransactionResult> results) {
        this.results = Collections.unmodifiableList(new ArrayList<>(results));
    }

    /** One per offer matched, in the order the inventory met them - failures included. */
    public List<TransactionResult> getResults() {
        return results;
    }

    /** The units sold, over every offer that went through. */
    public int getUnits() {
        int units = 0;
        for (TransactionResult result : results) {
            if (result.isSuccess()) {
                units += result.getQuantity();
            }
        }
        return units;
    }

    /** What was paid, per price line id (a currency id), over every offer that went through. */
    public Map<String, BigDecimal> getTotals() {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (TransactionResult result : results) {
            if (result.isSuccess()) {
                for (PriceLine line : result.getQuote().getLines()) {
                    totals.merge(line.getId(), result.getQuote().getTotal(line.getId()), BigDecimal::add);
                }
            }
        }
        return totals;
    }

    /** The offers that went through. */
    public List<Offer> getOffers() {
        List<Offer> offers = new ArrayList<>();
        for (TransactionResult result : results) {
            if (result.isSuccess()) {
                offers.add(result.getOffer());
            }
        }
        return offers;
    }

    @Override
    public String toString() {
        return "SellAllResult{" + getUnits() + " units, " + getTotals() + "}";
    }
}
