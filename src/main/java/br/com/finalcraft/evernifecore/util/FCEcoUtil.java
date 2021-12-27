package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.economy.ontime.EconomyManager;
import org.bukkit.OfflinePlayer;

import java.util.Objects;

public class FCEcoUtil {

    /**
     * Get Players Balance
     *
     * @param playerData The Player's PlayerData
     */
    public static double ecoGet(PlayerData playerData){
        return EconomyManager.getProvider().ecoGet(playerData);
    }

    public static double ecoGet(OfflinePlayer player) {
        PlayerData playerData = PlayerController.getPlayerData(player);
        Objects.requireNonNull(playerData, player.getName() + " does not exist on EverNifeCore's Database!");
        return ecoGet(playerData);
    }

    /**
     * Give money to a Player
     *
     * @param playerData The Player's PlayerData
     * @param value The amount of money
     */
    public static void ecoGive(PlayerData playerData, double value) {
        EconomyManager.getProvider().ecoGive(playerData, value);
    }

    public static void ecoGive(OfflinePlayer player, double value) {
        PlayerData playerData = PlayerController.getPlayerData(player);
        Objects.requireNonNull(playerData, player.getName() + " does not exist on EverNifeCore's Database!");
        ecoGive(playerData, value);
    }

    /**
     * Take money from a Player
     *
     * @param playerData The Player's PlayerData
     * @param value The amount of money
     *
     * @return <>true</> if success <>false</> otherwise
     */
    public static boolean ecoTake(PlayerData playerData, double value) {
        return EconomyManager.getProvider().ecoTake(playerData, value);
    }

    public static boolean ecoTake(OfflinePlayer player, double value) {
        PlayerData playerData = PlayerController.getPlayerData(player);
        Objects.requireNonNull(playerData, player.getName() + " does not exist on EverNifeCore's Database!");
        return ecoTake(playerData, value);
    }

    /**
     * Set the money from a Player to a specific value
     *
     * @param playerData The Player's PlayerData
     * @param value The amount of money
     */
    public static void ecoSet(PlayerData playerData, double value) {
        EconomyManager.getProvider().ecoSet(playerData, value);
    }

    public static void ecoSet(OfflinePlayer player, double value) {
        PlayerData playerData = PlayerController.getPlayerData(player);
        Objects.requireNonNull(playerData, player.getName() + " does not exist on EverNifeCore's Database!");
        ecoSet(playerData, value);
    }

    /**
     * Check if the Player has enought money
     *
     * @param playerData The Player's PlayerData
     * @param value The amount of money
     *
     * @return <>true</> if the player has the money <>false</>
     * otherwise
     */
    public static boolean ecoHasEnough(PlayerData playerData, double value) {
        return ecoGet(playerData) >= value;
    }

    public static boolean ecoHasEnough(OfflinePlayer player, double value) {
        PlayerData playerData = PlayerController.getPlayerData(player);
        Objects.requireNonNull(playerData, player.getName() + " does not exist on EverNifeCore's Database!");
        return ecoHasEnough(playerData, value);
    }

}
