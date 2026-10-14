package br.com.finalcraft.evernifecore.hytale.loader.imp;

import br.com.finalcraft.evernifecore.ecplugin.IPluginMetaInfo;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import java.io.File;

public class HyPluginMetaInfo implements IPluginMetaInfo {

    private final JavaPlugin javaPlugin;

    public HyPluginMetaInfo(JavaPlugin javaPlugin) {
        this.javaPlugin = javaPlugin;
    }

    /**
     * The manifest {@code Name} alone. Hytale's own {@code getName()} is {@code Group:Name}, and the core uses
     * this name as a region/flag owner, a map key and a file name - none of which take a {@code :}.
     */
    @Override
    public String getName() {
        return javaPlugin.getIdentifier().getName();
    }

    @Override
    public String getVersion() {
        return javaPlugin.getManifest().getVersion().toString();
    }

    @Override
    public String getAuthor() {
        return javaPlugin.getManifest().getAuthors().size() > 0
                ? javaPlugin.getManifest().getAuthors().get(0).getName()
                : "Unknown";
    }

    @Override
    public String getGroup() {
        return javaPlugin.getManifest().getGroup();
    }

    @Override
    public File getDataFolder() {
        return new File("mods/" + javaPlugin.getIdentifier().getGroup() + "_" + javaPlugin.getIdentifier().getName() + "/");
    }

    @Override
    public Object getDelegate() {
        return javaPlugin;
    }
}
