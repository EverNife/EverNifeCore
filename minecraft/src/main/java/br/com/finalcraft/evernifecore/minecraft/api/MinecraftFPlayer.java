package br.com.finalcraft.evernifecore.minecraft.api;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.BaseFPlayer;
import br.com.finalcraft.evernifecore.api.platoverride.player.FPlayerAdapter;
import br.com.finalcraft.evernifecore.integration.LuckPermsIntegration;
import br.com.finalcraft.evernifecore.minecraft.util.FCMinecraftAdventureUtil;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

public abstract class MinecraftFPlayer<DELEGATE> extends BaseFPlayer<DELEGATE> {

    public MinecraftFPlayer(DELEGATE delegate) {
        super(delegate);
    }

    public static MinecraftFPlayer of(OfflinePlayer player) {
        return new PlayerFPlayer(player);
    }

    public abstract OfflinePlayer getOfflinePlayer();

    @Override
    public String getName() {
        return getOfflinePlayer().getName();
    }

    @Override
    public UUID getUniqueId() {
        return getOfflinePlayer().getUniqueId();
    }

    @Override
    public void sendMessage(@Nonnull Component component) {
        Player player = getPlayer();
        if (player != null && player.isOnline()) {
            FCMinecraftAdventureUtil.sendMessage(player, component);
        }
    }

    @Override
    public boolean hasPermission(@Nonnull String permission) {
        Player onlinePlayer = getPlayer(); // null when the player is offline
        if (onlinePlayer != null) {
            return onlinePlayer.hasPermission(permission);
        }
        if (EverNifeCore.getPlatform().isPluginLoaded("LuckPerms")) {
            return LuckPermsIntegration.hasPermission(getUniqueId(), permission);
        }
        return false;
    }

    @Override
    public boolean isOnline() {
        return getOfflinePlayer().isOnline();
    }

    public @Nullable World getWorld() {
        Player player = getOfflinePlayer().getPlayer();

        if (player == null || !player.isOnline()) {
            return null;
        }

        return player.getWorld();
    }

    @Override
    public @Nullable FLocation getLocation() {
        Player player = getOfflinePlayer().getPlayer();

        if (player == null || !player.isOnline()) {
            return null;
        }

        return FCBukkitUtil.adapt(player.getLocation());
    }

    @Override
    public boolean teleportTo(FLocation targetLocation){
        return getPlayer().teleport(targetLocation.getDelegate(Location.class));
    }

    public Player getPlayer() {
        return getOfflinePlayer().getPlayer();
    }

    public static class PlayerFPlayer extends FPlayerAdapter<OfflinePlayer> {

        public PlayerFPlayer(OfflinePlayer player) {
            super(player);
        }

        @Override
        public OfflinePlayer getOfflinePlayer() {
            return getDelegate();
        }

    }

}
