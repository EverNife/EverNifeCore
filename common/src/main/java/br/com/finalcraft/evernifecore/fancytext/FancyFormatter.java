package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.fancytext.hover.FancyHover;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** An ordered chain of {@link FancySegment} pieces, rendered as a single Adventure component. */
public class FancyFormatter implements FancyText {

    protected Map<String, Object> mapOfPlaceholders = new HashMap<>();
    protected boolean complexPlaceholder = false;
    protected List<FancyText> fancyTextList = new ArrayList<>();

    public FancyFormatter addPlaceholder(String placeHolder, Object value) {
        mapOfPlaceholders.put(placeHolder, value);
        return this;
    }

    public FancyFormatter addPlaceholder(String placeHolder, Function<PlayerData, Object> function) {
        mapOfPlaceholders.put(placeHolder, function);
        complexPlaceholder = true;
        return this;
    }

    public FancyFormatter append(FancyText... fancyTexts) {
        for (FancyText fancyText : fancyTexts) {
            fancyTextList.add(fancyText);
        }
        return this;
    }

    @Override
    public FancyFormatter append(FancyText fancyText) {
        if (fancyText instanceof FancyFormatter other) {
            for (FancyText fancyTextInner : other.fancyTextList) {
                append(fancyTextInner.clone());
            }
        } else {
            this.fancyTextList.add(fancyText);
        }
        return this;
    }

    @Override
    public FancyFormatter append(String text) {
        this.fancyTextList.add(new FancySegment(text));
        return this;
    }

    @Override
    public FancyFormatter append(String text, String hoverText) {
        this.fancyTextList.add(new FancySegment(text, hoverText));
        return this;
    }

    @Override
    public FancyFormatter append(String text, String hoverText, String runCommand) {
        this.fancyTextList.add(new FancySegment(text, hoverText, runCommand));
        return this;
    }

    public boolean hasPlaceholders() {
        return mapOfPlaceholders.size() > 0;
    }

    public List<FancyText> getFancyTextList() {
        return fancyTextList;
    }

    @Override
    public FancyFormatter replace(String placeholder, String value) {
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
    public Component toComponent() {
        return toComponent("");
    }

    @Override
    public Component toComponent(String startingColor) {
        TextComponent.Builder builder = Component.text();
        // Legacy chat lets a colour bleed into the following text; Adventure siblings do not inherit
        // one another's colour, so carry each segment's trailing colour into the next as its start.
        String previousColor = startingColor;
        for (FancyText fancyText : fancyTextList) {
            builder.append(fancyText.toComponent(previousColor));
            previousColor = fancyText.getLastTextColor();
        }
        return builder.build();
    }

    @Override
    public FancyFormatter clone() {
        FancyFormatter clone = new FancyFormatter();
        for (FancyText fancyText : this.fancyTextList) {
            clone.append(fancyText.clone());
        }
        clone.mapOfPlaceholders = new HashMap<>(this.mapOfPlaceholders);
        clone.complexPlaceholder = this.complexPlaceholder;
        return clone;
    }

    // Getters/setters target the last appended segment; an empty formatter has none, so degrade
    // gracefully (null / NONE / no-op) instead of throwing IndexOutOfBounds.
    private FancyText lastOrNull() {
        return fancyTextList.isEmpty() ? null : fancyTextList.get(fancyTextList.size() - 1);
    }

    @Override
    public String getText() {
        FancyText last = lastOrNull();
        return last == null ? null : last.getText();
    }

    @Override
    public String getHoverText() {
        FancyText last = lastOrNull();
        return last == null ? null : last.getHoverText();
    }

    @Override
    public FancyHover getHover() {
        FancyText last = lastOrNull();
        return last == null ? null : last.getHover();
    }

    @Override
    public String getClickActionText() {
        FancyText last = lastOrNull();
        return last == null ? null : last.getClickActionText();
    }

    @Override
    public ClickActionType getClickActionType() {
        FancyText last = lastOrNull();
        return last == null ? ClickActionType.NONE : last.getClickActionType();
    }

    @Override
    public String getLastTextColor() {
        FancyText last = lastOrNull();
        return last == null ? "" : last.getLastTextColor();
    }

    @Override
    public FancyFormatter setText(String text) {
        FancyText last = lastOrNull();
        if (last != null) last.setText(text);
        return this;
    }

    @Override
    public FancyFormatter hover(String hoverText) {
        FancyText last = lastOrNull();
        if (last != null) last.hover(hoverText);
        return this;
    }

    @Override
    public FancyFormatter hover(FancyHover hover) {
        FancyText last = lastOrNull();
        if (last != null) last.hover(hover);
        return this;
    }

    @Override
    public FancyFormatter click(ClickActionType actionType) {
        FancyText last = lastOrNull();
        if (last != null) last.click(actionType);
        return this;
    }

    @Override
    public FancyFormatter click(String actionText, ClickActionType actionType) {
        FancyText last = lastOrNull();
        if (last != null) last.click(actionText, actionType);
        return this;
    }

    @Override
    public void send(FCommandSender... commandSenders) {
        FancyTextManager.send(this, commandSenders);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FancyFormatter other = (FancyFormatter) o;

        return Objects.equals(fancyTextList, other.fancyTextList);
    }

    @Override
    public int hashCode() {
        return fancyTextList != null ? fancyTextList.hashCode() : 0;
    }

    public static FancyFormatter of() {
        return new FancyFormatter();
    }

    public static FancyFormatter of(String text) {
        return new FancyFormatter().append(new FancySegment(text));
    }

    public static FancyFormatter of(String text, String hoverText) {
        return new FancyFormatter().append(new FancySegment(text, hoverText));
    }

    public static FancyFormatter of(String text, String hoverText, String runCommand) {
        return new FancyFormatter().append(new FancySegment(text, hoverText, runCommand));
    }

    public static FancyFormatter of(String text, String hoverText, String clickActionText, ClickActionType clickActionType) {
        return new FancyFormatter().append(new FancySegment(text, hoverText, clickActionText, clickActionType));
    }

    public static FancyFormatter of(FancyText... fancyTexts) {
        return new FancyFormatter().append(fancyTexts);
    }
}
