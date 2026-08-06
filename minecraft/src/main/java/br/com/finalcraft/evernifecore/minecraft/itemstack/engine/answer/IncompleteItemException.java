package br.com.finalcraft.evernifecore.minecraft.itemstack.engine.answer;

import jakarta.annotation.Nonnull;

/**
 * Thrown when a recipe could not be built whole: the runtime refused part of it, or the block it
 * came from carried lines that could not be read.
 *
 * <p>The message names everything missing, and the call that hands back the reduced item instead.</p>
 */
public class IncompleteItemException extends RuntimeException {

    public IncompleteItemException(@Nonnull String message) {
        super(message);
    }

}
