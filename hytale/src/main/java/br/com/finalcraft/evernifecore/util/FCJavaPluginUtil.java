package br.com.finalcraft.evernifecore.util;

import com.google.common.base.Preconditions;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.PluginClassLoader;
import jakarta.annotation.Nonnull;

import java.io.File;

public class FCJavaPluginUtil {

    public static JavaPlugin getProvidingPlugin(@Nonnull Class<?> clazz) {
        Preconditions.checkArgument(clazz != null, "Null class cannot have a plugin");
        final ClassLoader cl = clazz.getClassLoader();
        if (!(cl instanceof PluginClassLoader)) {
            throw new IllegalArgumentException(clazz + " is not provided by " + PluginClassLoader.class);
        }
        JavaPlugin plugin = (JavaPlugin) FCReflectionUtil.getField(PluginClassLoader.class, "plugin").get(cl);
        if (plugin == null) {
            throw new IllegalStateException("Cannot get plugin for " + clazz + " from a static initializer");
        }
        return plugin;
    }


    public static File getDataFolder(JavaPlugin javaPlugin){
        return new File("mods/" + javaPlugin.getIdentifier().getGroup() + "_" + javaPlugin.getIdentifier().getName() + "/");
    }
}
