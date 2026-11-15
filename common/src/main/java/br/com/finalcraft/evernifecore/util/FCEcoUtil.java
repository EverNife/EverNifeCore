package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.economy.Currencies;
import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The one-liner facade over {@link Currencies#money()}: the server's economy for a player, by uuid, {@link FPlayer} or
 * {@link IPlayerData}, in {@code double} or {@code BigDecimal}. Every method delegates to the {@code money} currency,
 * so its rules hold whichever entry point the caller picked; another currency or a group or global wallet means calling
 * a {@link br.com.finalcraft.evernifecore.economy.Currency Currency} directly.
 *
 * <p>Every move ({@code ecoGive}, {@code ecoTake}, {@code ecoSet}) is made on the {@link Caller} the plugin passes -
 * {@code Caller.of(plugin, "shop/buy")} - so it reaches that plugin's audit; reads take none. Mutations answer with an
 * {@link EcoResponse}. Nothing throws for a missing economy: a mutation answers {@link EcoResponse.Reason#UNAVAILABLE},
 * a read answers zero, and {@link #isEcoAvailable()} tells the two apart. Thread: the economy plugin's - main thread to
 * write.</p>
 */
public class FCEcoUtil {

    private FCEcoUtil() {
    }

    private static CurrencyOwner wallet(UUID playerUUID) {
        return CurrencyOwner.player(playerUUID);
    }

    /** True when money can actually move: the way to gate a shop instead of reading UNAVAILABLE afterwards. */
    public static boolean isEcoAvailable() {
        return Currencies.money().isAvailable();
    }

    // =========================================================================
    // ecoGet (double)
    // =========================================================================

    public static double ecoGet(IPlayerData playerData) {
        return ecoGet(playerData.getUniqueId());
    }

    public static double ecoGet(UUID playerUUID) {
        return Currencies.money().getBalance(wallet(playerUUID)).doubleValue();
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
        return Currencies.money().getBalance(wallet(playerUUID));
    }

    public static BigDecimal ecoGetInBigDecimal(FPlayer player) {
        return ecoGetInBigDecimal(player.getUniqueId());
    }

    // =========================================================================
    // ecoGive
    // =========================================================================

    public static EcoResponse ecoGive(Caller caller, IPlayerData playerData, double amount) {
        return ecoGive(caller, playerData.getUniqueId(), amount);
    }

    public static EcoResponse ecoGive(Caller caller, IPlayerData playerData, BigDecimal amount) {
        return ecoGive(caller, playerData.getUniqueId(), amount);
    }

    public static EcoResponse ecoGive(Caller caller, UUID playerUUID, double amount) {
        return ecoGive(caller, playerUUID, BigDecimal.valueOf(amount));
    }

    public static EcoResponse ecoGive(Caller caller, UUID playerUUID, BigDecimal amount) {
        return Currencies.money().give(wallet(playerUUID), amount, caller);
    }

    public static EcoResponse ecoGive(Caller caller, FPlayer player, double amount) {
        return ecoGive(caller, player.getUniqueId(), amount);
    }

    public static EcoResponse ecoGive(Caller caller, FPlayer player, BigDecimal amount) {
        return ecoGive(caller, player.getUniqueId(), amount);
    }

    // =========================================================================
    // ecoTake
    // =========================================================================

    public static EcoResponse ecoTake(Caller caller, IPlayerData playerData, double amount) {
        return ecoTake(caller, playerData.getUniqueId(), amount);
    }

    public static EcoResponse ecoTake(Caller caller, IPlayerData playerData, BigDecimal amount) {
        return ecoTake(caller, playerData.getUniqueId(), amount);
    }

    public static EcoResponse ecoTake(Caller caller, UUID playerUUID, double amount) {
        return ecoTake(caller, playerUUID, BigDecimal.valueOf(amount));
    }

    public static EcoResponse ecoTake(Caller caller, UUID playerUUID, BigDecimal amount) {
        return Currencies.money().take(wallet(playerUUID), amount, caller);
    }

    public static EcoResponse ecoTake(Caller caller, FPlayer player, double amount) {
        return ecoTake(caller, player.getUniqueId(), amount);
    }

    public static EcoResponse ecoTake(Caller caller, FPlayer player, BigDecimal amount) {
        return ecoTake(caller, player.getUniqueId(), amount);
    }

    // =========================================================================
    // ecoSet
    // =========================================================================

    public static EcoResponse ecoSet(Caller caller, IPlayerData playerData, double amount) {
        return ecoSet(caller, playerData.getUniqueId(), amount);
    }

    public static EcoResponse ecoSet(Caller caller, IPlayerData playerData, BigDecimal amount) {
        return ecoSet(caller, playerData.getUniqueId(), amount);
    }

    public static EcoResponse ecoSet(Caller caller, UUID playerUUID, double amount) {
        return ecoSet(caller, playerUUID, BigDecimal.valueOf(amount));
    }

    public static EcoResponse ecoSet(Caller caller, UUID playerUUID, BigDecimal amount) {
        return Currencies.money().set(wallet(playerUUID), amount, caller);
    }

    public static EcoResponse ecoSet(Caller caller, FPlayer player, double amount) {
        return ecoSet(caller, player.getUniqueId(), amount);
    }

    public static EcoResponse ecoSet(Caller caller, FPlayer player, BigDecimal amount) {
        return ecoSet(caller, player.getUniqueId(), amount);
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
        return Currencies.money().has(wallet(playerUUID), amount);
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

    /** Renders an amount the way the economy plugin itself would ({@code "$1,234.50"}); the plain amount without one. */
    public static String ecoFormat(double amount) {
        return ecoFormat(BigDecimal.valueOf(amount));
    }

    public static String ecoFormat(BigDecimal amount) {
        return Currencies.money().format(amount).getText();
    }

}
