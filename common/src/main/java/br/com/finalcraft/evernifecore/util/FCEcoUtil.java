package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.economy.EconomyManager;

import java.math.BigDecimal;
import java.util.UUID;

public class FCEcoUtil {

    // =========================================================================
    // ecoGet (double)
    // =========================================================================

    public static double ecoGet(IPlayerData playerData) {
        return ecoGet(playerData.getUniqueId());
    }

    public static double ecoGet(UUID playerUUID) {
        return EconomyManager.getProvider().ecoGet(playerUUID);
    }

    public static double ecoGet(FPlayer player) {
        return EconomyManager.getProvider().ecoGet(player.getUniqueId());
    }

    // =========================================================================
    // ecoGetInBigDecimal
    // =========================================================================

    public static BigDecimal ecoGetInBigDecimal(IPlayerData playerData) {
        return ecoGetInBigDecimal(playerData.getUniqueId());
    }

    public static BigDecimal ecoGetInBigDecimal(UUID playerUUID) {
        return EconomyManager.getProvider().ecoGetInBigDecimal(playerUUID);
    }

    public static BigDecimal ecoGetInBigDecimal(FPlayer player) {
        return EconomyManager.getProvider().ecoGetInBigDecimal(player.getUniqueId());
    }

    // =========================================================================
    // ecoGive
    // =========================================================================

    public static boolean ecoGive(IPlayerData playerData, double amount) {
        return ecoGive(playerData.getUniqueId(), amount);
    }

    public static boolean ecoGive(IPlayerData playerData, BigDecimal amount) {
        return ecoGive(playerData.getUniqueId(), amount);
    }

    public static boolean ecoGive(UUID playerUUID, double amount) {
        return EconomyManager.getProvider().ecoGive(playerUUID, amount);
    }

    public static boolean ecoGive(UUID playerUUID, BigDecimal amount) {
        return EconomyManager.getProvider().ecoGive(playerUUID, amount);
    }

    public static boolean ecoGive(FPlayer player, double amount) {
        return EconomyManager.getProvider().ecoGive(player.getUniqueId(), amount);
    }

    public static boolean ecoGive(FPlayer player, BigDecimal amount) {
        return EconomyManager.getProvider().ecoGive(player.getUniqueId(), amount);
    }

    // =========================================================================
    // ecoTake
    // =========================================================================

    public static boolean ecoTake(IPlayerData playerData, double amount) {
        return ecoTake(playerData.getUniqueId(), amount);
    }

    public static boolean ecoTake(IPlayerData playerData, BigDecimal amount) {
        return ecoTake(playerData.getUniqueId(), amount);
    }

    public static boolean ecoTake(UUID playerUUID, double amount) {
        return EconomyManager.getProvider().ecoTake(playerUUID, amount);
    }

    public static boolean ecoTake(UUID playerUUID, BigDecimal amount) {
        return EconomyManager.getProvider().ecoTake(playerUUID, amount);
    }

    public static boolean ecoTake(FPlayer player, double amount) {
        return EconomyManager.getProvider().ecoTake(player.getUniqueId(), amount);
    }

    public static boolean ecoTake(FPlayer player, BigDecimal amount) {
        return EconomyManager.getProvider().ecoTake(player.getUniqueId(), amount);
    }

    // =========================================================================
    // ecoSet
    // =========================================================================

    public static boolean ecoSet(IPlayerData playerData, double amount) {
        return ecoSet(playerData.getUniqueId(), amount);
    }

    public static boolean ecoSet(IPlayerData playerData, BigDecimal amount) {
        return ecoSet(playerData.getUniqueId(), amount);
    }

    public static boolean ecoSet(UUID playerUUID, double amount) {
        return EconomyManager.getProvider().ecoSet(playerUUID, amount);
    }

    public static boolean ecoSet(UUID playerUUID, BigDecimal amount) {
        return EconomyManager.getProvider().ecoSet(playerUUID, amount);
    }

    public static boolean ecoSet(FPlayer player, double amount) {
        return EconomyManager.getProvider().ecoSet(player.getUniqueId(), amount);
    }

    public static boolean ecoSet(FPlayer player, BigDecimal amount) {
        return EconomyManager.getProvider().ecoSet(player.getUniqueId(), amount);
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
        return EconomyManager.getProvider().ecoHasEnough(playerUUID, amount);
    }

    public static boolean ecoHasEnough(UUID playerUUID, BigDecimal amount) {
        return EconomyManager.getProvider().ecoHasEnough(playerUUID, amount);
    }

    public static boolean ecoHasEnough(FPlayer player, double amount) {
        return EconomyManager.getProvider().ecoHasEnough(player.getUniqueId(), amount);
    }

    public static boolean ecoHasEnough(FPlayer player, BigDecimal amount) {
        return EconomyManager.getProvider().ecoHasEnough(player.getUniqueId(), amount);
    }

}
