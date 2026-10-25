package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.logic.core.Compositions;
import br.com.finalcraft.evernifecore.types.PendingDefinition;
import br.com.finalcraft.evernifecore.types.TypeRegistry;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/** The condition types, the compositions as Java constructors, and the one safe way to test a condition. */
public final class Conditions {

    private static final TypeRegistry<Condition> REGISTRY = TypeRegistry.of(Condition.class, "condition");

    private Conditions() {
    }

    public static TypeRegistry<Condition> registry() {
        return REGISTRY;
    }

    /** Passes when every child passes; fails with every child's failure. */
    public static Condition all(Condition... children) {
        return Compositions.all(Arrays.asList(children));
    }

    /** Passes when one child passes; fails with every failure when none does. */
    public static Condition any(Condition... children) {
        return Compositions.any(Arrays.asList(children));
    }

    /** Passes when no child passes; fails naming the ones that did. */
    public static Condition none(Condition... children) {
        return Compositions.none(Arrays.asList(children));
    }

    /** Passes when at least {@code count} children pass; fails with the failures otherwise. */
    public static Condition atLeast(int count, Condition... children) {
        return Compositions.atLeast(count, Arrays.asList(children));
    }

    /** Always passes; a failure of {@code child} is carried as a note. */
    public static Condition optional(Condition child) {
        return Compositions.optional(child);
    }

    /**
     * Tests {@code condition} for {@code ctx}: a condition that throws counts as failed with the exception's text and a
     * {@code CONDITION_THREW} audit entry - it never breaks what tests it. The result names who answered.
     */
    public static ConditionResult test(Condition condition, ExecutionContext ctx) {
        try {
            ConditionResult result = condition.test(ctx);
            if (result == null) {
                throw new IllegalStateException(condition + " answered null instead of a ConditionResult");
            }
            return result.answeredBy(condition);
        } catch (RuntimeException threw) {
            ctx.getAudit().add(ctx, AuditKind.CONDITION_THREW, condition + " threw instead of answering: " + threw, null, threw);
            return ConditionResult.fail(FancyText.of("Could not test " + condition + ": " + threw.getMessage())).answeredBy(condition);
        }
    }

    /** The condition {@code definition} is: itself once resolved, else one that fails with why it is still pending. */
    public static Condition of(PendingDefinition<Condition> definition) {
        return definition.isResolved() ? definition.get().get() : new Pending(definition);
    }

    /**
     * Reads {@code node} as one condition of {@code caller}; a type nobody registered yet keeps it pending (it fails
     * with the cause until the type arrives).
     *
     * @throws TypeRegistryException for every other unreadable definition
     */
    public static Condition parse(ECPluginData caller, JsonNode node, ZoneId zone) throws TypeRegistryException {
        return of(REGISTRY.parseLater(caller, node, zone));
    }

    private static final class Pending implements Condition {
        private final PendingDefinition<Condition> definition;

        Pending(PendingDefinition<Condition> definition) {
            this.definition = definition;
        }

        @Override
        public ConditionResult test(ExecutionContext ctx) {
            if (definition.isResolved()) {
                return Conditions.test(definition.get().get(), ctx);
            }
            return ConditionResult.fail(FancyText.of(String.valueOf(definition.getCause())));
        }

        @Override
        public Set<EventSource> getNeeds() {
            return definition.isResolved() ? definition.get().get().getNeeds() : Collections.<EventSource>emptySet();
        }

        @Override
        public String toString() {
            return definition.isResolved() ? definition.get().get().toString() : "pending " + definition.getRaw();
        }
    }
}
