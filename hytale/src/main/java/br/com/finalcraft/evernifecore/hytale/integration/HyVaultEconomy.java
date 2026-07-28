package br.com.finalcraft.evernifecore.hytale.integration;

import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.economy.IEconomyProvider;
import net.milkbowl.vault2.economy.Economy;
import net.milkbowl.vault2.economy.EconomyResponse;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The VaultUnlocked economy of a Hytale server behind the shared contract.
 *
 * <p>Same API the Bukkit side uses for vault2, reached through Hytale's own services manager:
 * BigDecimal end to end, a real {@code set}, and every transaction stamped with
 * {@link #TRANSACTION_SOURCE} instead of the empty author the port used to send.</p>
 */
public class HyVaultEconomy implements IEconomyProvider {

    private final Economy economy;

    public HyVaultEconomy(Economy economy) {
        this.economy = economy;
    }

    @Override
    public Economy getHandle() {
        return economy;
    }

    @Override
    public BigDecimal getBalance(UUID playerUUID) {
        BigDecimal balance = economy.balance(TRANSACTION_SOURCE, playerUUID);
        return balance != null ? balance : BigDecimal.ZERO;
    }

    @Override
    public boolean hasEnough(UUID playerUUID, BigDecimal amount) {
        if (amount.signum() <= 0) {
            return true;
        }
        return economy.has(TRANSACTION_SOURCE, playerUUID, amount);
    }

    @Override
    public EcoResponse give(UUID playerUUID, BigDecimal amount) {
        if (amount.signum() < 0) {
            return EcoResponse.invalidAmount(amount);
        }
        if (amount.signum() == 0) {
            return EcoResponse.success(amount, getBalance(playerUUID));
        }

        return toEcoResponse(amount, economy.deposit(TRANSACTION_SOURCE, playerUUID, amount), playerUUID);
    }

    @Override
    public EcoResponse take(UUID playerUUID, BigDecimal amount) {
        if (amount.signum() < 0) {
            return EcoResponse.invalidAmount(amount);
        }

        BigDecimal current = getBalance(playerUUID);
        if (amount.signum() == 0) {
            return EcoResponse.success(amount, current);
        }
        if (!economy.has(TRANSACTION_SOURCE, playerUUID, amount)) {
            return EcoResponse.insufficientFunds(amount, current);
        }

        return toEcoResponse(amount, economy.withdraw(TRANSACTION_SOURCE, playerUUID, amount), playerUUID);
    }

    @Override
    public EcoResponse set(UUID playerUUID, BigDecimal amount) {
        if (amount.signum() < 0) {
            return EcoResponse.invalidAmount(amount);
        }

        BigDecimal current = getBalance(playerUUID);
        if (amount.compareTo(current) == 0) {
            //Already at the target: skipping the call keeps a no-op out of the provider's ledger.
            return EcoResponse.success(amount, current);
        }

        return toEcoResponse(amount, economy.set(TRANSACTION_SOURCE, playerUUID, amount), playerUUID);
    }

    @Override
    public String format(BigDecimal amount) {
        return economy.format(amount);
    }

    private EcoResponse toEcoResponse(BigDecimal amount, EconomyResponse response, UUID playerUUID) {
        if (response.transactionSuccess()) {
            return EcoResponse.success(amount, response.balance != null ? response.balance : getBalance(playerUUID));
        }
        return EcoResponse.providerError(amount, getBalance(playerUUID), response.errorMessage);
    }

}
