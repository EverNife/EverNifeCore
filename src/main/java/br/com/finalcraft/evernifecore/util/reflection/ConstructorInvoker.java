package br.com.finalcraft.evernifecore.util.reflection;

public interface ConstructorInvoker {
    /**
     * Invoke a constructor for a specific class.
     *
     * @param arguments - the arguments to pass to the constructor.
     * @return The constructed object.
     */
    Object invoke(Object... arguments);
}
