package br.com.finalcraft.evernifecore.version;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import org.bukkit.Bukkit;

//This is a personal class for my OWN PERSONAL PRIVATE servers... don't use it, this class might change a lot over the time
public enum ServerType {
    IDEAL("IDEAL"),
    SKYLORDS("Skylords"),
    SKYHORIZON("SkyHorizon"),
    DRAGONBLOCK("DragonBlock"),
    VANILLA_FACTIONS("Factions"),
    DECIMATION("Decimation"),
    SURVIVALZ("SurvivalZ"),
    PIXELMON("Pixelmon"),
    UNKNOWN("UNKNOWN");

    private final String name;
    ServerType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    private static ServerType serverType = null;
    private static Boolean personalEverNifeServer = null;

    public static boolean isSkylords(){
        return getCurrent() == ServerType.SKYLORDS;
    }

    public static boolean isSkylordsOrIDEAL(){
        return isIDEAL() || isSkylords();
    }

    public static boolean isIDEAL(){
        return getCurrent() == ServerType.IDEAL;
    }

    public static boolean isDragonblock(){
        return getCurrent() == ServerType.DRAGONBLOCK;
    }

    public static boolean isDecimation(){
        return getCurrent() == ServerType.DECIMATION;
    }

    public static boolean isVanilla(){
        return !FCBukkitUtil.isForge();
    }

    public static boolean isVanillaFactions(){
        return getCurrent() == ServerType.VANILLA_FACTIONS;
    }

    public static boolean isSkyHorizon(){
        return getCurrent() == ServerType.SKYHORIZON;
    }

    public static boolean isPixelmon(){
        return getCurrent() == ServerType.PIXELMON;
    }

    public static boolean isModdedServer(){
        return FCBukkitUtil.isForge();
    }

    public static boolean isSkyBlock(){
        return isSkyHorizon() || isSkylords();
    }

    public static ServerType getCurrent() {
        return serverType != null ? serverType : (serverType = calculateServerType());
    }

    public static boolean isEverNifePersonalServer(){
        if (personalEverNifeServer == null){
            personalEverNifeServer = Bukkit.getPluginManager().isPluginEnabled("FinalCraftCore");
        }
        return personalEverNifeServer;
    }

    private static ServerType calculateServerType() {

        if (!isEverNifePersonalServer()){
            return UNKNOWN;
        }

        if (FCBukkitUtil.isForge()){
            if (FCReflectionUtil.isClassLoaded("com.pixelmonmod.pixelmon.Pixelmon")){
                return ServerType.PIXELMON;
            }

            if (FCReflectionUtil.isClassLoaded("br.com.finalcraft.dragonblockutils.DragonBlockUtils")){
                return ServerType.DRAGONBLOCK;
            }

            if (FCReflectionUtil.isClassLoaded("br.com.finalcraft.gppskyblock.GPPSkyBlock")){
                if (MCVersion.isLowerEquals(MCVersion.v1_7_10)){
                    return ServerType.SKYLORDS;
                }else {
                    return ServerType.SKYHORIZON;
                }
            }

            if (FCReflectionUtil.isClassLoaded("br.com.finalcraft.evernifeworldrpg.EverNifeWorldRPG")){
                return ServerType.IDEAL;
            }

            if (FCReflectionUtil.isClassLoaded("br.com.finalcraft.evernifedarkdecimagick.EverNifeDarkDeciMagick")){
                return ServerType.DECIMATION;
            }

            if (FCReflectionUtil.isClassLoaded("com.vicmatskiv.mw.ModernWarfareMod")){
                return ServerType.SURVIVALZ;
            }
        }

        if (EverNifeCore.instance.getServer().getPluginManager().isPluginEnabled("Factions")){
            return ServerType.VANILLA_FACTIONS;
        }

        return ServerType.UNKNOWN;
    }

}
