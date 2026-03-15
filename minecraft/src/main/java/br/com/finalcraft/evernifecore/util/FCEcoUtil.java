package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.integration.VaultIntegration;
import org.bukkit.OfflinePlayer;

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
        return VaultIntegration.getEcon().ecoGet(playerUUID);
    }

    public static double ecoGet(OfflinePlayer player) {
        return VaultIntegration.getEcon().ecoGet(player);
    }

    // =========================================================================
    // ecoGetInBigDecimal
    // =========================================================================

    public static BigDecimal ecoGetInBigDecimal(IPlayerData playerData) {
        return ecoGetInBigDecimal(playerData.getUniqueId());
    }

    public static BigDecimal ecoGetInBigDecimal(UUID playerUUID) {
        return VaultIntegration.getEcon().ecoGetInBigDecimal(playerUUID);
    }

    public static BigDecimal ecoGetInBigDecimal(OfflinePlayer player) {
        return VaultIntegration.getEcon().ecoGetInBigDecimal(player);
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
        return VaultIntegration.getEcon().ecoGive(playerUUID, BigDecimal.valueOf(amount));
    }

    public static boolean ecoGive(UUID playerUUID, BigDecimal amount) {
        return VaultIntegration.getEcon().ecoGive(playerUUID, amount);
    }

    public static boolean ecoGive(OfflinePlayer player, double amount) {
        return VaultIntegration.getEcon().ecoGive(player, BigDecimal.valueOf(amount));
    }

    public static boolean ecoGive(OfflinePlayer player, BigDecimal amount) {
        return VaultIntegration.getEcon().ecoGive(player, amount);
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
        return VaultIntegration.getEcon().ecoTake(playerUUID, BigDecimal.valueOf(amount));
    }

    public static boolean ecoTake(UUID playerUUID, BigDecimal amount) {
        return VaultIntegration.getEcon().ecoTake(playerUUID, amount);
    }

    public static boolean ecoTake(OfflinePlayer player, double amount) {
        return VaultIntegration.getEcon().ecoTake(player, BigDecimal.valueOf(amount));
    }

    public static boolean ecoTake(OfflinePlayer player, BigDecimal amount) {
        return VaultIntegration.getEcon().ecoTake(player, amount);
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
        return VaultIntegration.getEcon().ecoSet(playerUUID, BigDecimal.valueOf(amount));
    }

    public static boolean ecoSet(UUID playerUUID, BigDecimal amount) {
        return VaultIntegration.getEcon().ecoSet(playerUUID, amount);
    }

    public static boolean ecoSet(OfflinePlayer player, double amount) {
        return VaultIntegration.getEcon().ecoSet(player, BigDecimal.valueOf(amount));
    }

    public static boolean ecoSet(OfflinePlayer player, BigDecimal amount) {
        return VaultIntegration.getEcon().ecoSet(player, amount);
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
        return VaultIntegration.getEcon().ecoHasEnough(playerUUID, BigDecimal.valueOf(amount));
    }

    public static boolean ecoHasEnough(UUID playerUUID, BigDecimal amount) {
        return VaultIntegration.getEcon().ecoHasEnough(playerUUID, amount);
    }

    public static boolean ecoHasEnough(OfflinePlayer player, double amount) {
        return VaultIntegration.getEcon().ecoHasEnough(player, BigDecimal.valueOf(amount));
    }

    public static boolean ecoHasEnough(OfflinePlayer player, BigDecimal amount) {
        return VaultIntegration.getEcon().ecoHasEnough(player, amount);
    }

}
