package br.com.finalcraft.evernifecore.logic.delivery;

import br.com.finalcraft.evernifecore.economy.Currency;
import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;

/**
 * Where an effect's {@code to:} sends what it gives: {@code evernifecore:inventory} (registered by each platform),
 * {@code evernifecore:pending}, a plugin's own ({@code finalcolonies:warehouse}). Registered in
 * {@link DeliveryDestinations}. Any thread: a destination that needs the player's thread dispatches itself.
 */
public interface DeliveryDestination {

    /** {@code owner:name}, the plugin that registers it first. */
    String getId();

    /**
     * Hands {@code item} to the context's player.
     *
     * @return what did NOT fit, or null when everything was delivered
     */
    @Nullable ItemSpec deliverItem(ExecutionContext ctx, ItemSpec item);

    /** Gives {@code amount} of {@code currency}; the default pays the context owner's wallet. */
    default EcoResponse deliverAmount(ExecutionContext ctx, Currency currency, BigDecimal amount) {
        return CurrencyOwner.of(ctx.getOwner())
                .map(owner -> currency.give(owner, amount, ctx.getCaller()))
                .orElseGet(() -> EcoResponse.unsupportedOwner(amount, currency.getId() + " has no wallet for " + ctx.getOwner() + "."));
    }
}
