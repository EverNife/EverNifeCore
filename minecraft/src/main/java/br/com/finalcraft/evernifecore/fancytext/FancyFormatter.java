package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class FancyFormatter extends FancyText {

    protected Map<String, Object> mapOfPlaceholders = new HashMap<>();
    protected boolean complexPlaceholder = false; //Placeholders that depends on the PlayerData
    protected List<FancyText> fancyTextList = new ArrayList<>();

    public FancyFormatter addPlaceholder(String placeHolder, Object value){
        mapOfPlaceholders.put(placeHolder, value);
        return this;
    }

    public FancyFormatter addPlaceholder(String placeHolder, Function<PlayerData, Object> function){
        mapOfPlaceholders.put(placeHolder, function);
        complexPlaceholder = true;
        return this;
    }

    public FancyFormatter append(FancyText... fancyTexts) {
        for (FancyText fancyText : fancyTexts) {
            fancyTextList.add(fancyText);
            fancyText.fancyFormatter = this;
        }
        return this;
    }

    @Override
    public FancyFormatter append(FancyText fancyText){
        if (fancyText instanceof FancyFormatter other){
            for (FancyText fancyTextInner : other.fancyTextList) {
                append(fancyTextInner.clone());
            }
        }else {
            this.fancyTextList.add(fancyText);
            fancyText.fancyFormatter = this;
        }
        return this;
    }

    @Override
    public FancyFormatter append(String text){
        FancyText fancyText = new FancyText(text);
        fancyText.fancyFormatter = this;
        this.fancyTextList.add(fancyText);
        return this;
    }

    @Override
    public FancyFormatter append(String text, String hoverText) {
        FancyText fancyText = new FancyText(text, hoverText);
        fancyText.fancyFormatter = this;
        this.fancyTextList.add(fancyText);
        return this;
    }

    @Override
    public FancyFormatter append(String text, String hoverText, String runCommand) {
        FancyText fancyText = new FancyText(text, hoverText, runCommand);
        fancyText.fancyFormatter = this;
        this.fancyTextList.add(fancyText);
        return this;
    }

    public boolean hasPlaceholders(){
        return mapOfPlaceholders.size() > 0;
    }

    public List<FancyText> getFancyTextList() {
        return fancyTextList;
    }

    @Override
    public FancyFormatter replace(String placeholder, String value){
        for (int i = 0; i < this.fancyTextList.size(); i++) {
            this.fancyTextList.set(i, this.fancyTextList.get(i).replace(placeholder, value));
        }
        return this;
    }

    @Override
    public FancyText replace(CompoundReplacer replacer) {
        for (int i = 0; i < this.fancyTextList.size(); i++) {
            this.fancyTextList.set(i, this.fancyTextList.get(i).replace(replacer));
        }
        return this;
    }

    @Override
    public FancyFormatter clone(){
        FancyFormatter clone = new FancyFormatter();
        for (FancyText fancyText : this.fancyTextList) {
            clone.append(fancyText.clone());
        }
        clone.mapOfPlaceholders = this.mapOfPlaceholders;
        clone.complexPlaceholder = this.complexPlaceholder;
        return clone;
    }

    //Mirrored functions

    @Override
    public String getText() {
        return this.fancyTextList.get(fancyTextList.size() - 1).getText();
    }

    @Override
    public String getHoverText() {
        return this.fancyTextList.get(fancyTextList.size() - 1).getHoverText();
    }

    @Override
    public String getClickActionText() {
        return this.fancyTextList.get(fancyTextList.size() - 1).getClickActionText();
    }

    @Override
    public ClickActionType getClickActionType() {
        return this.fancyTextList.get(fancyTextList.size() - 1).getClickActionType();
    }

    @Override
    public FancyText setHoverText(ItemStack hoverItem) {
        this.fancyTextList.get(fancyTextList.size() - 1).setHoverText(hoverItem);
        return this;
    }

    @Override
    public FancyFormatter setHoverText(String hoverText) {
        this.fancyTextList.get(fancyTextList.size() - 1).setHoverText(hoverText);
        return this;
    }

    @Override
    public FancyText setClickAction(ClickActionType actionType) {
        this.fancyTextList.get(fancyTextList.size() - 1).setClickAction(actionType);
        return this;
    }

    @Override
    public FancyText setClickAction(String actionText, ClickActionType actionType) {
        this.fancyTextList.get(fancyTextList.size() - 1).setClickAction(actionText, actionType);
        return this;
    }

    @Override
    public FancyFormatter setRunCommandAction(String runCommandAction) {
        this.fancyTextList.get(fancyTextList.size() - 1).setRunCommandAction(runCommandAction);
        return this;
    }

    @Override
    public FancyFormatter setSuggestCommandAction(String suggestCommandAction) {
        this.fancyTextList.get(fancyTextList.size() - 1).setSuggestCommandAction(suggestCommandAction);
        return this;
    }

    @Override
    public FancyFormatter setOpenLinkAction(String linkToOpen) {
        this.fancyTextList.get(fancyTextList.size() - 1).setOpenLinkAction(linkToOpen);
        return this;
    }

    @Override
    public void send(CommandSender... commandSenders){
        FancyTextManager.send(this, commandSenders);
    }

    // ------------------------------------------------------------------------------------------------------
    // Static Values
    // ------------------------------------------------------------------------------------------------------

    public static FancyFormatter of(){
        return new FancyText().getOrCreateFormmater();
    }

    public static FancyFormatter of(String text) {
        return new FancyText(text).getOrCreateFormmater();
    }

    public static FancyFormatter of(String text, String hoverText) {
        return new FancyText(text, hoverText).getOrCreateFormmater();
    }

    public static FancyFormatter of(String text, String hoverText, String runCommand) {
        return new FancyText(text, hoverText, runCommand).getOrCreateFormmater();
    }

    public static FancyFormatter of(String text, String hoverText, String clickActionText, ClickActionType clickActionType) {
        return new FancyText(text, hoverText, clickActionText, clickActionType).getOrCreateFormmater();
    }

    public static FancyFormatter of(FancyText... fancyTexts) {
        return new FancyFormatter().append(fancyTexts);
    }
}
