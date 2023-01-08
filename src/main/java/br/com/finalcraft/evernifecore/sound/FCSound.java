package br.com.finalcraft.evernifecore.sound;

import br.com.finalcraft.evernifecore.util.FCSoundUtil;
import br.com.finalcraft.evernifecore.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.version.MCVersion;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class FCSound {

    public static FCSound PLING = FCSound.of(MCVersion.getCurrent().isLowerEquals(MCDetailedVersion.v1_8_R3) ? "note.pling" : "block.note_block.pling");
    public static FCSound LEVEL_UP = FCSound.of(MCVersion.getCurrent().isLowerEquals(MCDetailedVersion.v1_8_R3) ? "random.levelup" : "entity.player.levelup");

    public static FCSound of(String key){
        return new FCSound(key);
    }

    private final String key;
    private FCSound(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }


    // -----------------------------------------------------------------------------------------------------------------
    //   Play Sound to all Players
    // -----------------------------------------------------------------------------------------------------------------

    public void playSoundAll() {
        FCSoundUtil.playSoundAll(this.getKey());
    }

    public void playSoundAll(float pitch) {
        FCSoundUtil.playSoundAll(this.getKey(), pitch);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //   Play Sound at Specific Location for suround players
    // -----------------------------------------------------------------------------------------------------------------

    public void playSoundAt(@NotNull Location location){
        FCSoundUtil.playSoundAt(this.getKey(), location);
    }

    public void playSoundAt(@NotNull Location location, @NotNull String sound, float volume, float pitch){
        FCSoundUtil.playSoundAt(this.getKey(), location, volume, pitch);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //   Play Sound to Specific Player
    // -----------------------------------------------------------------------------------------------------------------

    public void playSoundFor(@NotNull Player player){
        FCSoundUtil.playSoundFor(this.getKey(), player);
    }

    public void playSoundFor(@NotNull Player player, @NotNull Location location){
        FCSoundUtil.playSoundFor(this.getKey(), player, location);
    }

    public void playSoundFor(@NotNull String sound, @NotNull Player player, @NotNull Location location, float volume, float pitch){
        FCSoundUtil.playSoundFor(this.getKey(), player, location, volume, pitch);
    }
}
