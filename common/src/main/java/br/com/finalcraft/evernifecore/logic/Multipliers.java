package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.audit.LogicTrace;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** The registered {@link MultiplierProvider}s; {@link ExecutionContext#getMultiplier} is their product. */
public final class Multipliers {

    private static final List<Registered> PROVIDERS = new CopyOnWriteArrayList<>();

    private Multipliers() {
    }

    /** Adds {@code provider} under {@code owner}; it leaves with {@code owner}'s shutdown. */
    public static void register(ECPluginData owner, MultiplierProvider provider) {
        PROVIDERS.add(new Registered(owner, provider));
    }

    public static void unregisterAll(ECPluginData owner) {
        PROVIDERS.removeIf(registered -> registered.owner == owner);
    }

    /** The product of every provider's factor, each one traced; {@code ONE} without providers. */
    static BigDecimal of(ExecutionContext ctx, String variableId) {
        BigDecimal product = BigDecimal.ONE;
        for (Registered registered : PROVIDERS) {
            BigDecimal factor = registered.provider.getMultiplier(ctx, variableId);
            if (factor != null) {
                product = product.multiply(factor);
                LogicTrace.publish(ctx, () -> "multiplier " + variableId + ": " + registered.provider + " x" + factor.toPlainString());
            }
        }
        return product;
    }

    private static final class Registered {
        final ECPluginData owner;
        final MultiplierProvider provider;

        Registered(ECPluginData owner, MultiplierProvider provider) {
            this.owner = owner;
            this.provider = provider;
        }
    }
}
