package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.fancytext.MessageContext;
import br.com.finalcraft.evernifecore.fancytext.MessageScope;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public class LocaleMessageImp implements LocaleMessage {

    private final CompoundReplacer compoundReplacer = new CompoundReplacer();
    private final ECPluginData plugin;
    private final String key;
    private final HashMap<String, FancyText> fancyTextMap = new HashMap<>();
    private final boolean shouldSyncToFile;
    private boolean hasBeenSynced = false;
    private String origin = null;

    private transient FancyText defaultFancyText; //Cached FancyText of the DefaultLocale of the plugin

    public LocaleMessageImp(ECPluginData plugin, String key) {
        this.plugin = plugin;
        this.key = key;
        this.shouldSyncToFile = false;
    }

    public LocaleMessageImp(ECPluginData plugin, String key, boolean shouldSyncToFile) {
        this.plugin = plugin;
        this.key = key;
        this.shouldSyncToFile = shouldSyncToFile;
    }

    @Override
    public void send(FCommandSender... commandSenders){
        custom().send(commandSenders);//Use a custom to replace CONTEXT placeholders!
    }

    @Override
    public void broadcast(){
        custom().broadcast();//Use a custom to replace CONTEXT placeholders!
    }


    @Override
    public SendCustom custom(){
        return new SendCustom(this);
    }

    @Override
    public SendCustom addReplacer(CompoundReplacer compoundReplacer) {
        return custom().addReplacer(compoundReplacer);
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
        if (lang == null) return null; //getLangOf may yield null when no default lang is resolved
        return fancyTextMap.get(lang.toUpperCase(Locale.ROOT));
    }

    @Override
    public FancyText getFancyText(FCommandSender sender){
        //Per-player language is opt-in and read straight from the player's LocalePDSection, which is
        //hot-loaded on login - so this cache-only lookup never blocks nor touches storage. A player
        //without the section (or without a chosen lang), or any non-player sender, falls back to the
        //plugin's default, keeping the flag-off behaviour byte-identical to before.
        if (ECSettings.PER_PLAYER_LOCALE && sender instanceof FPlayer){
            PlayerData playerData = PlayerController.getLoaded(sender.getUniqueId());
            if (playerData != null){
                LocalePDSection localeSection = playerData.getPDSectionIfLoaded(LocalePDSection.class);
                if (localeSection != null){
                    FancyText perPlayer = getFancyText(localeSection.getLang());
                    if (perPlayer != null){
                        return perPlayer;
                    }
                }
            }
        }
        return getDefaultFancyText();
    }

    @Override
    public FancyText getDefaultFancyText() {
        if (defaultFancyText == null){
            defaultFancyText = getFancyText(FCLocaleManager.getLangOf(this.plugin));
            if (defaultFancyText == null){ //There is no set message for this lang, take first available
                if (fancyTextMap.isEmpty()){
                    EverNifeCore.getLog().warning("LocaleMessage '" + key + "' of plugin '"
                            + plugin.getMetaInfo().getName() + "' has no registered locale text.");
                    return null;
                }
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
        fancyTextMap.put(lang.toUpperCase(Locale.ROOT), fancyText);
    }

    public HashMap<String, FancyText> getFancyTextMap() {
        return fancyTextMap;
    }

    public String getKey() {
        return key;
    }

    /**
     * The annotated field this message was built from, as {@code fully.qualified.Class#fieldName}, or
     * {@code null} for a message that has no single declaring field (a command's own locales, a derived
     * copy). The key itself is built from the SIMPLE class name, so two classes with the same simple
     * name in different packages produce the same key - this is what lets the scanner name both
     * culprits instead of silently handing the second one the first one's message.
     */
    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public ECPluginData getPlugin() {
        return plugin;
    }

    /**
     * The placeholders that describe the command being executed right now ({@code ${label}},
     * {@code ${subcmd}}). Computed per call from the scope of the calling thread: a LocaleMessage
     * lives in a static field, so anything stored on the instance would be shared by every
     * concurrent execution of that command.
     */
    public Map<String, Object> getContextPlaceholders() {
        MessageContext context = MessageScope.currentOrEmpty();
        Map<String, Object> placeholders = new HashMap<>();
        if (context.getLabel() != null) {
            placeholders.put("${label}", context.getLabel());
        }
        if (context.getSubCommandName() != null) {
            placeholders.put("${subcmd}", context.getSubCommandName());
        }
        return placeholders;
    }

    /**
     * Returns a new, unregistered copy of this message with every locale's FancyText cloned and
     * {@code placeholder} baked in via {@link FancyText#replace(String, String)}. The copy is never
     * added to the owning plugin's locale cache (unlike {@link br.com.finalcraft.evernifecore.locale.scanner.FCLocaleScanner#scanForLocale})
     * and is never synced to a lang file, so it carries no key collision risk - this is what lets a
     * dynamic, per-instance command (e.g. a command alias) derive its OWN copy of a class-level
     * {@code @FCLocale} template without sharing state (or a hover) with any other instance of the
     * same class.
     */
    public LocaleMessageImp derivePlaceholderResolved(String placeholder, String value) {
        LocaleMessageImp derived = new LocaleMessageImp(this.plugin, this.key, false);
        for (Map.Entry<String, FancyText> entry : this.fancyTextMap.entrySet()) {
            derived.addLocale(entry.getKey(), entry.getValue().copy().replace(placeholder, value));
        }
        return derived;
    }

}
