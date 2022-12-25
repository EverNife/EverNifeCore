package br.com.finalcraft.evernifecore.ecplugin;

import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.ecplugin.annotations.ECPlugin;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.logger.ECLogger;
import br.com.finalcraft.evernifecore.util.commons.Tuple;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class ECPluginData {

    private final Plugin plugin;
    private final Runnable onReload;
    private final String[] reloadAfter;
    private String updateLink = null;
    private String pluginLanguage;
    private HashMap<String,LocaleMessageImp> localizedMessages = new HashMap();

    private Config localization_config;
    private Config customLangConfig;
    private final List<Tuple<LocaleType, Config>> hardcodedLocalizations = new ArrayList<>();
    private boolean markedForLocaleReload = false;

    //debug
    private Boolean debugEnabled = null;

    public ECPluginData(Plugin plugin) {
        this.plugin = plugin;

        // -------------------------------------------- //
        //  Handle @ECPlugin.Reload
        // -------------------------------------------- //
        final Method reloadMethod = Arrays.stream(plugin.getClass().getDeclaredMethods())
                .filter(method -> method.getAnnotation(ECPlugin.Reload.class) != null)
                .findFirst()
                .orElse(null);
        if (reloadMethod != null){
            final boolean isStatic = Modifier.isStatic(reloadMethod.getModifiers());
            this.onReload = () -> {
                try {
                    reloadMethod.invoke(isStatic ? null : plugin);
                }catch (InvocationTargetException | IllegalAccessException e){
                    plugin.getLogger().warning("Failed to execute OnReload method of (" + plugin.getName() + ")");
                    e.printStackTrace();
                }
            };
            this.reloadAfter = reloadMethod.getAnnotation(ECPlugin.Reload.class).reloadAfter();
        }else {
            this.onReload = null;
            this.reloadAfter = new String[0];
        }

        // -------------------------------------------- //
        //  Handle @ECPlugin.Logger
        // -------------------------------------------- //
        final Field loggerField = Arrays.stream(plugin.getClass().getDeclaredFields())
                .filter(field -> field.getAnnotation(ECPlugin.Logger.class) != null)
                .findFirst()
                .orElse(null);
        if (loggerField != null){
            if (loggerField.getDeclaringClass() != ECLogger.class){
                plugin.getLogger().severe("The field (" + loggerField.getName() + ") of (" + plugin.getName() + ") is not a ECLogger!");
            }else {
                try {
                    loggerField.set(plugin, new ECLogger(this));
                } catch (IllegalAccessException e) {
                    plugin.getLogger().warning("Failed to instantiate ECLogger on (" + plugin.getName() + ")");
                    e.printStackTrace();
                }
            }
        }

        for (LocaleType type : LocaleType.values()) {
            Config lang = new Config(plugin, "localization/lang_" + type.name() + ".yml");
            hardcodedLocalizations.add(Tuple.of(type, lang));
        }

        reloadAllCustomLocales();

        this.plugin.getLogger().info("[FCLocale] Setting locale to [" + pluginLanguage +"]!");
    }

    public boolean isDebugEnabled(){
        if (debugEnabled == null){
            Config config = new Config(plugin, "config.yml");
            debugEnabled = config.getOrSetDefaultValue(
                    "DebugMode.enabled",
                    false,
                    "If '" + plugin.getName() + "' should log debug messages on the console!"
            );
            config.setComment("DebugMode","-----------------------\n     Debug System\n-----------------------");
            config.saveIfNewDefaults();
        }
        return debugEnabled;
    }

    public void setDebugEnabled(Boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
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
                    Config hardcodedConfig = tuple.getBeta();
                    FancyText hardcodedOnConfig = hardcodedConfig.getLoadable(localeMessage.getKey(), FancyText.class);
                    if (hardcodedOnConfig == null){
                        hardcodedOnConfig = new FancyText("[LOCALE_NOT_FOUND]");
                    }

                    FancyText hardcodedOnCode = localeMessage.getFancyText(tuple.getAlfa().name());
                    if (hardcodedOnCode == null) hardcodedOnCode = defaultFancyText;

                    if (!hardcodedOnConfig.equals(hardcodedOnCode)){
                        hardcodedConfig.setValue(localeMessage.getKey(), hardcodedOnCode);
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
            FancyText customFancyText = this.customLangConfig.getLoadable(localeMessage.getKey(), FancyText.class);
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
            this.customLangConfig.save();
        }
    }

    public String[] getReloadAfter() {
        return reloadAfter;
    }

    public boolean canReload(){
        return onReload != null;
    }

    public void reloadPlugin(){
        if (canReload()){
            onReload.run();
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
