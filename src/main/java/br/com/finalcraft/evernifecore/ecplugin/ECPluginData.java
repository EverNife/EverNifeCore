package br.com.finalcraft.evernifecore.ecplugin;

import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.ecplugin.annotations.ECPlugin;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.logger.debug.IDebugModule;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

public class ECPluginData {

    private final Plugin plugin;
    private final Runnable onReload;
    private final String[] reloadAfter;
    private String updateLink = null;
    private String pluginLanguage;
    private HashMap<String,LocaleMessageImp> localizedMessages = new HashMap();

    private Config localization_config;
    private Config customLangConfig;
    private final Map<String, Config> hardcodedLocalizations = new LinkedHashMap();
    private boolean markedForLocaleReload = false;

    //debug
    private transient IDebugModule[] debugModules = new IDebugModule[0];
    private Boolean debugEnabled = null;

    public ECPluginData(Plugin plugin) {
        this.plugin = plugin;

        // -------------------------------------------- //
        //  Handle @ECPlugin
        // -------------------------------------------- //
        final ECPlugin ecPlugin = FCReflectionUtil.getAnnotationDeeply(plugin.getClass(), ECPlugin.class);

        if (ecPlugin != null && ecPlugin.debugModuleEnum() != IDebugModule.class){
            if (!ecPlugin.debugModuleEnum().isEnum()){
                plugin.getLogger().warning("Failed to read debugModuleEnum from @ECPlugin, " + ecPlugin.debugModuleEnum().getName() + "  is not an enum!");
            }else {
                defineDebugModules(ecPlugin.debugModuleEnum().getEnumConstants());
            }
        }

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

        reloadAllCustomLocales();
        //this.plugin.getLogger().info("[FCLocale] Setting locale to [" + pluginLanguage +"]!");
    }

    public void defineDebugModules(IDebugModule[] debugModules) {
        this.debugModules = debugModules;
    }

    public boolean isDebugEnabled(){
        return isDebugEnabled(null);
    }

    public boolean isDebugEnabled(@Nullable IDebugModule debugModule){
        if (debugEnabled == null){
            Config config = new Config(plugin, "config.yml");
            debugEnabled = config.getOrSetDefaultValue(
                    "DebugMode.enabled",
                    false,
                    "If '" + plugin.getName() + "' should log debug messages on the console!"
            );

            for (IDebugModule module : debugModules) {
                boolean enabled = module.onConfigLoad(config.getConfigSection("DebugMode"));
                module.setEnabled(enabled);
            }

            config.setComment("DebugMode","-----------------------\n     Debug System\n-----------------------");
            if (config.contains("DebugMode.DebugModules")){
                config.setComment("DebugMode.DebugModules","List of DebugModules that are enabled!\nThese debug modules bellow will only work when 'DebugMode.enabled' is 'true'");
            }
            config.saveIfNewDefaults();
        }
        return debugEnabled && (debugModule == null || debugModule.isEnabled());
    }

    public void setDebugEnabled(Boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    public void addHardcodedLocaleIfNeeded(String lang){
        if (!hardcodedLocalizations.containsKey(lang)){
            hardcodedLocalizations.put(lang, new Config(plugin, "localization/lang_" + lang + ".yml"));
            markedForLocaleReload = true;
        }
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
            requiredEntireReload = true;
        }

        //If the plugin is using a HardcodedLocale there is no need to make great changes
        boolean isHardcodedLocale = hardcodedLocalizations.containsKey(this.getPluginLanguage());

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

            //Set a DefaultValue for this Custom LocaleMessage based on the ENGLISH hardcoded LocaleMessage, or the next one
            FancyText defaultFancyText = null;
            Set<String> ALL_POSSIBLE_LOCALES = new LinkedHashSet<>();

            ALL_POSSIBLE_LOCALES.addAll(Arrays.stream(LocaleType.values()).collect(Collectors.toList())); //Add 'EN_US' and 'PT_BR' First
            ALL_POSSIBLE_LOCALES.addAll(hardcodedLocalizations.keySet()); //Add other hardcoded locales after, like "PT_BR_CUSTOM"

            for (String possibleLocale : ALL_POSSIBLE_LOCALES) {
                defaultFancyText = localeMessage.getFancyText(possibleLocale);
                if (defaultFancyText != null){
                    break;
                }
            }

            //Now we need to look for the LocaleMessage and save it to the hardcoded files, for example EN_US
            if (localeMessage.needToBeSynced()){
                for (Map.Entry<String, Config> entry : hardcodedLocalizations.entrySet()) {
                    Config hardcodedConfig = entry.getValue();
                    FancyText hardcodedOnConfig = hardcodedConfig.getLoadable(localeMessage.getKey(), FancyText.class);
                    if (hardcodedOnConfig == null){
                        hardcodedOnConfig = new FancyText("[LOCALE_NOT_FOUND]");
                    }

                    FancyText hardcodedOnCode = localeMessage.getFancyText(entry.getKey());
                    if (hardcodedOnCode == null) {
                        hardcodedOnCode = defaultFancyText;
                    }

                    if (!hardcodedOnConfig.equals(hardcodedOnCode)){

                        if (!localeMessage.shouldSyncToFile()){
                            localeMessage.addLocale(entry.getKey(), hardcodedOnCode);
                            //Ignore this locale is this might be, for example a custom locale created by demand at the CMDInterpreter
                            //This should not be saved to the file
                            continue;
                        }

                        hardcodedConfig.setValue(localeMessage.getKey(), hardcodedOnCode);
                        entry.getValue().setValue("HasBeenChanged", true);
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
        for (Map.Entry<String, Config> entry : hardcodedLocalizations.entrySet()) {
            if (!entry.getValue().getTheFile().exists() || entry.getValue().getBoolean("HasBeenChanged", false)){
                entry.getValue().setValue("HasBeenChanged", null);
                entry.getValue().saveAsync();
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
