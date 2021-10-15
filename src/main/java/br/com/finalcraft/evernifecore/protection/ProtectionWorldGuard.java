package br.com.finalcraft.evernifecore.protection;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.version.MCVersion;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WGBukkit;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.registry.SimpleFlagRegistry;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProtectionWorldGuard {

    public static WorldGuardPlugin getAPI(){
        return WGBukkit.getPlugin();
    }

    public static boolean canPvP(String playerName) {
        Player player = Bukkit.getPlayer(playerName);
        return canPvP(player);
    }

    public static boolean canPvP(Player player) {

        LocalPlayer localPlayer = WGBukkit.getPlugin().wrapPlayer(player);
        ApplicableRegionSet applicableRegionSet = getApplicableRegions(player.getLocation());

        if (!applicableRegionSet.testState(localPlayer, DefaultFlag.PVP)) {
            return false;
        }
        return true;
    }

    public static boolean canBuild(Player player, Block b){
        return isProtected(player,b);
    }

    public static boolean isProtected(Player player, Block b) {
        return !WGBukkit.getPlugin().canBuild(player, b);
    }

    public static boolean isOwnerOfAllRegionsAtLocation(Player player, Location location){
        LocalPlayer localPlayer = WGBukkit.getPlugin().wrapPlayer(player);
        return WGBukkit.getPlugin().getRegionManager(player.getWorld()).getApplicableRegions(location).isOwnerOfAll(localPlayer);
    }

    public static ProtectedRegion getRegionByName(@Nullable World world, String regionName){
        if (world != null) return ProtectionWorldGuard.getAPI().getRegionManager(world).getRegion(regionName);

        for (World aWorld : Bukkit.getWorlds()) {
            ProtectedRegion protectedRegion = ProtectionWorldGuard.getAPI().getRegionManager(aWorld).getRegion(regionName);
            if (protectedRegion != null) {
                return protectedRegion;
            }
        }

        return null;
    }

    public static World getWorldFromRegion(ProtectedRegion region){
        for (World aWorld : Bukkit.getWorlds()) {
            ProtectedRegion protectedRegion = ProtectionWorldGuard.getAPI().getRegionManager(aWorld).getRegion(region.getId());
            if (protectedRegion != null && protectedRegion == region) {
                return aWorld;
            }
        }
        return null;
    }

    public static boolean playerIsInsideRegion(Player player, String... regionName){
        for (String aRegionName : getRegionsNameAtPlayer(player)){
            for (String theRegionInQuestion : regionName){

                if (aRegionName.equalsIgnoreCase(theRegionInQuestion)){
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean playerIsInsideAnyRegion(Player player){
        return getRegionsAtPlayer(player).size() != 0;
    }

    public static boolean playerIsInsideRegion(Player player, List<String> regionName){
        for (String aRegionName : getRegionsNameAtPlayer(player)){
            for (String theRegionInQuestion : regionName){

                if (aRegionName.equalsIgnoreCase(theRegionInQuestion)){
                    return true;
                }
            }
        }
        return false;
    }

    public static List<Player> getAllPlayersInRegion(ProtectedRegion theRegion){
        List<Player> insideRegionPlayers = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()){
            Location playerLocation = player.getLocation();
            if (theRegion.contains(
                    playerLocation.getBlockX(),
                    playerLocation.getBlockY(),
                    playerLocation.getBlockZ())){
                insideRegionPlayers.add(player);
            }
        }
        return insideRegionPlayers;
    }

    public static List<Player> getAllPlayersInRegion(String... regionName){
        List<Player> insideRegionPlayers = new ArrayList<Player>();
        for (Player player : Bukkit.getOnlinePlayers()){
            if (playerIsInsideRegion(player,regionName)){
                insideRegionPlayers.add(player);
            }
        }
        return insideRegionPlayers;
    }

    public static List<String> getRegionsNameAtPlayer(Player player){
        List<String> regionsAtPlayer = new ArrayList<String>();

        for (ProtectedRegion protectedRegion : getRegionsAtPlayer(player)){
            regionsAtPlayer.add(protectedRegion.getId());
        }

        return regionsAtPlayer;
    }

    public static ApplicableRegionSet getRegionsSetAtLocation(Location location){
        return WGBukkit.getPlugin().getRegionContainer().get(location.getWorld()).getApplicableRegions(location);
    }

    public static ApplicableRegionSet getRegionsSetAtPlayer(Player player){
        return WGBukkit.getPlugin().getRegionContainer().get(player.getLocation().getWorld()).getApplicableRegions(player.getLocation());
    }

    public static Set<ProtectedRegion> getRegionsAtPlayer(Player player){
        return getRegionsAtLocatioin(player.getLocation());
    }

    public static Set<ProtectedRegion> getRegionsAtLocatioin(Location location){
        return WGBukkit.getPlugin().getRegionManager(location.getWorld()).getApplicableRegions(location).getRegions();
    }

    public static ApplicableRegionSet getApplicableRegions(Location location){
        return WGBukkit.getPlugin().getRegionManager(location.getWorld()).getApplicableRegions(location);
    }

    private static Field flagsListField = null;
    public static void registerFlag(Flag<?> flag, JavaPlugin javaPlugin){
        if (!MCVersion.isLegacy()){
            try {
                SimpleFlagRegistry simpleFlagRegistry = (SimpleFlagRegistry) ProtectionWorldGuard.getAPI().getFlagRegistry();
                Field flagsMapField = simpleFlagRegistry.getClass().getDeclaredField("flags");
                flagsMapField.setAccessible(true);

                ConcurrentMap<String, Flag<?>> flags = (ConcurrentMap<String, Flag<?>>) flagsMapField.get(simpleFlagRegistry);

                flags.remove(flag.getName().toLowerCase());
                flags.put(flag.getName().toLowerCase(), flag);

                schedulWorldGuardReload();
                javaPlugin.getLogger().info("[CustomFlagsRegister] Custom WorldGuard flag registered: " + flag.getName());
            }catch (Exception e){
                e.printStackTrace();
            }
            return;
        }
        try {
            if (flagsListField == null){
                flagsListField = DefaultFlag.class.getDeclaredField("flagsList");
                flagsListField.setAccessible(true);

                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(flagsListField, flagsListField.getModifiers() & ~Modifier.FINAL);
            }

            Flag<?>[] oldFlagsArray = (Flag<?>[]) flagsListField.get(null);
            Flag<?>[] theNewFlagsArray = new Flag[oldFlagsArray.length + 1];
            for (int i = 0; i < oldFlagsArray.length; i++) {
                theNewFlagsArray[i] = oldFlagsArray[i];
            }
            theNewFlagsArray[theNewFlagsArray.length - 1] = flag;
            flagsListField.set(null,theNewFlagsArray);
            javaPlugin.getLogger().info("[CustomFlagsRegister] Custom WorldGuard flag registered: " + flag.getName());
            schedulWorldGuardReload();
        }catch (Exception e){
            EverNifeCore.info("Failed to register flag: " + flag.getName());
            e.printStackTrace();
        }
    }
    private static AtomicBoolean scheduled = new AtomicBoolean(false);
    public static void schedulWorldGuardReload(){
        if (scheduled.get()) return;
        scheduled.set(true);
        new BukkitRunnable(){
            @Override
            public void run() {
                EverNifeCore.info("It seems no more customflags has been added, reloading all of them!");
                FCBukkitUtil.makeConsoleExecuteCommandFromAssyncThread("wg reload");
                scheduled.set(false);
            }
        }.runTaskLater(EverNifeCore.instance, 1);
    }
}
