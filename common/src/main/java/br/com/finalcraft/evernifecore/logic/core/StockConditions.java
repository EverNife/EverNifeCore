package br.com.finalcraft.evernifecore.logic.core;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Conditions;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.Variable;
import br.com.finalcraft.evernifecore.logic.Variables;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/** The stock conditions {@code compare} and {@code permission}, and how a written amount reads against a variable's type. */
final class StockConditions {

    private StockConditions() {
    }

    static void register(ECPluginData core) {
        Conditions.registry().register(core, TypeDescriptor.<Condition>of("evernifecore:compare")
                .describe("Compares a variable with an amount: numbers with < <= = >= > !=, texts with = != contains, true/false with = !=.")
                .param(ParamSpec.required("variable", ParamSpec.Kind.ID, "The variable read, e.g. exp-level or finaljobs:level."))
                .param(ParamSpec.required("op", ParamSpec.Kind.STRING, "How to compare.").oneOf("<", "<=", "=", ">=", ">", "!=", "contains"))
                .param(ParamSpec.required("amount", ParamSpec.Kind.STRING, "What the variable is compared with: a number, a text, true or false."))
                .factory(params -> {
                    String variable = params.getString("variable");
                    Optional<String> unsupported = Variables.registry().find(variable).flatMap(Variables.registry()::getUnsupported);
                    if (unsupported.isPresent()) {
                        throw new TypeRegistryException(unsupported.get());
                    }
                    return new Compare(params.getCaller(), variable, params.getString("op"), params.getString("amount"));
                })
                .build());
        Conditions.registry().register(core, TypeDescriptor.<Condition>of("evernifecore:permission")
                .describe("Passes when the player has a permission node.")
                .param(ParamSpec.required("node", ParamSpec.Kind.ID, "The permission node, e.g. shop.vip."))
                .factory(params -> {
                    String node = params.getString("node");
                    return new Condition() {
                        @Override
                        public ConditionResult test(ExecutionContext ctx) {
                            FPlayer player = ctx.getPlayer();
                            return ConditionResult.of(player != null && player.hasPermission(node), FancyText.of("Needs the permission " + node + "."));
                        }

                        @Override
                        public String toString() {
                            return "permission(" + node + ")";
                        }
                    };
                })
                .build());
    }

    /** The variable {@code id} names for {@code caller}; the message says how to register it when nobody did. */
    static Variable<?> variable(ECPluginData caller, String id) {
        return Variables.find(caller, id).orElseThrow(() -> new IllegalStateException("No variable '" + id + "' without parameters is"
                + " registered for " + caller.getMetaInfo().getName() + ". The plugin that owns it registers it in Variables.registry()."));
    }

    /** {@code amount} read as a value of {@code type}: a number (a duration in seconds), a text, or true/false. */
    static Object read(Class<?> type, String amount) {
        if (type == Boolean.class) {
            if (!amount.equalsIgnoreCase("true") && !amount.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException("'" + amount + "' is not true or false");
            }
            return Boolean.valueOf(amount);
        }
        if (type == String.class) {
            return amount;
        }
        try {
            BigDecimal number = new BigDecimal(amount.trim());
            return type == Duration.class ? Duration.ofMillis(number.movePointRight(3).longValue()) : number;
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("'" + amount + "' is not a number");
        }
    }

    /** A number or a duration as a number (a duration in seconds); null for any other value. */
    static @Nullable BigDecimal number(@Nullable Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        if (value instanceof Duration) {
            return BigDecimal.valueOf(((Duration) value).toMillis()).movePointLeft(3);
        }
        return null;
    }

    private static final class Compare implements Condition {
        private final ECPluginData caller;
        private final String variableId;
        private final String op;
        private final String amount;
        private volatile Variable<?> variable;

        Compare(ECPluginData caller, String variableId, String op, String amount) {
            this.caller = caller;
            this.variableId = variableId;
            this.op = op;
            this.amount = amount;
        }

        @Override
        public ConditionResult test(ExecutionContext ctx) {
            Variable<?> read = variable;
            if (read == null) {
                read = variable(caller, variableId);
                variable = read;
            }
            Object value = read.read(ctx);
            boolean passed;
            BigDecimal left = number(value);
            if (left != null) {
                BigDecimal right = number(StockConditions.read(read.getType() == Duration.class ? Duration.class : BigDecimal.class, amount));
                int order = left.compareTo(right);
                switch (op) {
                    case "<": passed = order < 0; break;
                    case "<=": passed = order <= 0; break;
                    case "=": passed = order == 0; break;
                    case ">=": passed = order >= 0; break;
                    case ">": passed = order > 0; break;
                    case "!=": passed = order != 0; break;
                    default: throw new IllegalArgumentException("'" + op + "' does not compare numbers: use <, <=, =, >=, > or !=");
                }
            } else if (value instanceof Boolean) {
                Object right = StockConditions.read(Boolean.class, amount);
                if (op.equals("=")) {
                    passed = value.equals(right);
                } else if (op.equals("!=")) {
                    passed = !value.equals(right);
                } else {
                    throw new IllegalArgumentException("'" + op + "' does not compare true/false: use = or !=");
                }
            } else {
                String text = String.valueOf(value);
                if (op.equals("=")) {
                    passed = text.equals(amount);
                } else if (op.equals("!=")) {
                    passed = !text.equals(amount);
                } else if (op.equals("contains")) {
                    passed = text.contains(amount);
                } else {
                    throw new IllegalArgumentException("'" + op + "' does not compare texts: use =, != or contains");
                }
            }
            return ConditionResult.of(passed, FancyText.of(variableId + " is " + format(value) + ", and it must be " + op + " " + amount + "."));
        }

        private static String format(Object value) {
            BigDecimal number = number(value);
            return number == null ? String.valueOf(value) : number.signum() == 0 ? "0" : number.stripTrailingZeros().toPlainString();
        }

        @Override
        public Set<EventSource> getNeeds() {
            return Variables.registry().find(variableId).map(descriptor -> descriptor.getNeeds()).orElse(Collections.<EventSource>emptySet());
        }

        @Override
        public String toString() {
            return "compare(" + variableId + " " + op + " " + amount + ")";
        }
    }
}
