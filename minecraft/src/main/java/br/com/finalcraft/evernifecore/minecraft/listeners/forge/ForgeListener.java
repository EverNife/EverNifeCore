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

    private static final String NEOFORGE_BUS = "net.neoforged.bus.api.IEventBus";
    private static final String FORGE_HOME = "net.minecraftforge.common.MinecraftForge";

    private static final IForgeListener INSTANCE = detectHybrid(ForgeListener::isLoadedHere);

    /**
     * The adapter for the hybrid behind this server, or {@code null} when nothing here can reach its
     * Forge side. Brand and era are separate questions and both get asked: one hybrid brand spans
     * Minecraft eras that disagree on which package the Forge types live in.
     *
     * <p>It never throws. Building an adapter resolves Forge members by name and wires a bridge into
     * the server, and a platform that only half-matches would otherwise fail this class's
     * initialization - turning every later call, including {@link #isAvailable()}, into a
     * {@code NoClassDefFoundError}. Failing here costs the Forge route and nothing else.</p>
     *
     * @param runtime which classes this server carries, taken as a parameter so a test with no server
     *                behind it can pose a platform this JVM does not have.
     */
    static IForgeListener detectHybrid(ClassPresence runtime){
        try {
            if (runtime.isLoaded("io.github.crucible.api.CrucibleEventBus")){
                //Present on 1.7.10
                return new CrucibleForgeListener();
            }else if (runtime.isLoaded("io.izzel.arclight.api.Arclight")){
                if (speaksNeoForgeOnly(runtime)){
                    reportEraNotServed();
                    return null;
                }
                //Present on 1.12.2 and 1.16.5 and 1.20.x
                return new ArclightForgeListener();
            }else if (runtime.isLoaded("com.mohistmc.forge.MohistEventBus")){
                //Present on 1.20.x
                return new ModernMohistForgeListener();
            }else if (runtime.isLoaded("com.mohistmc.api.event.BukkitHookForgeEvent")){
                //present on 1.12.2 and 1.16.5
                return new MohistForgeListener();
            }else if (runtime.isLoaded("catserver.api.bukkit.ForgeEventV2")){
                //present on 1.16.5; CatServer is served by the Mohist bridge and has no adapter of its own
                return new MohistForgeListener();
            }
        }catch (Throwable failure){
            reportUnavailable(failure);
        }
        return null;
    }

    /**
     * Whether this runtime's Forge side is NeoForge with no {@code net.minecraftforge} left to reach -
     * the era the Arclight branch cannot serve, because its one entry point takes a bus typed as
     * {@code net.minecraftforge.eventbus.api.IEventBus} and NeoForge renamed that whole tree.
     */
    static boolean speaksNeoForgeOnly(ClassPresence runtime){
        return runtime.isLoaded(NEOFORGE_BUS) && !runtime.isLoaded(FORGE_HOME);
    }

    /** Whether a class name resolves on the runtime being asked about. */
    interface ClassPresence {
        boolean isLoaded(String className);
    }

    private static boolean isLoadedHere(String className){
        return FCReflectionUtil.getClasses().isClassLoaded(className);
    }

    private static void reportEraNotServed(){
        try {
            EverNifeCore.getLog().warning("[ForgeListener] This server is an Arclight hybrid whose Forge"
                    + " side is NeoForge: '{}' is on this runtime and '{}' is not. Everything this route"
                    + " reaches for it reaches by name under net.minecraftforge, so the route is refused"
                    + " here rather than dying inside the server's own class remapper. Bukkit listeners"
                    + " and every other EverNifeCore feature are unaffected - ask"
                    + " ForgeListener.isAvailable() before building a Forge route. This is said once.",
                    NEOFORGE_BUS, FORGE_HOME);
        }catch (Throwable ignored){
            //Saying so is the one step that must not take the class initialization down with it.
        }
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
     * Whether a Forge route can be built on this server - the cheap question anything bridging to the
     * Forge side asks before it builds anything. A hybrid running an era no adapter serves answers
     * {@code false} here, the same as a plain Bukkit server does: what a caller needs to know is
     * whether anything of theirs would arrive, not which brand is underneath.
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
