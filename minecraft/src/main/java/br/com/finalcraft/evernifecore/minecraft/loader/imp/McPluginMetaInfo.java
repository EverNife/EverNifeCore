package br.com.finalcraft.evernifecore.minecraft.loader.imp;

import br.com.finalcraft.evernifecore.ecplugin.IPluginMetaInfo;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class McPluginMetaInfo implements IPluginMetaInfo {

    private final JavaPlugin javaPlugin;

    public McPluginMetaInfo(JavaPlugin javaPlugin) {
        this.javaPlugin = javaPlugin;
    }

    @Override
    public String getName() {
        return javaPlugin.getName();
    }

    @Override
    public String getVersion() {
        return javaPlugin.getDescription().getVersion();
    }

    @Override
    public String getAuthor() {
        return javaPlugin.getDescription().getAuthors().size() > 0
                ? javaPlugin.getDescription().getAuthors().get(0).toString()
                : "Unknown";
    }

    @Override
    public String getGroup() {
        return "";
    }

    @Override
    public File getDataFolder() {
        return javaPlugin.getDataFolder();
    }

    @Override
    public Object getDelegate() {
        return javaPlugin;
    }
}
