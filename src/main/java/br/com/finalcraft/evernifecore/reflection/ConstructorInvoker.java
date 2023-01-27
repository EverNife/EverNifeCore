package br.com.finalcraft.evernifecore.reflection;

public interface ConstructorInvoker<T> {
    /**
     * Invoke a constructor for a specific class.
     *
     * @param arguments - the arguments to pass to the constructor.
     * @return The constructed object.
     */
    T invoke(Object... arguments);
}
