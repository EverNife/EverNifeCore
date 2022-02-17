package br.com.finalcraft.evernifecore.ecplugin;

import br.com.finalcraft.evernifecore.commands.finalcmd.tab.TabCompleteParser;
import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class ECPlugin {

    private final Plugin plugin;
    private final String pluginLanguage;

    private HashSet<Class> LOCALIZED_CLASSES = new HashSet<>();
    private Map<String, TabCompleteParser> TAB_PARSERS = new HashMap();

    private Config customLangConfig = null;

    public ECPlugin(Plugin plugin) {
        this.plugin = plugin;

        Config localization_config = new Config(this.plugin, "localization/localization_config.yml");
        this.pluginLanguage = localization_config.getOrSetDefaultValue("Localization.fileName", "lang_" + FCLocaleManager.DEFAULT_EVERNIFECORE_LOCALE + ".yml")
                .replace(".yml","")
                .replace("lang_","");
        localization_config.saveIfNewDefaults();

        this.plugin.getLogger().info("[FCLocale] Setting locale to [" + pluginLanguage +"]!");

        clearLocalesAndLoadConfig();
    }

    public void addLocaleClass(Class... classes){
        LOCALIZED_CLASSES.addAll(Arrays.asList(classes));
    }

    public HashSet<Class> clearLocalesAndLoadConfig(){
        HashSet<Class> oldLocalizedClasses = this.LOCALIZED_CLASSES;
        this.LOCALIZED_CLASSES = new HashSet<>();

        //Check if the current lang is hardcoded (LocaleType.ENUM)
        boolean isHardcodedLocale = Arrays.stream(LocaleType.values()).map(Enum::name).filter(s -> s.equals(this.getPluginLanguage())).findAny().isPresent();
        this.customLangConfig = isHardcodedLocale ? null : new Config(plugin, "localization/lang_" + this.getPluginLanguage() + ".yml");

        return oldLocalizedClasses;
    }

    public String getPluginLanguage() {
        return pluginLanguage;
    }

    public HashSet<Class> getLocalizedClasses() {
        return LOCALIZED_CLASSES;
    }

    @Nullable
    public Config getCustomLangConfig(){
        return this.customLangConfig;
    }
}
