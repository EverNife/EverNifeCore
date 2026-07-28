package br.com.finalcraft.evernifecore.minecraft.integration;

import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.economy.IEconomyProvider;
import net.milkbowl.vault2.economy.Economy;
import net.milkbowl.vault2.economy.EconomyResponse;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The VaultUnlocked economy behind the shared contract.
 *
 * <p>This is the API the contract was shaped after: BigDecimal, uuid keys, a real {@code set} and a
 * response that already carries the resulting balance. Every transaction is stamped with
 * {@link #TRANSACTION_SOURCE}, so the ledger attributes it to EverNifeCore rather than to whichever
 * plugin happened to call.</p>
 */
public class VaultEconomyV2 implements IEconomyProvider {

    private final Economy economyV2;

    public VaultEconomyV2(Object economyV2) {
        this.economyV2 = (Economy) economyV2;
    }

    @Override
    public Economy getHandle() {
        return economyV2;
    }

    @Override
    public BigDecimal getBalance(UUID playerUUID) {
        BigDecimal balance = economyV2.balance(TRANSACTION_SOURCE, playerUUID);
        return balance != null ? balance : BigDecimal.ZERO;
    }

    @Override
    public boolean hasEnough(UUID playerUUID, BigDecimal amount) {
        if (amount.signum() <= 0) {
            return true;
        }
        return economyV2.has(TRANSACTION_SOURCE, playerUUID, amount);
    }

    @Override
    public EcoResponse give(UUID playerUUID, BigDecimal amount) {
        if (amount.signum() < 0) {
            return EcoResponse.invalidAmount(amount);
        }
        if (amount.signum() == 0) {
            return EcoResponse.success(amount, getBalance(playerUUID));
        }

        return toEcoResponse(amount, economyV2.deposit(TRANSACTION_SOURCE, playerUUID, amount), playerUUID);
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
        if (!economyV2.has(TRANSACTION_SOURCE, playerUUID, amount)) {
            return EcoResponse.insufficientFunds(amount, current);
        }

        return toEcoResponse(amount, economyV2.withdraw(TRANSACTION_SOURCE, playerUUID, amount), playerUUID);
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

        return toEcoResponse(amount, economyV2.set(TRANSACTION_SOURCE, playerUUID, amount), playerUUID);
    }

    @Override
    public String format(BigDecimal amount) {
        return economyV2.format(amount);
    }

    private EcoResponse toEcoResponse(BigDecimal amount, EconomyResponse response, UUID playerUUID) {
        if (response.transactionSuccess()) {
            return EcoResponse.success(amount, response.balance != null ? response.balance : getBalance(playerUUID));
        }
        return EcoResponse.providerError(amount, getBalance(playerUUID), response.errorMessage);
    }

}
