package br.com.finalcraft.evernifecore.ecplugin;

import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;

public class ECPlugin {

    private final Plugin plugin;
    private final String pluginLanguage;

    private HashMap<String,LocaleMessageImp> localizedMessages = new HashMap();

    private Config customLangConfig = null;
    private final HashMap<LocaleType, Config> hardcodedLocalizations = new HashMap<>();
    private boolean markedForLocaleReload = false;

    public ECPlugin(Plugin plugin) {
        this.plugin = plugin;

        Config localization_config = new Config(this.plugin, "localization/localization_config.yml");
        this.pluginLanguage = localization_config.getOrSetDefaultValue("Localization.fileName", "lang_" + FCLocaleManager.DEFAULT_EVERNIFECORE_LOCALE + ".yml")
                .replace(".yml","")
                .replace("lang_","");
        localization_config.saveIfNewDefaults();

        for (LocaleType type : LocaleType.values()) {
            Config lang = new Config(plugin, "localization/lang_" + type.name() + ".yml");
            hardcodedLocalizations.put(type, lang);
        }

        this.plugin.getLogger().info("[FCLocale] Setting locale to [" + pluginLanguage +"]!");

        reloadAllCustomLocales();
    }

    public void addLocale(LocaleMessageImp localeMessageImp){
        LocaleMessageImp previous = localizedMessages.put(localeMessageImp.getKey(), localeMessageImp);
        if (previous != null){
            //IF we are re-adding a locale that means the plugins needs a reload
            markedForLocaleReload = true;
        }
    }

    public void reloadAllCustomLocales(){
        markedForLocaleReload = false;
        //This code will only execute if the plugin is using a custom locale.
        //If the plugin is using a HardcodedLocale there is no need to reload anything at all
        boolean isHardcodedLocale = Arrays.stream(LocaleType.values()).map(Enum::name).filter(s -> s.equals(this.getPluginLanguage())).findAny().isPresent();

        this.customLangConfig = isHardcodedLocale ? null : new Config(plugin, "localization/lang_" + this.getPluginLanguage() + ".yml");

        if (this.customLangConfig == null){
            return;
        }

        for (LocaleMessageImp localeMessage : localizedMessages.values()) {
            //Set a DefaultValue for this Custom LocaleMessage based on the ENGLISH hardcoded LocaleMessage
            FancyText englishFancyText = localeMessage.getFancyText(LocaleType.EN_US.name());
            FancyText customfancyText = getCustomLangConfig().getFancyText(localeMessage.getKey());
            localeMessage.addLocale(getPluginLanguage(), customfancyText != null ? customfancyText : englishFancyText);
        }
    }

    public HashMap<String, LocaleMessageImp> getLocalizedMessages() {
        return localizedMessages;
    }

    public boolean isMarkedForLocaleReload() {
        return markedForLocaleReload;
    }

    public String getPluginLanguage() {
        return pluginLanguage;
    }

    @Nullable
    public Config getCustomLangConfig(){
        return this.customLangConfig;
    }

    public HashMap<LocaleType, Config> getHardcodedLocalizations() {
        return hardcodedLocalizations;
    }
}