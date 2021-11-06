package br.com.finalcraft.evernifecore.dependencies.util;

import net.byteflux.libby.BukkitLibraryManager;
import net.byteflux.libby.Library;
import org.bukkit.plugin.Plugin;

import java.util.regex.Pattern;

public class LibraryUtil {

    public static Library fromString(String mavenDependency){

        String[] split = mavenDependency.split(Pattern.quote(":"));

        return Library
                .builder()
                .groupId(split[0])
                .artifactId(split[1])
                .version(split[2])
                .build();
    }

    public static BukkitLibraryManager getManager(Plugin plugin){
        BukkitLibraryManager bukkitLibraryManager = new BukkitLibraryManager(plugin);
        bukkitLibraryManager.addJitPack();
        bukkitLibraryManager.addJCenter();
        bukkitLibraryManager.addMavenCentral();
        bukkitLibraryManager.addSonatype();
        bukkitLibraryManager.addRepository("https://maven.petrus.dev/public");
        return bukkitLibraryManager;
    }

}
