package br.com.finalcraft.evernifecore.logic.core;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Conditions;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.ParsedParams;
import br.com.finalcraft.evernifecore.types.PendingDefinition;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The condition trees: {@code all}, {@code any}, {@code none}, {@code at-least}, {@code optional}. */
public final class Compositions {

    private Compositions() {
    }

    public static Condition all(List<Condition> children) {
        return new Composite("all", children, 0);
    }

    public static Condition any(List<Condition> children) {
        return new Composite("any", children, 0);
    }

    public static Condition none(List<Condition> children) {
        return new Composite("none", children, 0);
    }

    /** @throws IllegalArgumentException when {@code count} is below 1 or above the children */
    public static Condition atLeast(int count, List<Condition> children) {
        if (count < 1 || count > children.size()) {
            throw new IllegalArgumentException("at-least needs a count between 1 and its " + children.size() + " conditions, not "
                    + count + ": with more it never passes, with less it always does.");
        }
        return new Composite("at-least", children, count);
    }

    public static Condition optional(Condition child) {
        return new Composite("optional", Collections.singletonList(child), 0);
    }

    /** Registers the five under {@code core} in {@link Conditions#registry()}. */
    static void register(ECPluginData core) {
        ParamSpec children = ParamSpec.required("conditions", ParamSpec.Kind.NODE_LIST, "The conditions combined.").nested(Conditions.registry());
        Conditions.registry().register(core, TypeDescriptor.<Condition>of("evernifecore:all")
                .describe("Passes when every condition passes; a failure lists every condition that failed.")
                .param(children)
                .factory(params -> all(children(params)))
                .build());
        Conditions.registry().register(core, TypeDescriptor.<Condition>of("evernifecore:any")
                .describe("Passes when at least one condition passes.")
                .param(children)
                .factory(params -> any(children(params)))
                .build());
        Conditions.registry().register(core, TypeDescriptor.<Condition>of("evernifecore:none")
                .describe("Passes when no condition passes.")
                .param(children)
                .factory(params -> none(children(params)))
                .build());
        Conditions.registry().register(core, TypeDescriptor.<Condition>of("evernifecore:at-least")
                .describe("Passes when at least count of the conditions pass.")
                .param(ParamSpec.required("count", ParamSpec.Kind.NUMBER, "How many conditions must pass."))
                .param(children)
                .factory(params -> atLeast(params.getNumber("count").intValueExact(), children(params)))
                .build());
        Conditions.registry().register(core, TypeDescriptor.<Condition>of("evernifecore:optional")
                .describe("Always passes; when its condition fails, the failure is shown as a note.")
                .param(ParamSpec.required("condition", ParamSpec.Kind.NODE, "The condition reported.").nested(Conditions.registry()))
                .factory(params -> optional(Conditions.of(params.<Condition>getPending("condition"))))
                .build());
    }

    private static List<Condition> children(ParsedParams params) {
        List<Condition> children = new ArrayList<>();
        for (PendingDefinition<Condition> child : params.<Condition>getPendingNodes("conditions")) {
            children.add(Conditions.of(child));
        }
        return children;
    }

    private static final class Composite implements Condition {
        private final String kind;
        private final List<Condition> children;
        private final int count;

        Composite(String kind, List<Condition> children, int count) {
            this.kind = kind;
            this.children = Collections.unmodifiableList(new ArrayList<>(children));
            this.count = count;
        }

        @Override
        public ConditionResult test(ExecutionContext ctx) {
            List<ConditionResult> passed = new ArrayList<>();
            List<ConditionResult> failed = new ArrayList<>();
            List<Condition> held = new ArrayList<>();
            for (Condition child : children) {
                ConditionResult result = Conditions.test(child, ctx);
                (result.isPassed() ? passed : failed).add(result);
                if (result.isPassed()) {
                    held.add(child);
                }
            }
            switch (kind) {
                case "all":
                    return failed.isEmpty() ? ConditionResult.pass().withNotes(passed) : ConditionResult.failAll(failed);
                case "any":
                    return passed.isEmpty() ? ConditionResult.failAll(failed) : ConditionResult.pass().withNotes(passed);
                case "none":
                    if (passed.isEmpty()) {
                        return ConditionResult.pass();
                    }
                    List<ConditionResult> offending = new ArrayList<>();
                    for (Condition child : held) {
                        offending.add(ConditionResult.fail(FancyText.of("This must not hold, and it does: " + child)).answeredBy(child));
                    }
                    return ConditionResult.failAll(offending);
                case "at-least":
                    return passed.size() >= count ? ConditionResult.pass().withNotes(passed) : ConditionResult.failAll(failed);
                default: //optional
                    return ConditionResult.pass().withNotes(passed.isEmpty() ? failed : passed);
            }
        }

        @Override
        public Set<EventSource> getNeeds() {
            Set<EventSource> union = new LinkedHashSet<>();
            for (Condition child : children) {
                union.addAll(child.getNeeds());
            }
            return union;
        }

        @Override
        public String toString() {
            return kind + (count > 0 ? "(" + count + " of " : "(") + children + ")";
        }
    }
}
