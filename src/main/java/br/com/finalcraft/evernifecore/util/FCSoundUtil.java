package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.version.MCVersion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class FCSoundUtil {

    // -----------------------------------------------------------------------------------------------------------------
    //   Play Sound to all Players
    // -----------------------------------------------------------------------------------------------------------------

    public static void playSoundAll(String sound) {
        playSoundAll(sound, 1.0F);
    }

    public static void playSoundAll(String sound, float pitch) {
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, Float.MAX_VALUE, pitch);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //   Play Sound at Specific Location for suround players
    // -----------------------------------------------------------------------------------------------------------------

    public static void playSoundAt(@NotNull String sound, @NotNull Location location){
        playSoundAt(sound, location, 1.0F, 1.0F);
    }

    public static void playSoundAt(@NotNull String sound, @NotNull Location location, float volume, float pitch){
        if (MCVersion.isEqual(MCVersion.v1_7_10)){
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().equals(location.getWorld())){
                    player.playSound(location, sound, volume, pitch);
                }
            }
        }else {
            location.getWorld().playSound(location, sound, volume, pitch);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //   Play Sound to Specific Player
    // -----------------------------------------------------------------------------------------------------------------

    public static void playSoundFor(@NotNull String sound, @NotNull Player player){
        playSoundFor(sound, player, player.getLocation(), 1.0F, 1.0F);
    }

    public static void playSoundFor(@NotNull String sound, @NotNull Player player, @NotNull Location location){
        playSoundFor(sound, player, location, 1.0F, 1.0F);
    }

    public static void playSoundFor(@NotNull String sound, @NotNull Player player, @NotNull Location location, float volume, float pitch){
        player.playSound(location, sound, volume, pitch);
    }

}
