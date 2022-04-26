package br.com.finalcraft.evernifecore.ecplugin;

import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.commons.Tuple;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class ECPlugin {

    private final Plugin plugin;
    private String updateLink = null;
    private String pluginLanguage;
    private HashMap<String,LocaleMessageImp> localizedMessages = new HashMap();

    private Config localization_config;
    private Config customLangConfig;
    private final List<Tuple<LocaleType, Config>> hardcodedLocalizations = new ArrayList<>();
    private boolean markedForLocaleReload = false;

    public ECPlugin(Plugin plugin) {
        this.plugin = plugin;

        for (LocaleType type : LocaleType.values()) {
            Config lang = new Config(plugin, "localization/lang_" + type.name() + ".yml");
            hardcodedLocalizations.add(Tuple.of(type, lang));
        }

        this.plugin.getLogger().info("[FCLocale] Setting locale to [" + pluginLanguage +"]!");

        reloadAllCustomLocales();
    }

    public void addLocale(LocaleMessageImp localeMessageImp){
        localizedMessages.put(localeMessageImp.getKey(), localeMessageImp);
        if (localeMessageImp.needToBeSynced()){
            markedForLocaleReload = true;
        }
    }

    public void reloadAllCustomLocales(){
        markedForLocaleReload = false;

        boolean requiredEntireReload = false;
        //Check for the locale name again
        if (this.localization_config == null || this.localization_config.hasBeenModified()){
            this.localization_config = new Config(this.plugin, "localization/localization_config.yml");
            this.pluginLanguage = localization_config.getOrSetDefaultValue("Localization.fileName", "lang_" + FCLocaleManager.DEFAULT_EVERNIFECORE_LOCALE + ".yml")
                    .replace(".yml","")
                    .replace("lang_","");
            localization_config.saveIfNewDefaults();
            requiredEntireReload=true;
        }

        //If the plugin is using a HardcodedLocale there is no need to make great changes
        boolean isHardcodedLocale = Arrays.stream(LocaleType.values()).map(Enum::name).filter(s -> s.equals(this.getPluginLanguage())).findAny().isPresent();

        if (!isHardcodedLocale &&
                (this.customLangConfig == null //There was no config, first load of the plugin
                        || this.customLangConfig.hasBeenModified() //The config has been modified
                        || !this.customLangConfig.getTheFile().getName().equals("lang_" + this.getPluginLanguage() + ".yml") //The language name has been changed
                )){
            this.customLangConfig = new Config(plugin, "localization/lang_" + this.getPluginLanguage() + ".yml");
            requiredEntireReload = true;
        }

        //In case the LangName has been changed
        //In case the LangConfig has been changed
        if (requiredEntireReload){
            this.localizedMessages.values().forEach(localeMessageImp -> {
                localeMessageImp.setHasBeenSynced(false);
                localeMessageImp.resetDefaultFancyText();
            });
        }

        boolean anyChange = false;
        for (LocaleMessageImp localeMessage : localizedMessages.values()) {

            if (!localeMessage.shouldSyncToFile()){
                //Ignore this locale
                continue;
            }

            //Set a DefaultValue for this Custom LocaleMessage based on the ENGLISH hardcoded LocaleMessage, or the next one
            FancyText defaultFancyText = null;
            for (LocaleType locale : LocaleType.values()) {
                defaultFancyText = localeMessage.getFancyText(locale.name());
                if (defaultFancyText != null){
                    break;
                }
            }

            //Now we need to look for the LocaleMessage and save it to the hardcoded files, for example EN_US
            if (localeMessage.needToBeSynced()){
                for (Tuple<LocaleType, Config> tuple : hardcodedLocalizations) {
                    FancyText originalHardcodedLocale = tuple.getBeta().getFancyText(localeMessage.getKey());
                    if (originalHardcodedLocale == null){
                        originalHardcodedLocale = new FancyText("[LOCALE_NOT_FOUND]");
                    }
                    Config config = tuple.getBeta();
                    FancyText current = config.getFancyText(localeMessage.getKey());
                    if (!originalHardcodedLocale.equals(current)){
                        config.setValue(localeMessage.getKey(), originalHardcodedLocale);
                        tuple.getBeta().setValue("HasBeenChanged", true);
                    }
                }
                if (isHardcodedLocale){
                    //We can stop here, no need to check for the customLangConfig
                    localeMessage.setHasBeenSynced(true);
                }
            }

            if (!localeMessage.needToBeSynced()){
                continue;
            }
            localeMessage.setHasBeenSynced(true);

            //Now look for the customFile
            FancyText customFancyText = this.customLangConfig.getFancyText(localeMessage.getKey());
            if (customFancyText == null){
                //Update on the new file
                customFancyText = defaultFancyText;
                this.customLangConfig.setValue(localeMessage.getKey(), customFancyText);
                anyChange = true;
            }
            localeMessage.addLocale(getPluginLanguage(), customFancyText);
        }

        //Validate Hardcoded Localization Files
        for (Tuple<LocaleType, Config> tuple : hardcodedLocalizations) {
            if (!tuple.getBeta().getTheFile().exists() || tuple.getBeta().getBoolean("HasBeenChanged", false)){
                tuple.getBeta().setValue("HasBeenChanged", null);
                tuple.getBeta().saveAsync();
            }
        }

        if (anyChange){
            plugin.getLogger().info("Saving the NewConfig to FIle");
            this.customLangConfig.save();
        }
    }

    public HashMap<String, LocaleMessageImp> getLocalizedMessages() {
        return localizedMessages;
    }

    public boolean isMarkedForLocaleReload() {
        return markedForLocaleReload;
    }

    public void markForLocaleReload(){
        markedForLocaleReload = true;
    }

    public String getPluginLanguage() {
        return pluginLanguage;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public boolean hasUpdate(){
        return updateLink != null;
    }

    public void setUpdateLink(String updateLink) {
        this.updateLink = updateLink;
    }

    public String getUpdateLink() {
        return updateLink;
    }

    @Nullable
    public Config getCustomLangConfig(){
        return this.customLangConfig;
    }

}
