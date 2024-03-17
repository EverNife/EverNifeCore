package br.com.finalcraft.evernifecore.listeners;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.events.ECFullyLoggedInEvent;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.config.uuids.UUIDsController;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import fr.xephi.authme.events.LoginEvent;
import org.apache.commons.io.Charsets;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.UUID;

public class PlayerLoginListener implements ECListener {

    public static void handlePlayerAsyncPreUUIDToNameCalculation(UUID currentUUID, String currentName){
        if (UUIDsController.isUUIDLinkedToName(currentUUID, currentName)){
            //99% os cases on relogin, this will happen
            return; //We already have this player in our database, and the name and the uuid are still the same
        }

        //Now three Scnearios:
        // 1- It's a complete new Player
        // 2- The UUID is different
        // 3- The Name is different

        String existingName = UUIDsController.getNameFromUUID(currentUUID);
        UUID existingUUID = UUIDsController.getUUIDFromName(currentName);

        if (existingName == null && existingUUID == null){
            //This is a complete new Player!
            //We just need to add the new Pair of UUID and Name
            //And create a new PlayerData for this player
            UUIDsController.addOrUpdateUUIDName(currentUUID, currentName);
            PlayerController.getOrCreateOne(currentUUID);
            return;
        }

        //We have an inconsistency in the playerData, two scenarios:
        // 1- The server is in OnlineMode=true and a player changed his name in the mojang site (so, name is different and uuid is the same)
        // 2- The server changed from OnlineMode=false to OnlineMode=true or vice-versa (so, name is the same and uuid is different)
        // C- It's a special case, described bellow

        /*
         * There special case only matters if the server is in onlineMode=true
         *
         *   [C1] Server is in OnlineMode=true or BungeeCord's is enabled
         *   [C2] An old player stops playing and change his mojang name to something else
         *   [C3] A new player or an existing one change his name to the same name from that old player
         *   [C4] In this case we must, at the eminence of the new player,
         *       delete the old player data (or move to dormant) and create a new one for the new player
         */
        UUID offlineCalculatedUUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + currentName).getBytes(Charsets.UTF_8));
        if (!currentUUID.equals(offlineCalculatedUUID) // Scenario [C1], we are online
                && existingUUID != null // This means that C2 and C3 is possible, as there is a player (maybe myself) with the same name
                && !existingUUID.equals(currentUUID) // This is probably different player
                && !existingUUID.equals(offlineCalculatedUUID)){ // Confirm it's a different player, this checks for "this player is not myself in offline-mode"

            //The CURRENT_NAME is vinculated to another UUID
            PlayerData playerData = PlayerController.getPlayerData(existingUUID);
            PlayerController.getMapOfPlayerData().remove(playerData.getUniqueId());//Unload this PlayerData

            playerData.getConfig().getTheFile().delete(); //Delete previous file
            playerData.getConfig().save(new File(EverNifeCore.instance.getDataFolder(), "PlayerData-Dormant/" + existingUUID + ".yml"));//Move to dormant folder
            EverNifeCore.getLog().info("[UUIDsController] [%s:%s] was moved to dormant files because his name is not valid anymore!", existingUUID, currentName);

            //Now we can create a new PlayerData for this player
            //Or maybe change, if this is not a complete new player, change his username
            if (existingName == null){
                //This is a complete new Player!
                //We just need to add the new Pair of UUID and Name
                //And create a new PlayerData for this player
                UUIDsController.addOrUpdateUUIDName(currentUUID, currentName);
                PlayerController.getOrCreateOne(currentUUID);
                return;
            }
        }

        // Treating case [1] and [2]
        if (existingName == null){
            //If no existingName, then we have a new name for an existing UUID
            PlayerData playerData = PlayerController.getPlayerData(existingUUID);
            PlayerController.getMapOfPlayerData().remove(playerData.getUniqueId());//Unload this PlayerData
            playerData.getConfig().setValue("PlayerData.UUID", currentUUID);

            playerData.getConfig().getTheFile().delete(); //Delete previous file, in case we have changed its name
            String newFileName = (ECSettings.useNamesInsteadOfUUIDToStorePlayerData ? currentName : currentUUID.toString()) + ".yml";
            playerData.getConfig().save(new File(EverNifeCore.instance.getDataFolder(), "PlayerData/" + newFileName));

            EverNifeCore.getLog().info("[UUIDsController] [%s] changed his UUID from %s to %s", currentName, playerData.getUniqueId(), currentUUID);
            UUIDsController.addOrUpdateUUIDName(currentUUID, currentName);
            PlayerController.getOrCreateOne(currentUUID);
        }else {
            //If no existingUUID, then we have a new UUID for an existing Name
            PlayerData playerData = PlayerController.getPlayerData(existingName);
            PlayerController.getMapOfPlayerData().remove(playerData.getUniqueId());//Unload this PlayerData
            playerData.getConfig().setValue("PlayerData.Username", currentName);

            playerData.getConfig().getTheFile().delete(); //Delete previous file, in case we have changed its name
            String newFileName = (ECSettings.useNamesInsteadOfUUIDToStorePlayerData ? currentName : currentUUID.toString()) + ".yml";
            playerData.getConfig().save(new File(EverNifeCore.instance.getDataFolder(), "PlayerData/" + newFileName));

            EverNifeCore.getLog().info("[UUIDsController] [%s] changed his name from %s to %s", currentUUID, playerData.getPlayerName(), currentName);
            UUIDsController.addOrUpdateUUIDName(currentUUID, currentName);
            PlayerController.getOrCreateOne(currentUUID);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED){
            return;
        }

        handlePlayerAsyncPreUUIDToNameCalculation(event.getUniqueId(), event.getName());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }

        PlayerData playerData = PlayerController.getPlayerData(event.getPlayer());
        playerData.setPlayer(event.getPlayer()); //[Store an instance of Player] is a bad practice, but in minecraft, what is not :D
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        PlayerData playerData = PlayerController.getPlayerData(event.getPlayer());
        if (playerData != null){
            //In some cases a Player may not have a PlayerData, this usually
            //happens when the player has not been able to fully join the server,
            //like when the Whitelist is turned on

            playerData.setPlayer(null); //[Store an instance of Player] is a bad practice, but in minecraft, what is not :D
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Handlers for ECFullyLoggedInEvent
    // -----------------------------------------------------------------------------------------------------------------

    public static class VanillaLogin implements ECListener {
        @EventHandler(priority = EventPriority.MONITOR)
        public void onPlayerLogin(PlayerJoinEvent event) {
            if (!FCBukkitUtil.isFakePlayer(event.getPlayer())){
                fireDelayedFullyLoggedInEvent(event.getPlayer(), false);
            }
        }
    }

    public static class AuthmeLogin implements ECListener{
        @EventHandler(priority = EventPriority.MONITOR)
        public void onAuthMeLogin(LoginEvent event) {
            fireDelayedFullyLoggedInEvent(event.getPlayer(), true);
        }
    }

    private static void fireDelayedFullyLoggedInEvent(Player player, boolean authMeLogin) {
        new BukkitRunnable(){
            @Override
            public void run() {
                if (player.isOnline()){
                    PlayerData playerData = PlayerController.getPlayerData(player);
                    if (playerData != null){
                        ECFullyLoggedInEvent event = new ECFullyLoggedInEvent(playerData, authMeLogin);
                        Bukkit.getPluginManager().callEvent(event);
                    }
                }
            }
        }.runTaskLater(EverNifeCore.instance, 1);
    }
}
