package br.com.finalcraft.evernifecore.minecraft.listeners;

import br.com.finalcraft.evernifecore.actionbar.ActionBarAPI;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.api.events.ECFullyLoggedInEvent;
import br.com.finalcraft.evernifecore.minecraft.loader.EverNifeCoreBukkitPlugin;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED){
            return;
        }

        //We are already off the main thread here - join() on the async login pipeline.
        //Bounded by a timeout: a hung backend must DENY the login, not hang the Netty thread forever.
        try {
            PlayerController.handleLoginWithTimeout(event.getUniqueId(), event.getName()).join();
        } catch (Throwable loginFailure) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "Could not load your player data (storage unavailable). Please try again shortly.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }

        PlayerData playerData = PlayerController.getLoaded(event.getPlayer().getUniqueId());

        if (playerData != null){
            //In some cases a Player may not have a PlayerData, this usually
            // happens when another plugin creates a FakePlayer without
            // calling the AsyncPlayerPreLoginEvent


            //[Holding onto a Player.class instance] is bad practice, but on minecraft, what isn't :D
            playerData.setPlayer(FCBukkitUtil.adapt(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        PlayerData playerData = PlayerController.getLoaded(event.getPlayer().getUniqueId());
        if (playerData != null){
            //In some cases a Player may not have a PlayerData, this usually
            // happens when the player could not fully join the server,
            // such as when the Whitelist is on (AsyncPlayerPreLoginEvent is not called correctly)


            //Detach + durably flush this player off the quit thread (bounded async; retried on
            //a storage outage, never dropped). Working-set sections evict after a short grace.
            PlayerController.handlePlayerQuit(playerData.getUniqueId());

            ActionBarAPI.clearReferences(playerData.getUniqueId());
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

                if (!player.isOnline()){
                    return;
                }

                PlayerData playerData = PlayerController.getLoaded(player.getUniqueId());

                if (playerData == null || playerData.getPlayer() == null){
                    return;
                }

                ECFullyLoggedInEvent event = new ECFullyLoggedInEvent(playerData, authMeLogin);
                Bukkit.getPluginManager().callEvent(event);
            }
        }.runTaskLater(EverNifeCoreBukkitPlugin.instance, 1);
    }
}
