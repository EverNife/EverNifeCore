package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.version.FCPlatformType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * One styled leaf of the rich-text model: a single run of text plus its own hover and click. See
 * {@link FancyFormatter} for the ordered chain of leaves.
 */
public class FancySegment implements FancyText {

    protected String text = "";
    protected String hoverText = null;
    protected String clickActionText = null;
    protected ClickActionType clickActionType = ClickActionType.NONE;
    protected String lastColor = "";

    private boolean recentChanged = true;
    private String lastStartingColor = "";
    private transient Component cachedComponent = null;

    public static final Map<String, BiConsumer<ComponentBuilder<?, ?>, String>> HOVER_HANDLERS = new LinkedHashMap<>();

    static {
        HOVER_HANDLERS.put("$show_item$", (builder, value) -> {
            builder.hoverEvent(HoverEvent.showItem(Key.key(value), 1, BinaryTagHolder.binaryTagHolder(value)));
        });
    }

    public FancySegment() {
    }

    public FancySegment(String text) {
        this.text = text;
    }

    public FancySegment(String text, String hoverText) {
        this.text = text;
        this.hoverText = hoverText;
    }

    public FancySegment(String text, String hoverText, String runCommand) {
        this.text = text;
        this.hoverText = hoverText;
        this.clickActionText = runCommand;
        this.clickActionType = ClickActionType.RUN_COMMAND;
    }

    public FancySegment(String text, String hoverText, String clickActionText, ClickActionType clickActionType) {
        this.text = text;
        this.hoverText = hoverText;
        this.clickActionText = clickActionText;
        this.clickActionType = clickActionType;
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public String getHoverText() {
        return hoverText;
    }

    @Override
    public String getClickActionText() {
        return clickActionText;
    }

    @Override
    public ClickActionType getClickActionType() {
        return clickActionType;
    }

    @Override
    public FancySegment setText(String text) {
        setRecentChanged();
        this.text = text;
        return this;
    }

    @Override
    public FancySegment replace(String placeholder, String value) {
        setRecentChanged();
        this.text = text.replace(placeholder, value);
        if (this.hoverText != null) this.hoverText = this.hoverText.replace(placeholder, value);
        if (this.clickActionText != null) this.clickActionText = this.clickActionText.replace(placeholder, value);
        return this;
    }

    @Override
    public FancySegment replace(CompoundReplacer replacer) {
        setRecentChanged();
        this.text = replacer.apply(this.text);
        if (this.hoverText != null) this.hoverText = replacer.apply(this.hoverText);
        if (this.clickActionText != null) this.clickActionText = replacer.apply(this.clickActionText);
        return this;
    }

    // Unlike the old model, appending never mutates this leaf: it comes back as a brand-new
    // formatter holding this leaf plus the new content, so a shared leaf reference never changes
    // shape out from under whoever else is still holding it.
    @Override
    public FancyFormatter append(String text) {
        return new FancyFormatter().append(this).append(new FancySegment(text));
    }

    @Override
    public FancyFormatter append(String text, String hoverText) {
        return new FancyFormatter().append(this).append(new FancySegment(text, hoverText));
    }

    @Override
    public FancyFormatter append(String text, String hoverText, String runCommand) {
        return new FancyFormatter().append(this).append(new FancySegment(text, hoverText, runCommand));
    }

    @Override
    public FancyFormatter append(FancyText fancyText) {
        return new FancyFormatter().append(this).append(fancyText);
    }

    @Override
    public FancySegment setHoverText(String hoverText) {
        setRecentChanged();
        this.hoverText = hoverText;
        return this;
    }

    @Override
    public FancySegment setClickAction(ClickActionType actionType) {
        this.setRecentChanged();
        this.clickActionType = actionType;
        return this;
    }

    @Override
    public FancySegment setClickAction(String clickActionText, ClickActionType actionType) {
        setRecentChanged();
        this.clickActionText = clickActionText;
        this.clickActionType = actionType;
        return this;
    }

    @Override
    public FancySegment setRunCommandAction(String runCommandAction) {
        return setClickAction(runCommandAction, ClickActionType.RUN_COMMAND);
    }

    @Override
    public FancySegment setSuggestCommandAction(String suggestCommandAction) {
        return setClickAction(suggestCommandAction, ClickActionType.SUGGEST_COMMAND);
    }

    @Override
    public FancySegment setOpenLinkAction(String linkToOpen) {
        return setClickAction(linkToOpen, ClickActionType.OPEN_URL);
    }

    private void setRecentChanged() {
        recentChanged = true;
        cachedComponent = null;
    }

    @Override
    public Component toComponent() {
        return toComponent("");
    }

    @Override
    public Component toComponent(String startingColor) {
        if (!startingColor.equals(lastStartingColor)) {
            setRecentChanged();
        }
        if (cachedComponent != null && !recentChanged) {
            return cachedComponent;
        }

        recentChanged = false;
        this.lastStartingColor = startingColor;

        String fixedText = startingColor + (
                FCPlatformType.isHytale()
                        ? this.text.replace("●", "•").replace("▶", "•")
                        : this.text
        );

        this.lastColor = FCColorUtil.getLastColors(fixedText);

        Component textComponent = FCColorUtil.colorfyComponent(fixedText);
        ComponentBuilder<?, ?> builder = textComponent.toBuilder();

        if (this.hoverText != null && !this.hoverText.isEmpty()) {
            boolean handled = false;
            for (Map.Entry<String, BiConsumer<ComponentBuilder<?, ?>, String>> entry : HOVER_HANDLERS.entrySet()) {
                if (this.hoverText.startsWith(entry.getKey())) {
                    entry.getValue().accept(builder, this.hoverText.substring(entry.getKey().length()));
                    handled = true;
                    break;
                }
            }
            if (!handled) {
                builder.hoverEvent(HoverEvent.showText(FCColorUtil.colorfyComponent(this.hoverText)));
            }
        }

        if (this.clickActionText != null) {
            switch (this.clickActionType) {
                case RUN_COMMAND:
                    builder.clickEvent(ClickEvent.runCommand(this.clickActionText));
                    break;
                case OPEN_URL:
                    builder.clickEvent(ClickEvent.openUrl(this.clickActionText));
                    break;
                case SUGGEST_COMMAND:
                    builder.clickEvent(ClickEvent.suggestCommand(this.clickActionText));
                    break;
                case NONE:
                    break;
            }
        }

        cachedComponent = builder.build();
        return cachedComponent;
    }

    @Override
    public String getLastTextColor() {
        return lastColor;
    }

    @Override
    public void send(FCommandSender... commandSender) {
        FancyTextManager.send(this, commandSender);
    }

    @Override
    public FancySegment clone() {
        return new FancySegment(text, hoverText, clickActionText, clickActionType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FancySegment fancyText = (FancySegment) o;

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
}
