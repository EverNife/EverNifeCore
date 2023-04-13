package br.com.finalcraft.evernifecore.config.settings;

import br.com.finalcraft.evernifecore.config.Config;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class FCSettingsScanner {

    public static void loadSettings(Class<?> settingsClass){
        Plugin plugin = JavaPlugin.getProvidingPlugin(settingsClass);
        SettingsData settingsData = settingsClass.getAnnotation(SettingsData.class);
        String configName = "config.yml";
        if (settingsData != null){
            configName = settingsData.config();
        }
        loadSettings(plugin, new Config(plugin, configName), settingsClass);
    }

    public static void loadSettings(Class<?> settingsClass, Config config){
        Plugin plugin = JavaPlugin.getProvidingPlugin(settingsClass);
        loadSettings(plugin, config, settingsClass);
    }

    public static void loadSettings(Plugin plugin, Config config, Class<?> settingsClass){

    }

}
