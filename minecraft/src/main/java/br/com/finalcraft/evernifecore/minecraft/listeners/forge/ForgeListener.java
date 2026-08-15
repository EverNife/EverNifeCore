package br.com.finalcraft.evernifecore.minecraft.listeners.forge;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.listeners.forge.imp.ArclightForgeListener;
import br.com.finalcraft.evernifecore.minecraft.listeners.forge.imp.CrucibleForgeListener;
import br.com.finalcraft.evernifecore.minecraft.listeners.forge.imp.ModernMohistForgeListener;
import br.com.finalcraft.evernifecore.minecraft.listeners.forge.imp.MohistForgeListener;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import org.bukkit.plugin.Plugin;

public class ForgeListener {

    private static final IForgeListener INSTANCE = detectHybrid();

    /**
     * The adapter for the hybrid behind this server, or {@code null} when there is none.
     *
     * <p>It never throws. Building an adapter resolves Forge members by name and wires a bridge into
     * the server, and a platform that only half-matches would otherwise fail this class's
     * initialization - turning every later call, including {@link #isAvailable()}, into a
     * {@code NoClassDefFoundError}. Failing here costs the Forge route and nothing else.</p>
     */
    private static IForgeListener detectHybrid(){
        try {
            if (FCReflectionUtil.getClasses().isClassLoaded("io.github.crucible.api.CrucibleEventBus")){
                //Present on 1.7.10
                return new CrucibleForgeListener();
            }else if (FCReflectionUtil.getClasses().isClassLoaded("io.izzel.arclight.api.Arclight")){
                //Present on 1.12.2 and 1.16.5 and 1.20.x
                return new ArclightForgeListener();
            }else if (FCReflectionUtil.getClasses().isClassLoaded("com.mohistmc.forge.MohistEventBus")){
                //Present on 1.20.x
                return new ModernMohistForgeListener();
            }else if (FCReflectionUtil.getClasses().isClassLoaded("com.mohistmc.api.event.BukkitHookForgeEvent")){
                //present on 1.12.2 and 1.16.5
                return new MohistForgeListener();
            }else if (FCReflectionUtil.getClasses().isClassLoaded("catserver.api.bukkit.ForgeEventV2")){
                //present on 1.16.5; CatServer is served by the Mohist bridge and has no adapter of its own
                return new MohistForgeListener();
            }
        }catch (Throwable failure){
            reportUnavailable(failure);
        }
        return null;
    }

    private static void reportUnavailable(Throwable failure){
        try {
            EverNifeCore.getLog().severe("[ForgeListener] This server looks like a hybrid Bukkit+Forge"
                    + " platform, but the adapter for it could not be built. Nothing reaches the Forge side"
                    + " for the rest of this run.", failure);
        }catch (Throwable ignored){
            //Saying so is the one step that must not take the class initialization down with it.
        }
    }

    /**
     * Whether this server has a hybrid Forge platform behind it - the cheap question anything
     * bridging to the Forge side asks before it builds anything.
     *
     * <p>Answering it is what runs the detection above, so the first caller pays for building the
     * implementation and everyone after it reads a field.</p>
     */
    public static boolean isAvailable(){
        return INSTANCE != null;
    }

    public static void registerListener(Plugin plugin, ECListener listener, Object... eventBus){
        if (INSTANCE == null){
            throw new IllegalStateException("Tried to register ForgeEvents but there is no IForgeListener available for EverNifeCore on this Server.");
        }
        INSTANCE.registerListener(plugin, listener, eventBus);
    }

    public static void registerListener(Plugin plugin, ECListener listener){
        if (INSTANCE == null){
            throw new IllegalStateException("Tried to register ForgeEvents but there is no IForgeListener available for EverNifeCore on this Server.");
        }
        INSTANCE.registerListener(plugin, listener);
    }

}
