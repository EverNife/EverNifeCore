package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.ClickActionType;
import br.com.finalcraft.evernifecore.fancytext.FancySegment;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.fancytext.RenderContext;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

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
    public SendCustom addPlaceholder(String key, Object value) {
        return custom().addPlaceholder(key, value);
    }

    @Override
    public SendCustom addPlaceholder(String key, Supplier<?> value) {
        return custom().addPlaceholder(key, value);
    }

    @Override
    public SendCustom addPlaceholder(String key, Function<PlayerData, ?> value) {
        return custom().addPlaceholder(key, value);
    }

    @Override
    public SendCustom addPlaceholders(Map<String, ?> values) {
        return custom().addPlaceholders(values);
    }

    @Override
    public SendCustom addParser(String key, String description, Function<RenderContext, ?> parser) {
        return custom().addParser(key, description, parser);
    }

    @Override
    public SendCustom setHover(String hover) {
        return custom().setHover(hover);
    }

    @Override
    public SendCustom setClick(String clickActionText, ClickActionType actionType) {
        return custom().setClick(clickActionText, actionType);
    }

    @Override
    public SendCustom append(LocaleMessage localeMessage) {
        return custom().append(localeMessage);
    }

    @Override
    public SendCustom append(SendCustom sendCustom) {
        return custom().append(sendCustom);
    }

    @Override
    public SendCustom append(FancyText fancyText) {
        return custom().append(fancyText);
    }

    @Override
    public SendCustom append(String text) {
        return custom().append(text);
    }

    @Override
    public FancyText getFancyText(String lang){
        if (lang == null) return null; //getLangOf may yield null when no default lang is resolved
        return fancyTextMap.get(lang.toUpperCase(Locale.ROOT));
    }

    @Override
    public FancyText getFancyText(FCommandSender sender){
        //A message this sender's language has no translation for still has to render something, so the
        //plugin default answers - which is also what the resolve itself falls back to.
        FancyText perSender = getFancyText(FCLocaleManager.getLangOf(sender, this.plugin));
        return perSender != null ? perSender : getDefaultFancyText();
    }

    @Override
    public boolean isDefined() {
        return !fancyTextMap.isEmpty();
    }

    @Override
    public FancyText getDefaultFancyText() {
        if (defaultFancyText == null){
            defaultFancyText = getFancyText(FCLocaleManager.getLangOf(this.plugin));
            if (defaultFancyText == null){ //There is no set message for this lang, take first available
                if (fancyTextMap.isEmpty()){
                    warnHasNoLocaleText();
                    //Visible on purpose: a message that silently renders as nothing is a bug that
                    //reaches production; one that renders its own key is reported by the first
                    //player who sees it. Not cached either - registering a locale later fixes it.
                    return new FancySegment("[LOCALE_NOT_DEFINED:" + key + "]");
                }
                defaultFancyText = new ArrayList<>(fancyTextMap.values()).get(0);
            }
        }
        return defaultFancyText;
    }

    private void warnHasNoLocaleText() {
        EverNifeCore.getLog().warning("LocaleMessage '{}' of plugin '{}' has no registered locale text.",
                key, plugin.getMetaInfo().getName());
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
