package br.com.finalcraft.evernifecore.util.reflection;

import java.lang.reflect.Method;

/**
 * An interface for invoking a specific method.
 *
 *  @param <T> - field type.
 */
public interface MethodInvoker<T> {
    /**
     * Invoke a method on a specific target object.
     *
     * @param target    - the target object, or NULL for a static method.
     * @param arguments - the arguments to pass to the method.
     * @return The return value, or NULL if is void.
     */
    T invoke(Object target, Object... arguments);

    /**
     *
     * @return The return Method of this invoker.
     */
    Method get();
}
