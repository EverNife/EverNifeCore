package br.com.finalcraft.evernifecore.minecraft.itemstack.engine.answer;

import jakarta.annotation.Nonnull;

/**
 * One item-data line that could not be read, and the sentence that tells the admin how to fix it.
 *
 * <p>A bad line costs itself and nothing else: the block it came from is still built, with every
 * other line applied.</p>
 */
public final class ItemLineProblem {

    private final String line;
    private final String reason;

    public ItemLineProblem(@Nonnull String line, @Nonnull String reason) {
        this.line = line;
        this.reason = reason;
    }

    @Nonnull
    public String getLine() {
        return line;
    }

    @Nonnull
    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "'" + line + "': " + reason;
    }

}
