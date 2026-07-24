package br.com.finalcraft.evernifecore.ecplugin;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.evernifecore.ecplugin.annotations.ECPlugin;
import br.com.finalcraft.evernifecore.fancytext.FancySegment;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.logger.ECLogger;
import br.com.finalcraft.evernifecore.logger.debug.IDebugModule;
import jakarta.annotation.Nullable;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

public class ECPluginData {

    private final Object plugin;
    private final IPluginMetaInfo iPluginMetaInfo;
    private final ECLogger ecLogger;

    private final Runnable onReload;
    private final String[] reloadAfter;
    private String updateLink = null;
    private String pluginLanguage;
    private HashMap<String, LocaleMessageImp> localizedMessages = new HashMap();

    private Config localization_config;
    private Config customLangConfig;
    private final Map<String, Config> hardcodedLocalizations = new LinkedHashMap();
    private boolean markedForLocaleReload = false;

    //debug
    private transient IDebugModule[] debugModules = new IDebugModule[0];
    private Boolean debugEnabled = null;

    //commands registered through FinalCMDManager, owned by this plugin
    private final List<FinalCMDPluginCommand> registeredCommands = new ArrayList<>();

    public ECPluginData(Object plugin) {
        EverNifeCore.getProviders().getECPluginExtractor().validateJavaPlugin(plugin);

        this.plugin = plugin;
        this.iPluginMetaInfo = EverNifeCore.getProviders().getECPluginExtractor().getPluginMetaInfo(plugin);
        this.ecLogger = new ECLogger(this);

        // -------------------------------------------- //
        //  Handle @ECPlugin.Reload
        // -------------------------------------------- //
        final Method reloadMethod = findReloadMethod(plugin.getClass());

        if (reloadMethod != null){
            final boolean isStatic = Modifier.isStatic(reloadMethod.getModifiers());
            this.onReload = () -> {
                try {
                    reloadMethod.invoke(isStatic ? null : plugin);
                }catch (InvocationTargetException | IllegalAccessException e){
                    getLog().warning("Failed to execute OnReload method of (" + getMetaInfo().getName() + ")");
                    e.printStackTrace();
                }
            };
            this.reloadAfter = reloadMethod.getAnnotation(ECPlugin.Reload.class).reloadAfter();
        }else if (plugin instanceof IECPluginBootstrap){
            //No annotated method: a bootstrap plugin is reloadable through its onECPluginReload hook
            this.onReload = ((IECPluginBootstrap) plugin)::onECPluginReload;
            this.reloadAfter = new String[0];
        }else {
            this.onReload = null;
            this.reloadAfter = new String[0];
        }

        reloadAllCustomLocales();
    }

    public void defineDebugModules(IDebugModule[] debugModules) {
        this.debugModules = debugModules;
    }

    public boolean isDebugEnabled(){
        return isDebugEnabled(null);
    }

    public boolean isDebugEnabled(@Nullable IDebugModule debugModule){
        if (debugEnabled == null){
            Config config = ConfigFactory.open(this, "config.yml");
            debugEnabled = config.getOrSetValueIfAbsent(
                    "DebugMode.enabled",
                    false,
                    "If '" + getMetaInfo().getName() + "' should log debug messages on the console!"
            );

            for (IDebugModule module : debugModules) {
                boolean enabled = module.onConfigLoad(config.getConfigSection("DebugMode"));
                module.setEnabled(enabled);
            }

            config.setComment("DebugMode","-----------------------\n     Debug System\n-----------------------");
            if (config.contains("DebugMode.DebugModules")){
                config.setComment("DebugMode.DebugModules","List of DebugModules that are enabled!\nThese debug modules bellow will only work when 'DebugMode.enabled' is 'true'");
            }
            if (config.hasNewSeededDefaults()){
                config.save();
                config.clearNewSeededDefaults();
            }
        }
        return debugEnabled && (debugModule == null || debugModule.isEnabled());
    }

    public void setDebugEnabled(Boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    public void addHardcodedLocaleIfNeeded(String lang){
        if (!hardcodedLocalizations.containsKey(lang)){
            String fileName = "localization/lang_" + lang + ".yml";
            hardcodedLocalizations.put(lang, ConfigFactory.open(this, fileName));
            // A language that only exists because some plugin declared it still has to show up
            // wherever the known languages are listed.
            LocaleType.register(lang);
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
            this.localization_config = ConfigFactory.open(this, "localization/localization_config.yml");
            this.pluginLanguage = localization_config.getOrSetValueIfAbsent("Localization.fileName", "lang_" + FCLocaleManager.DEFAULT_EVERNIFECORE_LOCALE + ".yml")
                    .replace(".yml","")
                    .replace("lang_","");
            if (localization_config.hasNewSeededDefaults()){
                localization_config.save();
                localization_config.clearNewSeededDefaults();
            }
            requiredEntireReload = true;
        }

        //If the plugin is using a HardcodedLocale there is no need to make great changes
        boolean isHardcodedLocale = hardcodedLocalizations.containsKey(this.getPluginLanguage());

        if (!isHardcodedLocale &&
                (this.customLangConfig == null //There was no config, first load of the plugin
                        || this.customLangConfig.hasBeenModified() //The config has been modified
                        || !this.customLangConfig.getFile().getName().equals("lang_" + this.getPluginLanguage() + ".yml") //The language name has been changed
                )){
            this.customLangConfig = ConfigFactory.open(this, "localization/lang_" + this.getPluginLanguage() + ".yml");
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

            ALL_POSSIBLE_LOCALES.addAll(LocaleType.values().stream().collect(Collectors.toList())); //Add 'EN_US' and 'PT_BR' First
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
                    FancyText hardcodedOnConfig = hardcodedConfig.getValue(localeMessage.getKey(), FancyText.class);
                    if (hardcodedOnConfig == null){
                        hardcodedOnConfig = new FancySegment("[LOCALE_NOT_FOUND]");
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
            FancyText customFancyText = this.customLangConfig.getValue(localeMessage.getKey(), FancyText.class);
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
            File hardcodedFile = entry.getValue().getFile();
            if ((hardcodedFile != null && !hardcodedFile.exists()) || entry.getValue().getBoolean("HasBeenChanged", false)){
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

    public Object getPlugin() {
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

    public IPluginMetaInfo getMetaInfo(){
        return iPluginMetaInfo;
    }

    public ECLogger<?> getLog(){
        return ecLogger;
    }

    /**
     * Live view of the commands this plugin registered through
     * {@link br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager}. Returns an immutable
     * copy - mutating the returned list never affects this plugin's tracked state.
     */
    public List<FinalCMDPluginCommand> getRegisteredCommands(){
        return Collections.unmodifiableList(new ArrayList<>(registeredCommands));
    }

    /** Finds a registered command by any of its labels - primary or alias - case-insensitive. */
    public Optional<FinalCMDPluginCommand> findRegisteredCommand(String label){
        for (FinalCMDPluginCommand command : registeredCommands) {
            if (command.getPrimaryLabel().equalsIgnoreCase(label)){
                return Optional.of(command);
            }
            for (String alias : command.getExtraLabels()) {
                if (alias.equalsIgnoreCase(label)){
                    return Optional.of(command);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Framework-internal bookkeeping: tracks {@code command} as registered by this plugin, replacing
     * any previous entry with the same primary label so a reload never leaves a stale/duplicate entry
     * behind. Called by {@link FinalCMDPluginCommand#registerCommand()} after a successful platform
     * registration - plugin authors never call this directly.
     */
    public void trackRegisteredCommand(FinalCMDPluginCommand command){
        registeredCommands.removeIf(existing -> existing.getPrimaryLabel().equalsIgnoreCase(command.getPrimaryLabel()));
        registeredCommands.add(command);
    }

    /**
     * Framework-internal bookkeeping: stops tracking {@code command} as registered by this plugin
     * (no-op if it isn't tracked). Called by {@link FinalCMDPluginCommand#unregister()} - plugin
     * authors never call this directly.
     */
    public void untrackRegisteredCommand(FinalCMDPluginCommand command){
        registeredCommands.remove(command);
    }

    /**
     * Finds the {@code @ECPlugin.Reload} method: the plugin's own declared methods first (any
     * visibility), then the public view - which also surfaces annotated methods inherited from
     * superclasses and interface defaults.
     */
    private static Method findReloadMethod(Class<?> pluginClass) {
        for (Method method : pluginClass.getDeclaredMethods()) {
            if (method.getAnnotation(ECPlugin.Reload.class) != null) {
                return method;
            }
        }
        for (Method method : pluginClass.getMethods()) {
            if (method.getAnnotation(ECPlugin.Reload.class) != null) {
                return method;
            }
        }
        return null;
    }

}
