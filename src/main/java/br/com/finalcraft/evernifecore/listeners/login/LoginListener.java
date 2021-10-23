package br.com.finalcraft.evernifecore.listeners.login;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.events.ECFullyLoggedInEvent;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import fr.xephi.authme.events.LoginEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class LoginListener implements ECListener {

    public static class VanillaLogin implements ECListener {
        @EventHandler(priority = EventPriority.MONITOR)
        public void onPlayerLogin(PlayerLoginEvent event) {
            fireDelayedECLoginEvent(event.getPlayer());
        }
    }

    public static class AuthmeLogin implements ECListener{
        @EventHandler(priority = EventPriority.MONITOR)
        public void onAuthmeLogin(LoginEvent event) {
            LoginListener.fireDelayedECLoginEvent(event.getPlayer());
        }
    }

    private static void fireDelayedECLoginEvent(Player player) {
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
