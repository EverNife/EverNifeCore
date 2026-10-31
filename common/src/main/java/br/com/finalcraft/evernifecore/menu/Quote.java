package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The totals of a {@link Price} for a quantity, line by line. A {@link TransactionPreEvent} listener changes them with
 * {@link #setTotal} - a tax, a discount, a stock price - and {@link #getOriginal()} keeps what the offer asked.
 */
public final class Quote {

    private final Map<String, PriceLine> lines = new LinkedHashMap<>();
    private final Map<String, BigDecimal> totals = new LinkedHashMap<>();
    private final @Nullable Quote original;

    Quote(List<PriceLine> lines, Map<String, BigDecimal> totals, @Nullable Quote original) {
        for (PriceLine line : lines) {
            this.lines.put(line.getId(), line);
        }
        this.totals.putAll(totals);
        this.original = original;
    }

    /** A copy whose {@link #getOriginal()} is this quote - what a pre-event hands its listeners. */
    Quote editable() {
        return new Quote(new ArrayList<>(lines.values()), totals, this);
    }

    public List<PriceLine> getLines() {
        return Collections.unmodifiableList(new ArrayList<>(lines.values()));
    }

    /** @throws IllegalArgumentException naming the lines this quote has, when it has none {@code lineId} */
    public BigDecimal getTotal(String lineId) {
        return totals.get(require(lineId).getId());
    }

    /**
     * Charges (BUY) or pays (SELL) {@code total} for the line {@code lineId} instead.
     *
     * @throws IllegalArgumentException when the line is not in this quote or the total is negative
     */
    public synchronized void setTotal(String lineId, BigDecimal total) {
        PriceLine line = require(lineId);
        if (total == null || total.signum() < 0) {
            throw new IllegalArgumentException("A total of " + total + " for " + lineId + " is not a price: write zero or more. A discount"
                    + " lowers the total, a refund is a separate give.");
        }
        totals.put(line.getId(), total);
    }

    /** What the offer asked before any listener changed it; this quote itself when nothing could change it. */
    public Quote getOriginal() {
        return original != null ? original : this;
    }

    /** Whether every line totals zero. */
    public boolean isFree() {
        for (BigDecimal total : totals.values()) {
            if (total.signum() != 0) {
                return false;
            }
        }
        return true;
    }

    /** Every line in its own words, comma separated: "250 coins, 2 gold". "free" for an empty price. */
    public FancyText describe(ExecutionContext ctx) {
        List<String> parts = new ArrayList<>();
        for (PriceLine line : lines.values()) {
            parts.add(line.describe(ctx, totals.get(line.getId())).toLegacyString());
        }
        return FancyText.of(parts.isEmpty() ? "free" : String.join(", ", parts));
    }

    private PriceLine require(String lineId) {
        PriceLine line = lines.get(lineId);
        if (line == null) {
            throw new IllegalArgumentException("This quote has no line '" + lineId + "'; it has " + lines.keySet() + ". A currency line is"
                    + " named by the currency's id, an item line by item:<material>.");
        }
        return line;
    }

    @Override
    public String toString() {
        return "Quote" + totals;
    }
}
