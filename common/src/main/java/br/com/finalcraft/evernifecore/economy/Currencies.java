package br.com.finalcraft.evernifecore.economy;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.Variable;
import br.com.finalcraft.evernifecore.logic.Variables;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import br.com.finalcraft.evernifecore.types.TypeRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The registered currencies, by {@code owner:name} id or alias ({@code money}, {@code vault}, {@code finaleconomy:gold}).
 * Registering a currency also registers it as a variable of the same id, so {@code {finaleconomy:gold}} and
 * {@code compare} read its balance. Any thread.
 */
public final class Currencies {

    private static final TypeRegistry<Currency> REGISTRY = TypeRegistry.of(Currency.class, "currency");
    private static final Map<String, Currency> BY_KEY = new ConcurrentHashMap<>();
    /** The variables this class registered, by key: a variable that already existed under the id is not ours to remove. */
    private static final Map<String, ECPluginData> OWN_VARIABLES = new ConcurrentHashMap<>();

    private Currencies() {
    }

    /** The registry behind ids and aliases; one type per currency, without parameters. */
    public static TypeRegistry<Currency> registry() {
        return REGISTRY;
    }

    /** {@code evernifecore:money}, registered or not: the economy plugin behind {@code IEconomyProvider}. */
    public static Currency money() {
        return MoneyCurrency.INSTANCE;
    }

    /** The core's own currencies, registered under {@code core} on enable before any plugin. */
    public static void registerCore(ECPluginData core) {
        if (!REGISTRY.find(MoneyCurrency.ID).isPresent()) {
            register(core, MoneyCurrency.INSTANCE, "vault");
        }
    }

    /**
     * Registers {@code currency} under {@code owner}, the plugin its id names, and the variable of the same id. A
     * variable {@code owner} already has under that id stays as it is.
     *
     * @throws IllegalArgumentException when the id names another plugin
     * @throws IllegalStateException when the id is already a currency
     */
    public static void register(ECPluginData owner, Currency currency, String... aliases) {
        REGISTRY.register(owner, TypeDescriptor.<Currency>of(currency.getId())
                .alias(aliases)
                .describe("The currency " + currency.getDisplayName().getText() + ".")
                .factory(params -> currency)
                .build());
        BY_KEY.put(currency.getId(), currency);
        Optional<TypeDescriptor<Variable<?>>> existing = Variables.registry().find(currency.getId());
        if (!existing.isPresent()) {
            Variables.registry().register(owner, TypeDescriptor.<Variable<?>>of(currency.getId())
                    .describe("The balance in " + currency.getDisplayName().getText() + " of the context's owner.")
                    .factory(params -> currency)
                    .build());
            OWN_VARIABLES.put(currency.getId(), owner);
        }
    }

    /** Takes out every currency {@code owner} registered, with the variables registered for them. */
    public static void unregisterAll(ECPluginData owner) {
        List<String> keys = new ArrayList<>();
        for (TypeDescriptor<Currency> descriptor : REGISTRY.getAll()) {
            if (REGISTRY.getOwner(descriptor).orElse(null) == owner) {
                keys.add(descriptor.getKey());
            }
        }
        for (String key : keys) {
            REGISTRY.unregister(owner, key);
            BY_KEY.remove(key);
            if (OWN_VARIABLES.remove(key, owner)) {
                Variables.registry().unregister(owner, key);
            }
        }
    }

    /** By id, or by a short name or alias that only one currency has. */
    public static Optional<Currency> find(String idOrAlias) {
        return REGISTRY.find(idOrAlias).map(descriptor -> BY_KEY.get(descriptor.getKey()));
    }

    /** @throws IllegalArgumentException naming the id asked, the registered ones, and how to register one */
    public static Currency require(String idOrAlias) {
        return find(idOrAlias).orElseThrow(() -> new IllegalArgumentException("No currency '" + idOrAlias + "' is registered."
                + " Registered: " + getAll().stream().map(Currency::getId).collect(Collectors.toList()) + ". A plugin registers its"
                + " own with Currencies.register(owner, currency); a short name works only when one currency has it."));
    }

    /** In registration order. */
    public static Collection<Currency> getAll() {
        List<Currency> all = new ArrayList<>();
        for (TypeDescriptor<Currency> descriptor : REGISTRY.getAll()) {
            Currency currency = BY_KEY.get(descriptor.getKey());
            if (currency != null) {
                all.add(currency);
            }
        }
        return all;
    }
}
