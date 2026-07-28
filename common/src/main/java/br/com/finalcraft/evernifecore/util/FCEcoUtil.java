package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.economy.IEconomyProvider;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The single entry point to economy, on every platform.
 *
 * <p>Mutations answer with an {@link EcoResponse} instead of a boolean, so "the balance was already
 * the target" and "the provider refused" stop looking the same. The {@code double} overloads are the
 * convenience layer: they convert to BigDecimal here, once, and everything below this class compares
 * with {@code compareTo} only.</p>
 *
 * <p>With no economy on the server every method throws {@link IllegalStateException} with an
 * actionable message. That is deliberate: a shop that charges nothing is worse than a shop that
 * fails loudly. Use {@link #isEcoAvailable()} to feature-gate instead of catching.</p>
 */
public class FCEcoUtil {

    private FCEcoUtil() {
    }

    private static IEconomyProvider economy() {
        IEconomyProvider provider = EverNifeCore.getProviders().getEconomyOrNull();
        if (provider == null) {
            throw new IllegalStateException("No economy provider registered - this platform never wired one up");
        }
        return provider;
    }

    /** True when money can actually move: the way to gate a shop instead of catching an exception. */
    public static boolean isEcoAvailable() {
        IEconomyProvider provider = EverNifeCore.getProviders().getEconomyOrNull();
        return provider != null && provider.isAvailable();
    }

    // =========================================================================
    // ecoGet (double)
    // =========================================================================

    public static double ecoGet(IPlayerData playerData) {
        return ecoGet(playerData.getUniqueId());
    }

    public static double ecoGet(UUID playerUUID) {
        return economy().getBalance(playerUUID).doubleValue();
    }

    public static double ecoGet(FPlayer player) {
        return ecoGet(player.getUniqueId());
    }

    // =========================================================================
    // ecoGetInBigDecimal
    // =========================================================================

    public static BigDecimal ecoGetInBigDecimal(IPlayerData playerData) {
        return ecoGetInBigDecimal(playerData.getUniqueId());
    }

    public static BigDecimal ecoGetInBigDecimal(UUID playerUUID) {
        return economy().getBalance(playerUUID);
    }

    public static BigDecimal ecoGetInBigDecimal(FPlayer player) {
        return ecoGetInBigDecimal(player.getUniqueId());
    }

    // =========================================================================
    // ecoGive
    // =========================================================================

    public static EcoResponse ecoGive(IPlayerData playerData, double amount) {
        return ecoGive(playerData.getUniqueId(), amount);
    }

    public static EcoResponse ecoGive(IPlayerData playerData, BigDecimal amount) {
        return ecoGive(playerData.getUniqueId(), amount);
    }

    public static EcoResponse ecoGive(UUID playerUUID, double amount) {
        return ecoGive(playerUUID, BigDecimal.valueOf(amount));
    }

    public static EcoResponse ecoGive(UUID playerUUID, BigDecimal amount) {
        return economy().give(playerUUID, amount);
    }

    public static EcoResponse ecoGive(FPlayer player, double amount) {
        return ecoGive(player.getUniqueId(), amount);
    }

    public static EcoResponse ecoGive(FPlayer player, BigDecimal amount) {
        return ecoGive(player.getUniqueId(), amount);
    }

    // =========================================================================
    // ecoTake
    // =========================================================================

    public static EcoResponse ecoTake(IPlayerData playerData, double amount) {
        return ecoTake(playerData.getUniqueId(), amount);
    }

    public static EcoResponse ecoTake(IPlayerData playerData, BigDecimal amount) {
        return ecoTake(playerData.getUniqueId(), amount);
    }

    public static EcoResponse ecoTake(UUID playerUUID, double amount) {
        return ecoTake(playerUUID, BigDecimal.valueOf(amount));
    }

    public static EcoResponse ecoTake(UUID playerUUID, BigDecimal amount) {
        return economy().take(playerUUID, amount);
    }

    public static EcoResponse ecoTake(FPlayer player, double amount) {
        return ecoTake(player.getUniqueId(), amount);
    }

    public static EcoResponse ecoTake(FPlayer player, BigDecimal amount) {
        return ecoTake(player.getUniqueId(), amount);
    }

    // =========================================================================
    // ecoSet
    // =========================================================================

    public static EcoResponse ecoSet(IPlayerData playerData, double amount) {
        return ecoSet(playerData.getUniqueId(), amount);
    }

    public static EcoResponse ecoSet(IPlayerData playerData, BigDecimal amount) {
        return ecoSet(playerData.getUniqueId(), amount);
    }

    public static EcoResponse ecoSet(UUID playerUUID, double amount) {
        return ecoSet(playerUUID, BigDecimal.valueOf(amount));
    }

    public static EcoResponse ecoSet(UUID playerUUID, BigDecimal amount) {
        return economy().set(playerUUID, amount);
    }

    public static EcoResponse ecoSet(FPlayer player, double amount) {
        return ecoSet(player.getUniqueId(), amount);
    }

    public static EcoResponse ecoSet(FPlayer player, BigDecimal amount) {
        return ecoSet(player.getUniqueId(), amount);
    }

    // =========================================================================
    // ecoHasEnough
    // =========================================================================

    public static boolean ecoHasEnough(IPlayerData playerData, double amount) {
        return ecoHasEnough(playerData.getUniqueId(), amount);
    }

    public static boolean ecoHasEnough(IPlayerData playerData, BigDecimal amount) {
        return ecoHasEnough(playerData.getUniqueId(), amount);
    }

    public static boolean ecoHasEnough(UUID playerUUID, double amount) {
        return ecoHasEnough(playerUUID, BigDecimal.valueOf(amount));
    }

    public static boolean ecoHasEnough(UUID playerUUID, BigDecimal amount) {
        return economy().hasEnough(playerUUID, amount);
    }

    public static boolean ecoHasEnough(FPlayer player, double amount) {
        return ecoHasEnough(player.getUniqueId(), amount);
    }

    public static boolean ecoHasEnough(FPlayer player, BigDecimal amount) {
        return ecoHasEnough(player.getUniqueId(), amount);
    }

    // =========================================================================
    // ecoFormat
    // =========================================================================

    /** Renders an amount the way the economy plugin itself would ({@code "$1,234.50"}). */
    public static String ecoFormat(double amount) {
        return ecoFormat(BigDecimal.valueOf(amount));
    }

    public static String ecoFormat(BigDecimal amount) {
        return economy().format(amount);
    }

}
