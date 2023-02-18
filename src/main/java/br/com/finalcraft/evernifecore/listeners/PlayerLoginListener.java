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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerLoginListener implements ECListener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED){
            return;
        }

        UUIDsController.addOrUpdateUUIDName(event.getUniqueId(),event.getName());
        PlayerData playerData = PlayerController.getOrCreateOne(event.getUniqueId());

        if (!ECSettings.useNamesInsteadOfUUIDToStorePlayerData && !event.getName().equals(playerData.getPlayerName())){
            //When in online mode there is the possibility for the player to change his name
            //So we need to update the name in the playerData!
            playerData.getConfig().setValue("PlayerData.Username", event.getName());
            PlayerController.reloadPlayerData(event.getUniqueId());
        }
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
                    ECFullyLoggedInEvent event = new ECFullyLoggedInEvent(playerData, authMeLogin);
                    Bukkit.getPluginManager().callEvent(event);
                }
            }
        }.runTaskLater(EverNifeCore.instance, 1);
    }
}
