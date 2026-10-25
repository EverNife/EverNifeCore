package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.types.TypeRegistry;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Optional;

/** The variable types. A type without parameters is also what {@code {id}} reads in a text. */
public final class Variables {

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final TypeRegistry<Variable<?>> REGISTRY = TypeRegistry.of((Class<Variable<?>>) (Class) Variable.class, "variable");

    private Variables() {
    }

    public static TypeRegistry<Variable<?>> registry() {
        return REGISTRY;
    }

    /**
     * {@code owner}'s own variables, one value per {@link ProgressOwner}, kept in {@code storage}: the same instance for
     * the same plugin and storage.
     */
    public static CustomVariables custom(ECPluginData owner, ECStorage storage) {
        return CustomVariables.of(owner, storage);
    }

    /** {@code owner}'s own variables kept in memory only - they start over with the process. */
    public static CustomVariables custom(ECPluginData owner) {
        return CustomVariables.of(owner, null);
    }

    /**
     * The variable {@code id} names for {@code caller} when its type takes no required parameter; empty when no such
     * type is registered, the name is ambiguous, or the type needs parameters.
     */
    public static Optional<Variable<?>> find(ECPluginData caller, String id) {
        ObjectNode node = JsonNodeFactory.instance.objectNode().put(TypeRegistry.TYPE_FIELD, id);
        try {
            return Optional.<Variable<?>>of(REGISTRY.parse(caller, node));
        } catch (TypeRegistryException notReadable) {
            return Optional.empty();
        }
    }
}
