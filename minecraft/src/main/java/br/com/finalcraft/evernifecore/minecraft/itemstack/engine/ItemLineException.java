package br.com.finalcraft.evernifecore.minecraft.itemstack.engine;

import jakarta.annotation.Nonnull;

/**
 * What a part throws when the text it was handed is not a value it can hold.
 *
 * <p>The message is written for whoever wrote the line: it states the shape the value takes and
 * shows one that works, so the exception alone is enough to fix the file.</p>
 */
public class ItemLineException extends RuntimeException {

    public ItemLineException(@Nonnull String message) {
        super(message);
    }

    /** The house shape: what is wrong, what the value looks like, and one example that works. */
    @Nonnull
    public static ItemLineException expecting(@Nonnull String argument, @Nonnull String shape,
                                             @Nonnull String example) {
        return new ItemLineException("'" + argument + "' does not fit. The value is " + shape
                + " - for example '" + example + "'.");
    }

}
