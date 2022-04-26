package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Function;

public class LocaleMessageImp implements LocaleMessage {

    private final Plugin plugin;
    private final String key;
    private final HashMap<String, FancyText> fancyTextMap = new HashMap<>();
    private final boolean shouldSyncToFile;
    private boolean hasBeenSynced = false;

    private transient FancyText defaultFancyText; //Cached FancyText of the DefaultLocale of the plugin

    //For COMMAND LOCALE MESSAGES these placeholders store context like objects, like %label% and other useful placeholders
    private final transient HashMap<String, Object> contextPlaceholders = new HashMap<>();
    //
    public LocaleMessageImp(Plugin plugin, String key) {
        this.plugin = plugin;
        this.key = key;
        this.shouldSyncToFile = false;
    }

    public LocaleMessageImp(Plugin plugin, String key, boolean shouldSyncToFile) {
        this.plugin = plugin;
        this.key = key;
        this.shouldSyncToFile = shouldSyncToFile;
    }

    @Override
    public void send(CommandSender... commandSenders){
        custom().send(commandSenders);//Use a custom to replace CONTEXT placeholders!
    }

    @Override
    public SendCustom custom(){
        return new SendCustom(this);
    }

    @Override
    public SendCustom addPlaceholder(String placeHolder, Object value) {
        return custom().addPlaceholder(placeHolder, value);
    }

    @Override
    public SendCustom addPlaceholder(String placeHolder, Function<PlayerData, Object> function) {
        return custom().addPlaceholder(placeHolder, function);
    }

    @Override
    public SendCustom addHover(String hover) {
        return custom().addHover(hover);
    }

    @Override
    public SendCustom addAction(String action) {
        return custom().addAction(action);
    }

    @Override
    public SendCustom addSuggest(String suggest) {
        return custom().addSuggest(suggest);
    }

    @Override
    public SendCustom addLink(String link) {
        return custom().addLink(link);
    }

    @Override
    public SendCustom concat(LocaleMessage localeMessage) {
        return custom().concat(localeMessage);
    }

    @Override
    public SendCustom concat(SendCustom sendCustom) {
        return custom().concat(sendCustom);
    }

    @Override
    public FancyText getFancyText(String lang){
        return fancyTextMap.get(lang.toUpperCase());
    }

    @Override
    public FancyText getFancyText(CommandSender sender){
        return getDefaultFancyText();
        //TODO Create a PER_PLAYER locale
        //return fancyTextMap.get(FCLocaleManager.getLangOf(sender));
    }

    @Override
    public FancyText getDefaultFancyText() {
        if (defaultFancyText == null){
            defaultFancyText = getFancyText(FCLocaleManager.getLangOf(this.plugin));
            if (defaultFancyText == null){ //There is no set message for this lang, take first available
                defaultFancyText = new ArrayList<>(fancyTextMap.values()).get(0);
            }
        }
        return defaultFancyText;
    }

    public boolean needToBeSynced() {
        return shouldSyncToFile && !hasBeenSynced;
    }

    public boolean shouldSyncToFile() {
        return shouldSyncToFile;
    }

    public void setHasBeenSynced(boolean hasBeenSynced) {
        this.hasBeenSynced = hasBeenSynced;
    }

    public boolean hasBeenSaved() {
        return hasBeenSynced;
    }

    public void resetDefaultFancyText(){
        defaultFancyText = null;
    }

    public void addLocale(String lang, FancyText fancyText){
        fancyTextMap.put(lang.toUpperCase(), fancyText);
    }

    public HashMap<String, FancyText> getFancyTextMap() {
        return fancyTextMap;
    }

    public String getKey() {
        return key;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public HashMap<String, Object> getContextPlaceholders() {
        return contextPlaceholders;
    }

}
