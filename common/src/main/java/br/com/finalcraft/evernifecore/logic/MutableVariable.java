package br.com.finalcraft.evernifecore.logic;

/** A {@link Variable} the {@code set} and {@code add} effects can write. Written on the target's thread. */
public interface MutableVariable<T> extends Variable<T> {

    void set(ExecutionContext ctx, T value);
}
