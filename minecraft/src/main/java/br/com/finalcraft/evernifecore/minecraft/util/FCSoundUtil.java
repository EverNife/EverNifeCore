package br.com.finalcraft.evernifecore.minecraft.util;

import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import jakarta.annotation.Nonnull;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class FCSoundUtil {

    /**
     * Picks the sound key for the running era. {@code detailedShortValue} is
     * {@code MCDetailedVersion.getShortValue()} (17,18 = 1.7/1.8; 19..112 = 1.9..1.12; &gt;=113 = 1.13+),
     * so a key renamed at the 1.9 or the 1.13 flattening resolves to the name that server actually knows.
     */
    public static String pickByEra(int detailedShortValue, String pre1_9, String from1_9to1_12, String flat1_13) {
        if (detailedShortValue <= 18) return pre1_9;
        if (detailedShortValue <= 112) return from1_9to1_12;
        return flat1_13;
    }

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

    public static void playSoundAt(@Nonnull String sound, @Nonnull Location location){
        playSoundAt(sound, location, 1.0F, 1.0F);
    }

    public static void playSoundAt(@Nonnull String sound, @Nonnull Location location, float volume, float pitch){
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

    public static void playSoundFor(@Nonnull String sound, @Nonnull Player player){
        playSoundFor(sound, player, player.getLocation(), 1.0F, 1.0F);
    }

    public static void playSoundFor(@Nonnull String sound, @Nonnull Player player, @Nonnull Location location){
        playSoundFor(sound, player, location, 1.0F, 1.0F);
    }

    public static void playSoundFor(@Nonnull String sound, @Nonnull Player player, @Nonnull Location location, float volume, float pitch){
        player.playSound(location, sound, volume, pitch);
    }

}
