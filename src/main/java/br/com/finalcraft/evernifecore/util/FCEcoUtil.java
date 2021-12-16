package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.economy.ontime.EconomyManager;

public class FCEcoUtil {

    /**
     * Get Players Balance
     *
     * @param playerData The Player's PlayerData
     */
    public static double ecoGet(PlayerData playerData){
        return EconomyManager.getProvider().ecoGet(playerData);
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

    /**
     * Set the money from a Player to a specific value
     *
     * @param playerData The Player's PlayerData
     * @param value The amount of money
     */
    public static void ecoSet(PlayerData playerData, double value) {
        EconomyManager.getProvider().ecoSet(playerData, value);
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

}
