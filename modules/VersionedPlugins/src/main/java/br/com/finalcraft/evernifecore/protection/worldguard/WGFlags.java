package br.com.finalcraft.evernifecore.protection.worldguard;

import com.sk89q.worldguard.protection.flags.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;


//This class is a mirror of https://github.com/EngineHub/WorldGuard/blob/master/worldguard-core/src/main/java/com/sk89q/worldguard/protection/flags/Flags.java
// I had to do this because this class has changed its name
// from "DefaultFlags" to "Flags" between WorldGuard 6.0 and 7.0
public class WGFlags {

    // Overrides membership check
    public static @DefaultState(state = false) StateFlag PASSTHROUGH;

    // This flag is unlike the others. It forces the checking of region membership
    public static @DefaultState(state = false) StateFlag BUILD;

    // These flags are used in tandem with the BUILD flag - if the player can
    // build, then the following flags do not need to be checked (although they
    // are still checked for DENY), so they are false by default
    public static @DefaultState(state = false) StateFlag BLOCK_BREAK;
    public static @DefaultState(state = false) StateFlag BLOCK_PLACE;
    public static @DefaultState(state = false) StateFlag USE;
    public static @DefaultState(state = false) StateFlag INTERACT;
    public static @DefaultState(state = false) StateFlag DAMAGE_ANIMALS;
    public static @DefaultState(state = false) StateFlag PVP;
    public static @DefaultState(state = false) StateFlag SLEEP;
    public static @DefaultState(state = false) StateFlag RESPAWN_ANCHORS;
    public static @DefaultState(state = false) StateFlag TNT;
    public static @DefaultState(state = false) StateFlag CHEST_ACCESS;
    public static @DefaultState(state = false) StateFlag PLACE_VEHICLE = (StateFlag) getFlagFromWorldGuard("vehicle-place");
    public static @DefaultState(state = false) StateFlag DESTROY_VEHICLE = (StateFlag) getFlagFromWorldGuard("vehicle-destroy");
    public static @DefaultState(state = false) StateFlag LIGHTER;
    public static @DefaultState(state = false) StateFlag RIDE;
    public static @DefaultState(state = false) StateFlag POTION_SPLASH;
    public static @DefaultState(state = false) StateFlag ITEM_FRAME_ROTATE = (StateFlag) getFlagFromWorldGuard("item-frame-rotation");
    public static @DefaultState(state = false) StateFlag TRAMPLE_BLOCKS = (StateFlag) getFlagFromWorldGuard("block-trampling");
    public static @DefaultState(state = false) StateFlag FIREWORK_DAMAGE;
    public static @DefaultState(state = false) StateFlag USE_ANVIL;
    public static @DefaultState(state = false) StateFlag USE_DRIPLEAF;

    // These flags are similar to the ones above (used in tandem with BUILD),
    // but their defaults are set to TRUE because it is more user friendly.
    // However, it is not possible to disable these flags by default in all
    // regions because setting DENY in __global__ would also override the
    // BUILD flag. In the future, StateFlags will need a DISALLOW state.
    public static @DefaultState(state = true) StateFlag ITEM_PICKUP; // Intentionally true
    public static @DefaultState(state = true) StateFlag ITEM_DROP; // Intentionally true
    public static @DefaultState(state = true) StateFlag EXP_DROPS; // Intentionally true

    // These flags adjust behavior and are not checked in tandem with the
    // BUILD flag so they need to be TRUE for their defaults.

    // mob griefing related
    public static @DefaultState(state = true) StateFlag MOB_DAMAGE;
    public static @DefaultState(state = true) StateFlag CREEPER_EXPLOSION;
    public static @DefaultState(state = true) StateFlag ENDERDRAGON_BLOCK_DAMAGE;
    public static @DefaultState(state = true) StateFlag GHAST_FIREBALL;
    public static @DefaultState(state = true) StateFlag OTHER_EXPLOSION;
    public static @DefaultState(state = true) StateFlag WITHER_DAMAGE;
    public static @DefaultState(state = true) StateFlag ENDER_BUILD = (StateFlag) getFlagFromWorldGuard("enderman-grief");
    public static @DefaultState(state = true) StateFlag SNOWMAN_TRAILS;
    public static @DefaultState(state = true) StateFlag RAVAGER_RAVAGE;
    public static @DefaultState(state = true) StateFlag ENTITY_PAINTING_DESTROY;
    public static @DefaultState(state = true) StateFlag ENTITY_ITEM_FRAME_DESTROY;

    // mob spawning related
    public static @DefaultState(state = true) StateFlag MOB_SPAWNING;
    public static SetFlag DENY_SPAWN;

    // block dynamics
    public static @DefaultState(state = true) StateFlag PISTONS;
    public static @DefaultState(state = true) StateFlag FIRE_SPREAD;
    public static @DefaultState(state = true) StateFlag LAVA_FIRE;
    public static @DefaultState(state = true) StateFlag LIGHTNING;
    public static @DefaultState(state = true) StateFlag SNOW_FALL;
    public static @DefaultState(state = true) StateFlag SNOW_MELT;
    public static @DefaultState(state = true) StateFlag ICE_FORM;
    public static @DefaultState(state = true) StateFlag ICE_MELT;
    public static @DefaultState(state = true) StateFlag FROSTED_ICE_MELT;
    public static @DefaultState(state = false) StateFlag FROSTED_ICE_FORM; // this belongs in the first category of "checked with build"
    public static @DefaultState(state = true) StateFlag MUSHROOMS = (StateFlag) getFlagFromWorldGuard("mushroom-growth");
    public static @DefaultState(state = true) StateFlag LEAF_DECAY;
    public static @DefaultState(state = true) StateFlag GRASS_SPREAD = (StateFlag) getFlagFromWorldGuard("grass-growth");
    public static @DefaultState(state = true) StateFlag MYCELIUM_SPREAD;
    public static @DefaultState(state = true) StateFlag VINE_GROWTH;
    public static @DefaultState(state = true) StateFlag ROCK_GROWTH;
    public static @DefaultState(state = true) StateFlag SCULK_GROWTH;
    public static @DefaultState(state = true) StateFlag CROP_GROWTH;
    public static @DefaultState(state = true) StateFlag SOIL_DRY;
    public static @DefaultState(state = true) StateFlag CORAL_FADE;
    public static @DefaultState(state = true) StateFlag WATER_FLOW;
    public static @DefaultState(state = true) StateFlag LAVA_FLOW;

    public static Flag WEATHER_LOCK; //<WeatherType> Its EnumFlag on 6.1 and RegistryFlag on 7.1
    public static StringFlag TIME_LOCK;

    // chat related flags
    public static @DefaultState(state = true) StateFlag SEND_CHAT;
    public static @DefaultState(state = true) StateFlag RECEIVE_CHAT;
    public static SetFlag<String> BLOCKED_CMDS;
    public static SetFlag<String> ALLOWED_CMDS;

    // locations
    public static LocationFlag TELE_LOC = (LocationFlag) getFlagFromWorldGuard("teleport");
    public static LocationFlag SPAWN_LOC = (LocationFlag) getFlagFromWorldGuard("spawn");


    // idk?
    public static @DefaultState(state = false) StateFlag INVINCIBILITY = (StateFlag) getFlagFromWorldGuard("invincible");
    public static @DefaultState(state = true) StateFlag FALL_DAMAGE;
    public static @DefaultState(state = true) StateFlag HEALTH_REGEN = (StateFlag) getFlagFromWorldGuard("natural-health-regen");
    public static @DefaultState(state = true) StateFlag HUNGER_DRAIN = (StateFlag) getFlagFromWorldGuard("natural-hunger-drain");

    // session and movement based flags
    public static @DefaultState(state = true) StateFlag ENTRY;
    public static @DefaultState(state = true) StateFlag EXIT;
    public static BooleanFlag EXIT_OVERRIDE;
    public static @DefaultState(state = true) StateFlag EXIT_VIA_TELEPORT;

    public static @DefaultState(state = true) StateFlag ENDERPEARL;
    public static @DefaultState(state = true) StateFlag CHORUS_TELEPORT;

    /**
     * @deprecated The type of this flag will change from a StringFlag to a ComponentFlag to support JSON text
     *              in a future release. If you depend on the type of this flag, take proper precaution for future breakage.
     */
    //@Deprecated
    public static StringFlag GREET_MESSAGE;
    /**
     * @deprecated The type of this flag will change from a StringFlag to a ComponentFlag to support JSON text
     *              in a future release. If you depend on the type of this flag, take proper precaution for future breakage.
     */
    //@Deprecated
    public static StringFlag FAREWELL_MESSAGE;
    /**
     * @deprecated The type of this flag will change from a StringFlag to a ComponentFlag to support JSON text
     *              in a future release. If you depend on the type of this flag, take proper precaution for future breakage.
     */
    //@Deprecated
    public static StringFlag GREET_TITLE;
    /**
     * @deprecated The type of this flag will change from a StringFlag to a ComponentFlag to support JSON text
     *              in a future release. If you depend on the type of this flag, take proper precaution for future breakage.
     */
    //@Deprecated
    public static StringFlag FAREWELL_TITLE;

    public static BooleanFlag NOTIFY_ENTER;
    public static BooleanFlag NOTIFY_LEAVE;

    public static Flag GAME_MODE; //<GameMode> Its EnumFlag on 6.1 and RegistryFlag on 7.1

    public static IntegerFlag HEAL_DELAY;
    public static IntegerFlag HEAL_AMOUNT;
    public static DoubleFlag MIN_HEAL;
    public static DoubleFlag MAX_HEAL;

    public static IntegerFlag FEED_DELAY;
    public static IntegerFlag FEED_AMOUNT;
    public static IntegerFlag MIN_FOOD;
    public static IntegerFlag MAX_FOOD;

    // deny messages
    /**
     * @deprecated The type of this flag will change from a StringFlag to a ComponentFlag to support JSON text
     *              in a future release. If you depend on the type of this flag, take proper precaution for future breakage.
     */
    //@Deprecated
    public static StringFlag DENY_MESSAGE = (StringFlag) getFlagFromWorldGuard("deny-message");
    /**
     * @deprecated The type of this flag will change from a StringFlag to a ComponentFlag to support JSON text
     *              in a future release. If you depend on the type of this flag, take proper precaution for future breakage.
     */
    //@Deprecated
    public static StringFlag ENTRY_DENY_MESSAGE = (StringFlag) getFlagFromWorldGuard("entry-deny-message");
    /**
     * @deprecated The type of this flag will change from a StringFlag to a ComponentFlag to support JSON text
     *              in a future release. If you depend on the type of this flag, take proper precaution for future breakage.
     */
    //@Deprecated
    public static StringFlag EXIT_DENY_MESSAGE = (StringFlag) getFlagFromWorldGuard("exit-deny-message");

    private static Flag<?> getFlagFromWorldGuard(final String flagName) {
        return WGPlatform.getInstance().getFlag(flagName);
    }

    static {
        for (Field declaredField : WGFlags.class.getDeclaredFields()) {
            try {
                if (declaredField.get(null) == null){
                    String flagName = declaredField.getName().replace("_","-").toLowerCase();
                    Flag flag = getFlagFromWorldGuard(flagName);
                    declaredField.set(null, flag);
                }
            } catch (Exception ignored) {
                //For now, lets fail-silent
                //System.out.println("[EverNifeCore] Failed to create symlink for the WorldGuardFlag: " + declaredField);
                //e.printStackTrace();
            }
        }
    }

    /**
     * This is a Descriminator for the Flags, to tell the default value of the Flag
     *
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    public static @interface DefaultState {

        boolean state();

    }
}
