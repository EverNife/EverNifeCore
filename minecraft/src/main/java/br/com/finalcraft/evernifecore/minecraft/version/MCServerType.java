package br.com.finalcraft.evernifecore.minecraft.version;

import br.com.finalcraft.evernifecore.minecraft.loader.EverNifeCoreBukkitPlugin;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import org.bukkit.Bukkit;

//This is a personal class for my OWN PERSONAL PRIVATE servers... don't use it, this class might change a lot over the time
public enum MCServerType {
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
    MCServerType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    private static MCServerType MCServerType = null;
    private static Boolean personalEverNifeServer = null;

    public static boolean isSkylords(){
        return getCurrent() == MCServerType.SKYLORDS;
    }

    public static boolean isSkylordsOrIDEAL(){
        return isIDEAL() || isSkylords();
    }

    public static boolean isIDEAL(){
        return getCurrent() == MCServerType.IDEAL;
    }

    public static boolean isDragonblock(){
        return getCurrent() == MCServerType.DRAGONBLOCK;
    }

    public static boolean isDecimation(){
        return getCurrent() == MCServerType.DECIMATION;
    }

    public static boolean isVanilla(){
        return !FCBukkitUtil.isModded();
    }

    public static boolean isVanillaFactions(){
        return getCurrent() == MCServerType.VANILLA_FACTIONS;
    }

    public static boolean isSkyHorizon(){
        return getCurrent() == MCServerType.SKYHORIZON;
    }

    public static boolean isPixelmon(){
        return getCurrent() == MCServerType.PIXELMON;
    }

    public static boolean isModdedServer(){
        return FCBukkitUtil.isModded();
    }

    public static boolean isSkyBlock(){
        return isSkyHorizon() || isSkylords();
    }

    public static MCServerType getCurrent() {
        return MCServerType != null ? MCServerType : (MCServerType = calculateServerType());
    }

    public static boolean isEverNifePersonalServer(){
        if (personalEverNifeServer == null){
            personalEverNifeServer = Bukkit.getPluginManager().isPluginEnabled("FinalCraftCore");
        }
        return personalEverNifeServer;
    }

    private static MCServerType calculateServerType() {

        if (!isEverNifePersonalServer()){
            return UNKNOWN;
        }

        if (FCBukkitUtil.isModded()){
            if (FCReflectionUtil.getClasses().isClassLoaded("com.pixelmonmod.pixelmon.Pixelmon")){
                return MCServerType.PIXELMON;
            }

            if (FCReflectionUtil.getClasses().isClassLoaded("br.com.finalcraft.dragonblockutils.DragonBlockUtils")){
                return MCServerType.DRAGONBLOCK;
            }

            if (FCReflectionUtil.getClasses().isClassLoaded("br.com.finalcraft.gppskyblock.GPPSkyBlock")){
                if (MCVersion.isLowerEquals(MCVersion.v1_7_10)){
                    return MCServerType.SKYLORDS;
                }else {
                    return MCServerType.SKYHORIZON;
                }
            }

            if (FCReflectionUtil.getClasses().isClassLoaded("br.com.finalcraft.evernifeworldrpg.EverNifeWorldRPG")){
                return MCServerType.IDEAL;
            }

            if (FCReflectionUtil.getClasses().isClassLoaded("br.com.finalcraft.evernifedarkdecimagick.EverNifeDarkDeciMagick")){
                return MCServerType.DECIMATION;
            }

            if (FCReflectionUtil.getClasses().isClassLoaded("com.vicmatskiv.mw.ModernWarfareMod")){
                return MCServerType.SURVIVALZ;
            }
        }

        if (EverNifeCoreBukkitPlugin.instance.getServer().getPluginManager().isPluginEnabled("Factions")){
            return MCServerType.VANILLA_FACTIONS;
        }

        return MCServerType.UNKNOWN;
    }

}
