package br.com.finalcraft.evernifecore.minecraft.integration;

import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.economy.IEconomyProvider;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Function;

/**
 * The classic Vault economy behind the shared contract.
 *
 * <p>Vault v1 is double-native and keyed by {@code OfflinePlayer}, so this is where the BigDecimal
 * contract degrades - and it is the only implementation that has to emulate {@code set}, since v1 has
 * no such verb and only moves the difference.</p>
 */
public class VaultEconomyV1 implements IEconomyProvider {

    private final Economy economy;
    private final Function<UUID, OfflinePlayer> asPlayer;

    public VaultEconomyV1(Object economy) {
        this(economy, Bukkit::getOfflinePlayer);
    }

    //The uuid conversion is a collaborator so the whole bridge runs in a unit test: Bukkit.getOfflinePlayer
    //is the only static here, and it is what used to make these semantics unverifiable off a live server.
    VaultEconomyV1(Object economy, Function<UUID, OfflinePlayer> asPlayer) {
        this.economy = (Economy) economy;
        this.asPlayer = asPlayer;
    }

    @Override
    public Economy getHandle() {
        return economy;
    }

    @Override
    public BigDecimal getBalance(UUID playerUUID) {
        return BigDecimal.valueOf(economy.getBalance(asPlayer.apply(playerUUID)));
    }

    @Override
    public boolean hasEnough(UUID playerUUID, BigDecimal amount) {
        if (amount.signum() <= 0) {
            return true;
        }
        return economy.has(asPlayer.apply(playerUUID), amount.doubleValue());
    }

    @Override
    public EcoResponse give(UUID playerUUID, BigDecimal amount) {
        if (amount.signum() < 0) {
            return EcoResponse.invalidAmount(amount);
        }

        OfflinePlayer player = asPlayer.apply(playerUUID);
        if (amount.signum() == 0) {
            return EcoResponse.success(amount, BigDecimal.valueOf(economy.getBalance(player)));
        }

        return toEcoResponse(amount, economy.depositPlayer(player, amount.doubleValue()), player);
    }

    @Override
    public EcoResponse take(UUID playerUUID, BigDecimal amount) {
        if (amount.signum() < 0) {
            return EcoResponse.invalidAmount(amount);
        }

        OfflinePlayer player = asPlayer.apply(playerUUID);
        BigDecimal current = BigDecimal.valueOf(economy.getBalance(player));
        if (amount.signum() == 0) {
            return EcoResponse.success(amount, current);
        }
        if (!economy.has(player, amount.doubleValue())) {
            return EcoResponse.insufficientFunds(amount, current);
        }

        return toEcoResponse(amount, economy.withdrawPlayer(player, amount.doubleValue()), player);
    }

    @Override
    public EcoResponse set(UUID playerUUID, BigDecimal amount) {
        if (amount.signum() < 0) {
            return EcoResponse.invalidAmount(amount);
        }

        BigDecimal current = getBalance(playerUUID);
        int comparison = amount.compareTo(current);
        if (comparison == 0) {
            //Already at the target. Emulating the set would move zero, and reporting that as a failure
            //is what made this verb mean something different on every economy.
            return EcoResponse.success(amount, current);
        }

        EcoResponse moved = comparison > 0
                ? give(playerUUID, amount.subtract(current))
                : take(playerUUID, current.subtract(amount));

        //The caller asked for a target balance, not for the difference that got moved.
        return moved.isSuccess() ? EcoResponse.success(amount, moved.getBalance()) : moved;
    }

    @Override
    public String format(BigDecimal amount) {
        return economy.format(amount.doubleValue());
    }

    private EcoResponse toEcoResponse(BigDecimal amount, EconomyResponse response, OfflinePlayer player) {
        if (response.transactionSuccess()) {
            return EcoResponse.success(amount, BigDecimal.valueOf(response.balance));
        }
        return EcoResponse.providerError(amount, BigDecimal.valueOf(economy.getBalance(player)), response.errorMessage);
    }

}
