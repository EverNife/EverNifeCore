package br.com.finalcraft.evernifecore.version;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;

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

    private static ServerType serverType;
    private static boolean moddedServer = false;

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
        return !moddedServer;
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
        return moddedServer;
    }

    public static boolean isSkyBlock(){
        return isSkyHorizon() || isSkylords();
    }

    public static ServerType getCurrent() {
        return serverType != null ? serverType : (serverType = calculateServerType());
    }

    public static boolean isEverNifePersonalServer(){
        return getCurrent() != ServerType.UNKNOWN;
    }

    private static ServerType calculateServerType() {

        if (!FCBukkitUtil.isClassLoaded("br.com.finalcraft.FinalCraftCore")){
            return ServerType.UNKNOWN;
        }

        if (FCBukkitUtil.isClassLoaded("br.com.finalcraft.dragonblockutils.DragonBlockUtils")){
            moddedServer = true;
            return ServerType.PIXELMON;
        }

        if (FCBukkitUtil.isClassLoaded("br.com.finalcraft.dragonblockutils.DragonBlockUtils")){
            moddedServer = true;
            return ServerType.DRAGONBLOCK;
        }

        if (FCBukkitUtil.isClassLoaded("br.com.finalcraft.gppskyblock.GPPSkyBlock")){
            moddedServer = true;
            if (MCVersion.isLegacy()) return ServerType.SKYLORDS;
            return ServerType.SKYHORIZON;
        }

        if (FCBukkitUtil.isClassLoaded("br.com.finalcraft.evernifeworldrpg.EverNifeWorldRPG")){
            moddedServer = true;
            return ServerType.IDEAL;
        }

        if (FCBukkitUtil.isClassLoaded("br.com.finalcraft.evernifedarkdecimagick.EverNifeDarkDeciMagick")){
            moddedServer = true;
            return ServerType.DECIMATION;
        }

        if (FCBukkitUtil.isClassLoaded("com.vicmatskiv.mw.ModernWarfareMod")){
            moddedServer = true;
            return ServerType.SURVIVALZ;
        }

        if (EverNifeCore.instance.getServer().getPluginManager().isPluginEnabled("Factions")){
            return ServerType.VANILLA_FACTIONS;
        }

        if (moddedServer == false){
            moddedServer = FCBukkitUtil.isClassLoaded("br.com.finalcraft.everforgelib.EverForgeLib");
        }

        return ServerType.UNKNOWN;
    }

}
