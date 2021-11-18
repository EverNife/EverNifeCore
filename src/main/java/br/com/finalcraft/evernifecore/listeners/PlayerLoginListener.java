package br.com.finalcraft.evernifecore.listeners;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.events.ECFullyLoggedInEvent;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.config.uuids.UUIDsController;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import fr.xephi.authme.events.LoginEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerLoginListener implements ECListener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED){
            return;
        }

        UUIDsController.addUUIDName(event.getUniqueId(),event.getName());
        PlayerController.getOrCreateOne(event.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }

        PlayerData playerData = PlayerController.getPlayerData(event.getPlayer());
        playerData.setPlayer(event.getPlayer()); //This is a bad practice, but in minecraft, what is not :D
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        PlayerData playerData = PlayerController.getPlayerData(event.getPlayer());
        playerData.setPlayer(null); //This is a bad practice, but in minecraft, what is not :D
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Handlers for ECFullyLoggedInEvent
    // -----------------------------------------------------------------------------------------------------------------

    public static class VanillaLogin implements ECListener {
        @EventHandler(priority = EventPriority.MONITOR)
        public void onPlayerLogin(PlayerLoginEvent event) {
            fireDelayedFullyLoggedInEvent(event.getPlayer());
        }
    }

    public static class AuthmeLogin implements ECListener{
        @EventHandler(priority = EventPriority.MONITOR)
        public void onAuthmeLogin(LoginEvent event) {
            fireDelayedFullyLoggedInEvent(event.getPlayer());
        }
    }

    private static void fireDelayedFullyLoggedInEvent(Player player) {
        new BukkitRunnable(){
            @Override
            public void run() {
                if (player.isOnline()){
                    PlayerData playerData = PlayerController.getPlayerData(player);
                    ECFullyLoggedInEvent event = new ECFullyLoggedInEvent(playerData, true);
                    Bukkit.getPluginManager().callEvent(event);
                }
            }
        }.runTaskLater(EverNifeCore.instance, 1);
    }
}
