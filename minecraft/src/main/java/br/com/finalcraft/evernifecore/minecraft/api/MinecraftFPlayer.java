package br.com.finalcraft.evernifecore.minecraft.api;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.BaseFPlayer;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.platoverride.player.FPlayerAdapter;
import br.com.finalcraft.evernifecore.integration.LuckPermsIntegration;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.minecraft.util.FCMinecraftAdventureUtil;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.time.ECScheduler;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
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
        if (EverNifeCore.getPlatform().isPluginEnabled("LuckPerms")) {
            return LuckPermsIntegration.hasPermission(getUniqueId(), permission);
        }
        return false;
    }

    /** Online, Bukkit's own answer (an op is not "set"); offline, LuckPerms', which never answers by op. */
    @Override
    public boolean isPermissionSet(@Nonnull String permission) {
        Player onlinePlayer = getPlayer();
        if (onlinePlayer != null) {
            return onlinePlayer.isPermissionSet(permission);
        }
        return hasPermission(permission);
    }

    @Override
    public boolean canSee(@Nonnull FPlayer player) {
        Player viewer = getPlayer();
        Player target = player instanceof MinecraftFPlayer ? ((MinecraftFPlayer<?>) player).getPlayer() : null;
        return viewer == null || target == null || viewer.canSee(target);
    }

    @Override
    public boolean isOnline() {
        return getOfflinePlayer().isOnline();
    }

    @Override
    public boolean hasPlayedBefore() {
        return getOfflinePlayer().hasPlayedBefore();
    }

    @Override
    public void kick(@Nonnull String reason) {
        Player player = getPlayer();
        if (player == null || !player.isOnline()) return; // nothing to kick
        //kickPlayer is a non-thread-safe Bukkit call; run on the main thread and re-check online there
        ECScheduler.onMainThread(() -> {
            Player online = getPlayer();
            if (online != null && online.isOnline()) online.kickPlayer(reason);
        });
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
        Player player = getPlayer();
        Object delegate = targetLocation.getDelegate();
        //coordinates alone (no Bukkit location) keep the player's facing
        if (delegate instanceof Location) {
            return player.teleport((Location) delegate);
        }
        WorldLocPos at = targetLocation.getWorldLocPos();
        World world = Bukkit.getWorld(at.getWorldName());
        Location facing = player.getLocation();
        return world != null && player.teleport(new Location(world, at.getX(), at.getY(), at.getZ(), facing.getYaw(), facing.getPitch()));
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
