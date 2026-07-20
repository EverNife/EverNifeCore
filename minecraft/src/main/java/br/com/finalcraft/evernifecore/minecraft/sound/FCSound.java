package br.com.finalcraft.evernifecore.minecraft.sound;

import br.com.finalcraft.evernifecore.minecraft.util.FCSoundUtil;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import jakarta.annotation.Nonnull;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class FCSound {

    public static FCSound PLING = FCSound.of(FCSoundUtil.pickByEra(MCVersion.getCurrent().getShortValue(), "note.pling", "block.note.pling", "block.note_block.pling"));
    public static FCSound HARP = FCSound.of(FCSoundUtil.pickByEra(MCVersion.getCurrent().getShortValue(), "note.harp", "block.note.harp", "block.note_block.harp"));
    public static FCSound LEVEL_UP = FCSound.of(MCVersion.isLowerEquals(MCDetailedVersion.v1_8_R3) ? "random.levelup" : "entity.player.levelup");
    public static FCSound EXPERIENCE_ORB = FCSound.of(MCVersion.isLowerEquals(MCDetailedVersion.v1_8_R3) ? "random.orb" : "entity.experience_orb.pickup");
    public static FCSound EXPLODE = FCSound.of(MCVersion.isLowerEquals(MCDetailedVersion.v1_8_R3) ? "random.explode" : "entity.generic.explode");
    public static FCSound CLICK = FCSound.of(MCVersion.isLowerEquals(MCDetailedVersion.v1_8_R3) ? "random.click" : "ui.button.click");
    public static FCSound ANVIL_BREAK = FCSound.of(MCVersion.isLowerEquals(MCDetailedVersion.v1_8_R3) ? "random.anvil_break" : "block.anvil.destroy");
    public static FCSound ANVIL_USE = FCSound.of(MCVersion.isLowerEquals(MCDetailedVersion.v1_8_R3) ? "random.anvil_use" : "block.anvil.use");
    public static FCSound ANVIL_LAND = FCSound.of(MCVersion.isLowerEquals(MCDetailedVersion.v1_8_R3) ? "random.anvil_land" : "block.anvil.land");
    public static FCSound CHICKEN_EGG_POP = FCSound.of(MCVersion.isLowerEquals(MCDetailedVersion.v1_8_R3) ? "random.pop" : "entity.chicken.egg");
    public static FCSound ITEM_BREAK = FCSound.of(MCVersion.isLowerEquals(MCDetailedVersion.v1_8_R3) ? "random.break" : "entity.item.break");

    public static FCSound SUCCESSFUL_HIT = FCSound.of(MCVersion.isLowerEquals(MCDetailedVersion.v1_8_R3) ? "random.successful_hit" : "entity.player.attack.nodamage");
    public static FCSound BURP = FCSound.of(MCVersion.isLowerEquals(MCDetailedVersion.v1_8_R3) ? "random.burp" : "entity.player.burp");
    public static FCSound DOOR_OPEN = FCSound.of(MCVersion.isLowerEquals(MCDetailedVersion.v1_8_R3) ? "random.door_open" : "block.wooden_door.open");
    public static FCSound DOOR_CLOSE = FCSound.of(MCVersion.isLowerEquals(MCDetailedVersion.v1_8_R3) ? "random.door_close" : "block.wooden_door.close");
    public static FCSound CHEST_OPEN = FCSound.of(MCVersion.isLowerEquals(MCDetailedVersion.v1_8_R3) ? "random.chestopen" : "block.chest.open");
    public static FCSound CHEST_CLOSE = FCSound.of(MCVersion.isLowerEquals(MCDetailedVersion.v1_8_R3) ? "random.chestclosed" : "block.chest.close");
    public static FCSound FIREWORK_BLAST = FCSound.of(FCSoundUtil.pickByEra(MCVersion.getCurrent().getShortValue(), "fireworks.blast", "entity.firework.blast", "entity.firework_rocket.blast"));
    public static FCSound FIREWORK_LARGE_BLAST = FCSound.of(FCSoundUtil.pickByEra(MCVersion.getCurrent().getShortValue(), "fireworks.largeBlast", "entity.firework.large_blast", "entity.firework_rocket.large_blast"));
    public static FCSound FIREWORK_BLAST2 = FCSound.of(FCSoundUtil.pickByEra(MCVersion.getCurrent().getShortValue(), "fireworks.blast2", "entity.firework.blast_far", "entity.firework_rocket.blast_far"));
    public static FCSound FIREWORK_TWINKLE = FCSound.of(FCSoundUtil.pickByEra(MCVersion.getCurrent().getShortValue(), "fireworks.twinkle", "entity.firework.twinkle", "entity.firework_rocket.twinkle"));
    public static FCSound FIREWORK_TWINKLE2 = FCSound.of(FCSoundUtil.pickByEra(MCVersion.getCurrent().getShortValue(), "fireworks.twinkle2", "entity.firework.twinkle_far", "entity.firework_rocket.twinkle_far"));
    public static FCSound FIREWORK_LAUNCH = FCSound.of(FCSoundUtil.pickByEra(MCVersion.getCurrent().getShortValue(), "fireworks.launch", "entity.firework.launch", "entity.firework_rocket.launch"));

    public static FCSound PORTAL_TRAVEL = FCSound.of(MCVersion.isLowerEquals(MCDetailedVersion.v1_8_R3) ? "portal.travel" : "block.portal.travel");
    public static FCSound PORTAL_TRIGGER = FCSound.of(MCVersion.isLowerEquals(MCDetailedVersion.v1_8_R3) ? "portal.trigger" : "block.portal.trigger");
    public static FCSound ENDERMAN_TELEPORT = FCSound.of(FCSoundUtil.pickByEra(MCVersion.getCurrent().getShortValue(), "mob.endermen.portal", "entity.endermen.teleport", "entity.enderman.teleport"));


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

    public void playSoundAt(@Nonnull Location location){
        FCSoundUtil.playSoundAt(this.getKey(), location);
    }

    public void playSoundAt(@Nonnull Location location, float volume, float pitch){
        FCSoundUtil.playSoundAt(this.getKey(), location, volume, pitch);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //   Play Sound to Specific Player
    // -----------------------------------------------------------------------------------------------------------------

    public void playSoundFor(@Nonnull Player player){
        FCSoundUtil.playSoundFor(this.getKey(), player);
    }

    public void playSoundFor(@Nonnull Player player, @Nonnull Location location){
        FCSoundUtil.playSoundFor(this.getKey(), player, location);
    }

    public void playSoundFor(@Nonnull Player player, @Nonnull Location location, float volume, float pitch){
        FCSoundUtil.playSoundFor(this.getKey(), player, location, volume, pitch);
    }
}
