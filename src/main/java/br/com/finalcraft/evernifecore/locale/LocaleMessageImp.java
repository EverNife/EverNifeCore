package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.function.Function;

public class LocaleMessageImp implements LocaleMessage {

    private final Plugin plugin;
    private final String key;
    private final HashMap<String, FancyText> fancyTextMap = new HashMap<>();

    //For COMMAND LOCALE MESSAGES these placeholders store context like objects, like %label% and other useful placeholders
    private final transient HashMap<String, Object> contextPlaceholders = new HashMap<>();
    //


    private transient FancyText fancyText = null; //Cached FancyText of the DefaultLocale of the plugin

    public LocaleMessageImp(Plugin plugin, String key) {
        this.plugin = plugin;
        this.key = key;
    }

    protected void setCachedFancyTextTo(FancyText fancyText){
        this.fancyText = fancyText;
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
    public SendCustom concat(LocaleMessage localeMessage) {
        return custom().concat(localeMessage);
    }

    @Override
    public SendCustom concat(SendCustom sendCustom) {
        return custom().concat(sendCustom);
    }

    protected void addLocale(FancyText fancyText, String lang){
        fancyTextMap.put(lang.toUpperCase(), fancyText);
    }

    public FancyText getFancyText(LocaleType localeType){
        return fancyTextMap.get(localeType.name());
    }

    public FancyText getFancyText(String lang){
        return fancyTextMap.get(lang);
    }

    public FancyText getFancyText(CommandSender sender){
        return fancyText;
        //TODO Create a PER_PLAYER locale
        //return fancyTextMap.get(FCLocaleManager.getLangOf(sender));
    }

    public HashMap<String, FancyText> getFancyTextMap() {
        return fancyTextMap;
    }

    protected String getKey() {
        return key;
    }

    public HashMap<String, Object> getContextPlaceholders() {
        return contextPlaceholders;
    }

}
