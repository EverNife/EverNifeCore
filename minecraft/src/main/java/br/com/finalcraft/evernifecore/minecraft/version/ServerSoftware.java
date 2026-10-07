package br.com.finalcraft.evernifecore.minecraft.version;

import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;

import java.util.Arrays;
import java.util.List;

/**
 * The server software this plugin runs on, told apart by a class only that software ships.
 *
 * <p>Declaration order is detection order, and it matters: every hybrid bundles a Paper or Spigot
 * API, and Youer is a MohistMC build, so each software is declared before anything it carries.</p>
 */
public enum ServerSoftware {
    CRUCIBLE(true, "io.github.crucible.api.CrucibleEventBus"),
    ARCLIGHT(true, "io.izzel.arclight.api.Arclight"),
    YOUER(true, "com.mohistmc.youer.Youer"),
    MOHIST(true, "com.mohistmc.forge.MohistEventBus", "com.mohistmc.api.event.BukkitHookForgeEvent"),
    CATSERVER(true, "catserver.api.bukkit.ForgeEventV2"),
    PAPER(false, "io.papermc.paper.configuration.Configuration", "com.destroystokyo.paper.PaperConfig"),
    SPIGOT(false, "org.spigotmc.SpigotConfig"),
    CRAFTBUKKIT(false, "org.bukkit.craftbukkit.Main"),
    UNKNOWN(false);

    private static volatile ServerSoftware current;

    private final boolean hybrid;
    private final List<String> markers;

    ServerSoftware(boolean hybrid, String... markers) {
        this.hybrid = hybrid;
        this.markers = Arrays.asList(markers);
    }

    /** Whether this software runs Bukkit plugins next to Forge or NeoForge mods. */
    public boolean isHybrid() {
        return hybrid;
    }

    public boolean isCurrent() {
        return getCurrent() == this;
    }

    /** The software of this server, detected on the first call. */
    public static ServerSoftware getCurrent() {
        ServerSoftware software = current;
        if (software == null) {
            current = software = detect(className -> FCReflectionUtil.getClasses().isClassLoaded(className));
        }
        return software;
    }

    /**
     * The first software, in declaration order, with one of its marker classes on {@code runtime};
     * {@link #UNKNOWN} when none matches.
     *
     * @param runtime which classes the server carries, a parameter so a test can pose a server this
     *                JVM is not.
     */
    public static ServerSoftware detect(ClassPresence runtime) {
        for (ServerSoftware software : values()) {
            for (String marker : software.markers) {
                if (runtime.isLoaded(marker)) {
                    return software;
                }
            }
        }
        return UNKNOWN;
    }

    /** Whether a class name resolves on the runtime being asked about. */
    public interface ClassPresence {
        boolean isLoaded(String className);
    }

}
