package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.types.PendingDefinition;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import br.com.finalcraft.evernifecore.types.TypeRegistry;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.ZoneId;

/** The cost types. A cost is a condition too: {@link #register} puts it in both registries, so {@code all} can only look at it. */
public final class Costs {

    private static final TypeRegistry<Cost> REGISTRY = TypeRegistry.of(Cost.class, "cost");

    private Costs() {
    }

    public static TypeRegistry<Cost> registry() {
        return REGISTRY;
    }

    /** Registers {@code descriptor} as a cost and as a condition under {@code owner}. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(ECPluginData owner, TypeDescriptor<Cost> descriptor) {
        REGISTRY.register(owner, descriptor);
        //a factory that builds a Cost builds a Condition: the one descriptor serves both registries
        Conditions.registry().register(owner, (TypeDescriptor) descriptor);
    }

    /** The cost {@code definition} is: itself once resolved, else one that fails and takes nothing while pending. */
    public static Cost of(PendingDefinition<Cost> definition) {
        return definition.isResolved() ? definition.get().get() : new Pending(definition);
    }

    /** As {@link Conditions#parse}, for a cost. */
    public static Cost parse(ECPluginData caller, JsonNode node, ZoneId zone) throws TypeRegistryException {
        return of(REGISTRY.parseLater(caller, node, zone));
    }

    private static final class Pending implements Cost {
        private final PendingDefinition<Cost> definition;

        Pending(PendingDefinition<Cost> definition) {
            this.definition = definition;
        }

        @Override
        public ConditionResult test(ExecutionContext ctx) {
            return definition.isResolved() ? Conditions.test(definition.get().get(), ctx)
                    : ConditionResult.fail(FancyText.of(String.valueOf(definition.getCause())));
        }

        @Override
        public boolean take(ExecutionContext ctx) {
            return definition.isResolved() && definition.get().get().take(ctx);
        }

        @Override
        public void refund(ExecutionContext ctx) {
            if (definition.isResolved()) {
                definition.get().get().refund(ctx);
            }
        }

        @Override
        public FancyText describe(ExecutionContext ctx) {
            return definition.isResolved() ? definition.get().get().describe(ctx) : FancyText.of("pending " + definition.getRaw());
        }

        @Override
        public String toString() {
            return definition.isResolved() ? definition.get().get().toString() : "pending " + definition.getRaw();
        }
    }
}
