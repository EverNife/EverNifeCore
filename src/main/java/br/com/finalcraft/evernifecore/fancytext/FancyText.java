package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class FancyText {

    protected String text = "";
    protected String hoverText = null;
    protected String clickActionText = null;
    protected ClickActionType clickActionType = ClickActionType.NONE;
    protected String lastColor = "";

    private boolean recentChanged = true;
    private String lastStartingColor = "";

    private final transient List<TextComponent> textComponents = new ArrayList<>(); //Cache of TextComponents
    protected transient @Nullable FancyFormatter fancyFormatter; //States if this FancyText is inside a FancyFormatter

    public FancyText() {
    }

    public FancyText(String text) {
        this.text = text;
    }

    public FancyText(String text, String hoverText) {
        this.text = text;
        this.hoverText = hoverText;
    }

    public FancyText(String text, String hoverText, String runCommand) {
        this.text = text;
        this.hoverText = hoverText;
        this.clickActionText = runCommand;
        this.clickActionType = ClickActionType.RUN_COMMAND;
    }

    public FancyText(String text, String hoverText, String clickActionText, ClickActionType clickActionType) {
        this.text = text;
        this.hoverText = hoverText;
        this.clickActionText = clickActionText;
        this.clickActionType = clickActionType;
    }

    public String getText() {
        return text;
    }

    public String getHoverText() {
        return hoverText;
    }

    public String getClickActionText() {
        return clickActionText;
    }

    public ClickActionType getClickActionType() {
        return clickActionType;
    }

    /**
     * Seta o texto dessa FancyMessage, por padrão, o texto é vazio "";
     */
    public FancyText setText(String text) {
        setRecentChanged();
        this.text = text;
        return this;
    }

    public FancyText replace(String placeholder, String value){
        setRecentChanged();
        this.text = text.replace(placeholder, value);
        if (this.hoverText != null) this.hoverText = this.hoverText.replace(placeholder, value);
        if (this.clickActionText != null) this.clickActionText = this.clickActionText.replace(placeholder, value);
        return this;
    }

    public FancyText replace(CompoundReplacer replacer){
        setRecentChanged();
        this.text = replacer.apply(this.text);
        if (this.hoverText != null) this.hoverText = replacer.apply(this.hoverText);
        if (this.clickActionText != null) this.clickActionText = replacer.apply(this.clickActionText);
        return this;
    }

    protected FancyFormatter getOrCreateFormmater(){
        if (this.fancyFormatter == null){
            this.fancyFormatter = new FancyFormatter();
            this.fancyFormatter.append(this);
        }
        return this.fancyFormatter;
    }

    public FancyFormatter append(String text){
        return getOrCreateFormmater().append(new FancyText(text));
    }

    public FancyFormatter append(String text, String hoverText) {
        return getOrCreateFormmater().append(new FancyText(text, hoverText));
    }

    public FancyFormatter append(String text, String hoverText, String runCommand) {
        return getOrCreateFormmater().append(new FancyText(text, hoverText, runCommand));
    }

    public FancyFormatter append(FancyText fancyText){
        return getOrCreateFormmater().append(fancyText);
    }

    /**
     * Seta o a HoverMessage dessa FancyMessage;
     */
    public FancyText setHoverText(String hoverText) {
        setRecentChanged();
        this.hoverText = hoverText;
        return this;
    }

    /**
     * Seta o a HoverMessage dessa FancyMessage;
     */
    public FancyText setHoverText(List<String> hoverText) {
        setRecentChanged();
        this.hoverText = String.join("\n", hoverText);
        return this;
    }

    /**
     * Seta o a RunCommand dessa FancyMessage;
     * (Comando executado quando o jogador clica na mensagem)
     */
    public FancyText setClickAction(String actionText, ClickActionType actionType) {
        setRecentChanged();
        this.clickActionText = actionText;
        this.clickActionType = actionType;
        return this;
    }

    /**
     * Seta o a RunCommand dessa FancyMessage;
     * (Comando executado quando o jogador clica na mensagem)
     */
    public FancyText setRunCommandAction(String runCommandAction) {
        setRecentChanged();
        this.clickActionText = runCommandAction;
        this.clickActionType = ClickActionType.RUN_COMMAND;
        return this;
    }

    /**
     * Seta o a SuggestCommand dessa FancyMessage;
     * (Comando sugerido quando o jogador clica na mensagem)
     */
    public FancyText setSuggestCommandAction(String suggestCommandAction) {
        setRecentChanged();
        this.clickActionText = suggestCommandAction;
        this.clickActionType = ClickActionType.SUGGEST_COMMAND;
        return this;
    }

    /**
     * Seta o a OpenLink dessa FancyMessage;
     * (Link aberto quando o jogador clica na mensagem)
     */
    public FancyText setOpenLinkAction(String linkToOpen) {
        setRecentChanged();
        this.clickActionText = linkToOpen;
        this.clickActionType = ClickActionType.OPEN_URL;
        return this;
    }

    public void setRecentChanged() {
        recentChanged = true;
    }

    /**
     *   Retorna a parte do comando fancytext dessa FancyMessage!
     *
     *   Vale salientar que esse método nao retorna o comando correto do fancytext, apenas uma parte dele!
     *
     *   Use as funções do FancyText.class ao invés dessa!
     */
    protected List<TextComponent> getTextComponents(String startingColor){

        if (!startingColor.equals(lastStartingColor)){
            setRecentChanged();
        }
        if (!textComponents.isEmpty() && !recentChanged){
            return this.textComponents;
        }

        recentChanged = false;
        textComponents.clear();

        String fixedText = startingColor + this.text; //Fix LastColors
        this.lastStartingColor = startingColor;
        this.lastColor = ChatColor.getLastColors(fixedText);

        TextComponent baseTextComponent = new TextComponent();
        if (this.hoverText != null && !this.hoverText.isEmpty()){

            List<String> hoverLines = splitAtNewLine(this.hoverText);

            for (int i = 1; i < hoverLines.size(); i++) { //From the second line forward
                hoverLines.set(i, ChatColor.getLastColors(hoverLines.get(i - 1)) + hoverLines.get(i));
            }

            final String fixedHoverText = String.join("\n", hoverLines);

            baseTextComponent.setHoverEvent(new HoverEvent( HoverEvent.Action.SHOW_TEXT, new ComponentBuilder( fixedHoverText ).create() ));
        }

        if (this.clickActionText != null){
            switch (this.clickActionType){
                case NONE:
                    break;
                case RUN_COMMAND:
                    baseTextComponent.setClickEvent( new ClickEvent( ClickEvent.Action.RUN_COMMAND,  this.clickActionText ) );
                    break;
                case OPEN_URL:
                    baseTextComponent.setClickEvent( new ClickEvent( ClickEvent.Action.OPEN_URL,  this.clickActionText ) );
                    break;
                case SUGGEST_COMMAND:
                    baseTextComponent.setClickEvent( new ClickEvent( ClickEvent.Action.SUGGEST_COMMAND,  this.clickActionText ) );
                    break;
            }
        }

        List<String> lines = splitAtNewLine(fixedText);

        TextComponent currentTextComponent;
        String _temp_startingColor = "";

        for (int i = 0; i < lines.size(); i++) {
            if (i > 0){
                this.textComponents.add(null);
            }
            String textComponentString = _temp_startingColor + lines.get(i);
            _temp_startingColor = ChatColor.getLastColors(textComponentString);
            currentTextComponent = new TextComponent(TextComponent.fromLegacyText( textComponentString ));
            copyTextEventsFrom(baseTextComponent,currentTextComponent);

            this.textComponents.add(currentTextComponent);
        }

        return (this.textComponents);
    }

    private void copyTextEventsFrom(TextComponent originFont, TextComponent targetToPaste){
        targetToPaste.setHoverEvent(originFont.getHoverEvent());
        targetToPaste.setClickEvent(originFont.getClickEvent());
    }

    /**
     *   Recebe um player como argumento
     *
     *   Monta o comando para esses FancyTextElement
     *   enums envia a mensagem para o jogador!
     */
    public void send(CommandSender... commandSender){
        FancyTextManager.send(this, commandSender);
    }

    public void broadcast(){
        send(Bukkit.getOnlinePlayers().toArray(new Player[0]));
    }

    public FancyText clone() {
        FancyText fancyText = new FancyText(text, hoverText, clickActionText, clickActionType);
        fancyText.lastColor = this.lastColor;
        return fancyText;
    }

    // ------------------------------------------------------------------------------------------------------
    // TellRaw Fixer
    // ------------------------------------------------------------------------------------------------------

    protected String getLastTextColor() {
        return lastColor;
    }

    private List<String> splitAtNewLine(String text){
        char[] charArray = text.toCharArray();
        List<String> lines = new ArrayList<>();
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < charArray.length; i++) {

            if (charArray[i] == '\n'){
                lines.add(stringBuilder.toString());
                stringBuilder = new StringBuilder();
                continue;
            }

            stringBuilder.append(charArray[i]);
        }
        lines.add(stringBuilder.toString());
        return lines;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FancyText fancyText = (FancyText) o;

        if (!Objects.equals(text, fancyText.text)) return false;
        if (!Objects.equals(hoverText, fancyText.hoverText)) return false;
        if (!Objects.equals(clickActionText, fancyText.clickActionText)) return false;

        return clickActionType == fancyText.clickActionType;
    }

    @Override
    public int hashCode() {
        int result = text != null ? text.hashCode() : 0;
        result = 31 * result + (hoverText != null ? hoverText.hashCode() : 0);
        result = 31 * result + (clickActionText != null ? clickActionText.hashCode() : 0);
        result = 31 * result + (clickActionType != null ? clickActionType.hashCode() : 0);
        return result;
    }

    // ------------------------------------------------------------------------------------------------------
    // Static Values
    // ------------------------------------------------------------------------------------------------------

    public static FancyText of(){
        return new FancyText();
    }

    public static FancyText of(String text) {
        return new FancyText(text);
    }

    public static FancyText of(String text, String hoverText) {
        return new FancyText(text, hoverText);
    }

    public static FancyText of(String text, String hoverText, String runCommand) {
        return new FancyText(text, hoverText, runCommand);
    }

    public static FancyText of(String text, String hoverText, String clickActionText, ClickActionType clickActionType) {
        return new FancyText(text, hoverText, clickActionText, clickActionType);
    }

}
