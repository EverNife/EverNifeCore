package br.com.finalcraft.evernifecore.minecraft.worldedit;

import com.sk89q.worldedit.world.World;
import jakarta.annotation.Nullable;

import java.lang.reflect.Method;

/**
 * The WorldEdit Forge mod on a 1.7.10 hybrid server. There the Bukkit plugin's NMS adapter does not load (the server's
 * NMS carries Forge names), so a {@code BukkitWorld} reads and writes no tile entity data; the mod's own world does.
 * The mod is reached by reflection, so nothing compiles against a Forge class.
 */
public final class WorldEditForge {

    public static final String MOD_URL = "https://github.com/GTNewHorizons/worldedit-gtnh";
    private static final String FORGE_WORLDEDIT = "com.sk89q.worldedit.forge.ForgeWorldEdit";

    private WorldEditForge() {
    }

    /** Whether the WorldEdit Forge mod is loaded on this server. */
    public static boolean isModPresent() {
        try {
            Class.forName(FORGE_WORLDEDIT, false, WorldEditForge.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
    }

    /** Whether the mod's world stands in for the {@code BukkitWorld}: only on 1.7.10, where the plugin has no working adapter. */
    public static boolean replacesBukkitWorld(boolean is1_7_10, boolean modPresent) {
        return is1_7_10 && modPresent;
    }

    /** The console warning for a 1.7.10 server running the WorldEdit plugin without the mod; null when nothing is missing. */
    public static @Nullable String bootWarning(boolean is1_7_10, boolean bukkitPluginPresent, boolean modPresent) {
        if (!is1_7_10 || !bukkitPluginPresent || modPresent) {
            return null;
        }
        return "WorldEdit Forge was not detected. On 1.7.10 the WorldEdit plugin cannot read or write tile entities, so a region"
                + " snapshot comes back with empty chests and blank signs (the same loss WorldEdit warns about above). Install"
                + " WorldEdit Forge in mods/, next to the plugin: " + MOD_URL;
    }

    /** The mod's world for this Bukkit world: {@code ForgeWorldEdit.inst.getWorld(craftWorld.getHandle())}. */
    public static World getWorld(org.bukkit.World world) {
        try {
            Class<?> forge = Class.forName(FORGE_WORLDEDIT, true, WorldEditForge.class.getClassLoader());
            Object instance = forge.getField("inst").get(null);
            Object handle = world.getClass().getMethod("getHandle").invoke(world);
            for (Method method : forge.getMethods()) {
                if (method.getName().equals("getWorld") && method.getParameterTypes().length == 1
                        && method.getParameterTypes()[0].isInstance(handle)) {
                    return (World) method.invoke(instance, handle);
                }
            }
            throw new IllegalStateException(FORGE_WORLDEDIT + " has no getWorld(World) that takes " + handle.getClass().getName()
                    + " - this WorldEdit Forge build is not one the core knows; use " + MOD_URL + ".");
        } catch (ReflectiveOperationException failed) {
            throw new IllegalStateException("WorldEdit Forge is loaded but its world for " + world.getName() + " could not be reached"
                    + " (" + failed + ") - use the build from " + MOD_URL + ".", failed);
        }
    }
}
