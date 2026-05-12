package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.version.FCPlatformType;
import jakarta.annotation.Nullable;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

public class FancyText {

    protected String text = "";
    protected String hoverText = null;
    protected String clickActionText = null;
    protected ClickActionType clickActionType = ClickActionType.NONE;
    protected String lastColor = "";

    private boolean recentChanged = true;
    private String lastStartingColor = "";
    private transient Component cachedComponent = null;
    protected transient @Nullable FancyFormatter fancyFormatter;

    public static final Map<String, BiConsumer<ComponentBuilder<?, ?>, String>> HOVER_HANDLERS = new LinkedHashMap<>();

    static {
        HOVER_HANDLERS.put("$show_item$", (builder, value) -> {
            builder.hoverEvent(HoverEvent.showItem(Key.key(value), 1, BinaryTagHolder.binaryTagHolder(value)));
        });
    }

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

    public FancyText setText(String text) {
        setRecentChanged();
        this.text = text;
        return this;
    }

    public FancyText replace(String placeholder, String value) {
        setRecentChanged();
        this.text = text.replace(placeholder, value);
        if (this.hoverText != null) this.hoverText = this.hoverText.replace(placeholder, value);
        if (this.clickActionText != null) this.clickActionText = this.clickActionText.replace(placeholder, value);
        return this;
    }

    public FancyText replace(CompoundReplacer replacer) {
        setRecentChanged();
        this.text = replacer.apply(this.text);
        if (this.hoverText != null) this.hoverText = replacer.apply(this.hoverText);
        if (this.clickActionText != null) this.clickActionText = replacer.apply(this.clickActionText);
        return this;
    }

    protected FancyFormatter getOrCreateFormmater() {
        if (this.fancyFormatter == null) {
            this.fancyFormatter = new FancyFormatter();
            this.fancyFormatter.append(this);
        }
        return this.fancyFormatter;
    }

    public FancyFormatter append(String text) {
        return getOrCreateFormmater().append(new FancyText(text));
    }

    public FancyFormatter append(String text, String hoverText) {
        return getOrCreateFormmater().append(new FancyText(text, hoverText));
    }

    public FancyFormatter append(String text, String hoverText, String runCommand) {
        return getOrCreateFormmater().append(new FancyText(text, hoverText, runCommand));
    }

    public FancyFormatter append(FancyText fancyText) {
        return getOrCreateFormmater().append(fancyText);
    }

    public FancyText setHoverText(String hoverText) {
        setRecentChanged();
        this.hoverText = hoverText;
        return this;
    }

    public FancyText setHoverText(List<String> hoverText) {
        setRecentChanged();
        this.hoverText = String.join("\n", hoverText);
        return this;
    }

    public FancyText setHoverItem(String serializedItem) {
        setRecentChanged();
        this.hoverText = "$show_item$" + serializedItem;
        return this;
    }

    public FancyText setClickAction(ClickActionType actionType) {
        this.setRecentChanged();
        this.clickActionType = actionType;
        return this;
    }

    public FancyText setClickAction(String clickActionText, ClickActionType actionType) {
        setRecentChanged();
        this.clickActionText = clickActionText;
        this.clickActionType = actionType;
        return this;
    }

    public FancyText setRunCommandAction(String runCommandAction) {
        return setClickAction(runCommandAction, ClickActionType.RUN_COMMAND);
    }

    public FancyText setSuggestCommandAction(String suggestCommandAction) {
        return setClickAction(suggestCommandAction, ClickActionType.SUGGEST_COMMAND);
    }

    public FancyText setOpenLinkAction(String linkToOpen) {
        return setClickAction(linkToOpen, ClickActionType.OPEN_URL);
    }

    public void setRecentChanged() {
        recentChanged = true;
        cachedComponent = null;
    }

    public Component toComponent() {
        return toComponent("");
    }

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

    protected String getLastTextColor() {
        return lastColor;
    }

    public void send(FCommandSender... commandSender) {
        FancyTextManager.send(this, commandSender);
    }

    public void broadcast() {
        EverNifeCore.getPlatform().getChatAdapter().broadcast(this);
    }

    public FancyText clone() {
        return new FancyText(text, hoverText, clickActionText, clickActionType);
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

    public static FancyText of() {
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
