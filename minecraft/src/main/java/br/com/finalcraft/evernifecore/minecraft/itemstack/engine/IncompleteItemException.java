package br.com.finalcraft.evernifecore.minecraft.itemstack.engine;

import jakarta.annotation.Nonnull;

/** Thrown by {@link BuiltItem#requireComplete()} when the runtime could not build the whole item. */
public class IncompleteItemException extends RuntimeException {

    public IncompleteItemException(@Nonnull String message) {
        super(message);
    }

}
