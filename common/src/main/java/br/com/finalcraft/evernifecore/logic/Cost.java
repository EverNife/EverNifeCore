package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.fancytext.FancyText;

/**
 * A condition that also charges: {@link #test} only looks, {@link #take} takes. A {@link Gate} takes its costs in order
 * and refunds the taken ones in reverse when one does not come out. Taken and refunded on the caller's thread, without
 * blocking I/O.
 */
public interface Cost extends Condition {

    /** Takes the cost, atomically by the implementation's own means; false means nothing came out. */
    boolean take(ExecutionContext ctx);

    /** Gives back what {@link #take} took; best effort - a refund that throws is audited with {@link #describe}. */
    void refund(ExecutionContext ctx);

    /** What the cost is, for a lore or a log line: "100 coins". */
    FancyText describe(ExecutionContext ctx);
}
