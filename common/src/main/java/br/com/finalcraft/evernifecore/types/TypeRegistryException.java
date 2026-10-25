package br.com.finalcraft.evernifecore.types;

/**
 * A definition a {@link TypeRegistry} cannot read: an unknown or ambiguous type, a missing or unreadable parameter, a
 * type this platform does not support. The message names what was read, what was expected and how to fix it.
 * Unchecked, so a factory lambda may throw it.
 */
public class TypeRegistryException extends RuntimeException {

    public TypeRegistryException(String message) {
        super(message);
    }

    public TypeRegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
