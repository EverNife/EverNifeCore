package br.com.finalcraft.evernifecore.protection.worldguard;

import com.sk89q.worldguard.protection.flags.*;

import java.lang.reflect.Field;


//This class is a mirror of https://github.com/EngineHub/WorldGuard/blob/master/worldguard-core/src/main/java/com/sk89q/worldguard/protection/flags/Flags.java
// I had to do this because this class has change it's name
// from "DefaultFlags" to "Flags" between WorldGuard 6.0 and 7.0
public class WGFlags {

    // Overrides membership check
    public static StateFlag PASSTHROUGH;

    // This flag is unlike the others. It forces the checking of region membership
    public static StateFlag BUILD;

    // These flags are used in tandem with the BUILD flag - if the player can
    // build, then the following flags do not need to be checked (although they
    // are still checked for DENY), so they are false by default
    public static StateFlag BLOCK_BREAK;
    public static StateFlag BLOCK_PLACE;
    public static StateFlag USE;
    public static StateFlag INTERACT;
    public static StateFlag DAMAGE_ANIMALS;
    public static StateFlag PVP;
    public static StateFlag SLEEP;
    public static StateFlag RESPAWN_ANCHORS;
    public static StateFlag TNT;
    public static StateFlag CHEST_ACCESS;
    public static StateFlag PLACE_VEHICLE = (StateFlag) getFlagFromWorldGuard("vehicle-place");
    public static StateFlag DESTROY_VEHICLE = (StateFlag) getFlagFromWorldGuard("vehicle-destroy");
    public static StateFlag LIGHTER;
    public static StateFlag RIDE;
    public static StateFlag POTION_SPLASH;
    public static StateFlag ITEM_FRAME_ROTATE = (StateFlag) getFlagFromWorldGuard("item-frame-rotation");
    public static StateFlag TRAMPLE_BLOCKS = (StateFlag) getFlagFromWorldGuard("block-trampling");
    public static StateFlag FIREWORK_DAMAGE;
    public static StateFlag USE_ANVIL;
    public static StateFlag USE_DRIPLEAF;

    // These flags are similar to the ones above (used in tandem with BUILD),
    // but their defaults are set to TRUE because it is more user friendly.
    // However, it is not possible to disable these flags by default in all
    // regions because setting DENY in __global__ would also override the
    // BUILD flag. In the future, StateFlags will need a DISALLOW state.
    public static StateFlag ITEM_PICKUP; // Intentionally true
    public static StateFlag ITEM_DROP; // Intentionally true
    public static StateFlag EXP_DROPS; // Intentionally true

    // These flags adjust behavior and are not checked in tandem with the
    // BUILD flag so they need to be TRUE for their defaults.

    // mob griefing related
    public static StateFlag MOB_DAMAGE;
    public static StateFlag CREEPER_EXPLOSION;
    public static StateFlag ENDERDRAGON_BLOCK_DAMAGE;
    public static StateFlag GHAST_FIREBALL;
    public static StateFlag OTHER_EXPLOSION;
    public static StateFlag WITHER_DAMAGE;
    public static StateFlag ENDER_BUILD = (StateFlag) getFlagFromWorldGuard("enderman-grief");
    public static StateFlag SNOWMAN_TRAILS;
    public static StateFlag RAVAGER_RAVAGE;
    public static StateFlag ENTITY_PAINTING_DESTROY;
    public static StateFlag ENTITY_ITEM_FRAME_DESTROY;

    // mob spawning related
    public static StateFlag MOB_SPAWNING;
    public static SetFlag DENY_SPAWN;

    // block dynamics
    public static StateFlag PISTONS;
    public static StateFlag FIRE_SPREAD;
    public static StateFlag LAVA_FIRE;
    public static StateFlag LIGHTNING;
    public static StateFlag SNOW_FALL;
    public static StateFlag SNOW_MELT;
    public static StateFlag ICE_FORM;
    public static StateFlag ICE_MELT;
    public static StateFlag FROSTED_ICE_MELT;
    public static StateFlag FROSTED_ICE_FORM; // this belongs in the first category of "checked with build"
    public static StateFlag MUSHROOMS = (StateFlag) getFlagFromWorldGuard("mushroom-growth");
    public static StateFlag LEAF_DECAY;
    public static StateFlag GRASS_SPREAD = (StateFlag) getFlagFromWorldGuard("grass-growth");
    public static StateFlag MYCELIUM_SPREAD;
    public static StateFlag VINE_GROWTH;
    public static StateFlag ROCK_GROWTH;
    public static StateFlag SCULK_GROWTH;
    public static StateFlag CROP_GROWTH;
    public static StateFlag SOIL_DRY;
    public static StateFlag CORAL_FADE;
    public static StateFlag WATER_FLOW;
    public static StateFlag LAVA_FLOW;

    public static Flag WEATHER_LOCK; //<WeatherType> Its EnumFlag on 6.1 and RegistryFlag on 7.1
    public static StringFlag TIME_LOCK;

    // chat related flags
    public static StateFlag SEND_CHAT;
    public static StateFlag RECEIVE_CHAT;
    public static SetFlag<String> BLOCKED_CMDS;
    public static SetFlag<String> ALLOWED_CMDS;

    // locations
    public static LocationFlag TELE_LOC = (LocationFlag) getFlagFromWorldGuard("teleport");
    public static LocationFlag SPAWN_LOC = (LocationFlag) getFlagFromWorldGuard("spawn");


    // idk?
    public static StateFlag INVINCIBILITY;
    public static StateFlag FALL_DAMAGE;
    public static StateFlag HEALTH_REGEN;
    public static StateFlag HUNGER_DRAIN;

    // session and movement based flags
    public static StateFlag ENTRY;
    public static StateFlag EXIT;
    public static BooleanFlag EXIT_OVERRIDE;
    public static StateFlag EXIT_VIA_TELEPORT;

    public static StateFlag ENDERPEARL;
    public static StateFlag CHORUS_TELEPORT;

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
}
