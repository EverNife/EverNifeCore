package br.com.finalcraft.evernifecore.protection.worldguard;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.protection.worldguard.adapter.FCRegionManager;
import br.com.finalcraft.evernifecore.protection.worldguard.adapter.FCRegionResultSet;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.registry.SimpleFlagRegistry;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class WGPlatform {

    private static final WGPlatform INSTANCE;

    static {
        String wgVersion = Bukkit.getPluginManager().getPlugin("WorldGuard").getDescription().getVersion();
        String apiClassname = wgVersion.startsWith("6.1")
                ? "v1_7_R4" //WorldGuard 6.0
                : wgVersion.startsWith("6.2")
                ? "v1_12_R2" //WorldGuard 6.2
                : "v1_16_R3"; //WorldGuard 7.*

        WGPlatform wgPlatform = null;
        try {
            wgPlatform = (WGPlatform) Class.forName("br.com.finalcraft.evernifecore.compat." + apiClassname + ".protection.worldguard.ImpWGPlatform").newInstance();
        } catch (Throwable e) {
            System.out.println("[EverNifeCore] You do not have WorldGuard present or you did not waited for EverNifeCore to be fully StartedUp!");
            e.printStackTrace();
        }
        INSTANCE = wgPlatform;
    }

    public static WGPlatform getInstance(){
        return INSTANCE;
    }


    public abstract IFCFlagRegistry getFlagRegistry();

    public abstract FCRegionManager getRegionManager(World world);

    public abstract FCWorldGuardRegion wrapRegion(@Nonnull World world, ProtectedRegion protectedRegion);

    protected abstract FCWorldGuardRegion createFCWorldGuardRegion(String id, BlockPos pt1, BlockPos pt2);

    protected abstract FCWorldGuardRegion createFCWorldGuardRegion(String id, boolean isTransient, BlockPos pt1, BlockPos pt2);

    public LocalPlayer wrapPlayer(@Nonnull OfflinePlayer player){
        if (player.isOnline()){
            return WorldGuardPlugin.inst().wrapPlayer((Player) player);
        }else {
            return WorldGuardPlugin.inst().wrapOfflinePlayer(player);
        }
    }

    public FCRegionResultSet getApplicableRegions(@Nonnull Location location){
        return getRegionManager(location.getWorld()).getApplicableRegions(location);
    }

    public FCWorldGuardRegion getRegionByID(@Nullable World world, @Nonnull String regionID) {
        regionID = regionID.toLowerCase();
        if (world != null) {
            return this.getRegionManager(world).getRegion(regionID);
        }

        for (World aWorld : Bukkit.getWorlds()) {
            FCWorldGuardRegion protectedRegion = this.getRegionManager(aWorld).getRegion(regionID);
            if (protectedRegion != null) {
                return protectedRegion;
            }
        }

        return null;
    }

    public void registerFlag(@Nonnull Flag<?> flag, @Nonnull Plugin plugin) {
        try {
            //This is the default way of registering a flag on WorldGuard for all MC versions, expect for 1.7.10
            // SO i override it only there!
            SimpleFlagRegistry flagRegistry = (SimpleFlagRegistry) getFlagRegistry().getHandle();//NULL only on 1.7.10
            Field flagsMapField = flagRegistry.getClass().getDeclaredField("flags");
            flagsMapField.setAccessible(true);

            ConcurrentMap<String, Flag<?>> flags = (ConcurrentMap<String, Flag<?>>) flagsMapField.get(flagRegistry);

            flags.remove(flag.getName().toLowerCase());
            flags.put(flag.getName().toLowerCase(), flag);
            plugin.getLogger().info("[EverNifeCore -> WorldGuard] Custom Flag registered: " + flag.getName());
            schedulWorldGuardReload();
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public Flag getFlag(@Nonnull String flagName){
        return getFlagRegistry().get(flagName);
    }

    protected final AtomicBoolean scheduled = new AtomicBoolean(false);
    protected void schedulWorldGuardReload(){
        if (scheduled.get() == true){
            return;
        }

        JavaPlugin everNifeCore = JavaPlugin.getProvidingPlugin(this.getClass());

        scheduled.set(true);
        new BukkitRunnable(){
            @Override
            public void run() {
                everNifeCore.getLogger().info("[WGPlatform] It seems no more customflags has been added, reloading all of them!");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "wg reload");
                scheduled.set(false);
            }
        }.runTaskLater(everNifeCore, 1);
    }
}
