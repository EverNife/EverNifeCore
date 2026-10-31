package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import br.com.finalcraft.evernifecore.types.TypeRegistry;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The objective types, as a {@link TypeRegistry} of kind {@code objective}: validation, pending types and the reference come from it. */
public final class ObjectiveTypes {

    /** The keys of an objective node that belong to the objective; every other key is its type's parameter. */
    public static final List<String> OBJECTIVE_KEYS = Collections.unmodifiableList(Arrays.asList("id", "goal", "mode", "conditions",
            "costs", "then", "on-match", "hidden", "description", "platforms"));

    private static final TypeRegistry<ObjectiveFilter> REGISTRY = TypeRegistry.of(ObjectiveFilter.class, "objective")
            .reserveKeys(OBJECTIVE_KEYS.toArray(new String[0]));

    private ObjectiveTypes() {
    }

    public static TypeRegistry<ObjectiveFilter> registry() {
        return REGISTRY;
    }

    /**
     * Registers {@code type} under {@code owner}; it leaves with {@code owner}'s shutdown. Any thread.
     *
     * @throws IllegalArgumentException when the type cancels the native outcome of a source that has no event before it
     */
    public static <C> void register(ECPluginData owner, ObjectiveType<C> type) {
        if (type.cancelsNative()) {
            for (EventSource source : type.getSources()) {
                if (type.getPreEvent(source) == null) {
                    throw new IllegalArgumentException("Objective type '" + type.getKey() + "' declares cancelsNative, but the source " + source
                            + " has no pre-outcome event on this platform. Register the source with a pre event (answer getPreEvent), or drop"
                            + " cancelsNative from the type.");
                }
            }
        }
        TypeDescriptor.Builder<ObjectiveFilter> descriptor = TypeDescriptor.<ObjectiveFilter>of(type.getKey())
                .describe(type.getDescription())
                .platforms(type.getPlatforms().toArray(new String[0]))
                .factory(params -> ObjectiveFilter.of(type, type.parseFilter(params)));
        for (ParamSpec param : type.getParams()) {
            descriptor.param(param);
        }
        REGISTRY.register(owner, descriptor.build());
    }
}
