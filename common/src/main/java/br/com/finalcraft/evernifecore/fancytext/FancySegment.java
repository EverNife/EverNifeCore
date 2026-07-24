package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.fancytext.hover.FancyHover;
import br.com.finalcraft.evernifecore.fancytext.hover.FancyHoverRegistry;
import br.com.finalcraft.evernifecore.fancytext.hover.ItemHover;
import br.com.finalcraft.evernifecore.fancytext.hover.TextHover;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.version.FCPlatformType;
import jakarta.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * One styled leaf of the rich-text model: a single run of text plus its own hover and click. See
 * {@link FancyFormatter} for the ordered chain of leaves.
 */
public class FancySegment implements FancyText {

    protected String text = "";
    protected FancyHover hover = null;
    protected String clickActionText = null;
    protected ClickActionType clickActionType = ClickActionType.NONE;
    protected String lastColor = "";

    private boolean recentChanged = true;
    private String lastStartingColor = "";
    private transient Component cachedComponent = null;

    public FancySegment() {
    }

    public FancySegment(String text) {
        this.text = text;
    }

    public FancySegment(String text, String hoverText) {
        this.text = text;
        this.hover = legacyHoverOf(hoverText);
    }

    public FancySegment(String text, String hoverText, String runCommand) {
        this.text = text;
        this.hover = legacyHoverOf(hoverText);
        this.clickActionText = runCommand;
        this.clickActionType = ClickActionType.RUN_COMMAND;
    }

    public FancySegment(String text, String hoverText, String clickActionText, ClickActionType clickActionType) {
        this.text = text;
        this.hover = legacyHoverOf(hoverText);
        this.clickActionText = clickActionText;
        this.clickActionType = clickActionType;
    }

    /** The legacy string form: a plain tooltip, or a {@code "$show_item$"}-prefixed item id/SNBT string. */
    private static FancyHover legacyHoverOf(@Nullable String hoverText) {
        if (hoverText == null) {
            return null;
        }
        if (hoverText.startsWith(ItemHover.LEGACY_SENTINEL)) {
            return new ItemHover(hoverText.substring(ItemHover.LEGACY_SENTINEL.length()));
        }
        return new TextHover(hoverText);
    }

    /**
     * Only the legacy string-payload hover kinds (plain text, item id) support placeholder
     * substitution today; a custom registry type's payload is opaque to this class and passes
     * through untouched.
     */
    private static FancyHover replaceHoverPayload(FancyHover hover, UnaryOperator<String> transform) {
        if (hover instanceof TextHover) {
            return new TextHover(transform.apply(((TextHover) hover).text()));
        }
        if (hover instanceof ItemHover) {
            return new ItemHover(transform.apply(((ItemHover) hover).rawItem()));
        }
        return hover;
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public String getHoverText() {
        return hover == null ? null : hover.toLegacyPayload();
    }

    @Override
    public FancyHover getHover() {
        return hover;
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
        this.hover = replaceHoverPayload(this.hover, legacyPayload -> legacyPayload.replace(placeholder, value));
        if (this.clickActionText != null) this.clickActionText = this.clickActionText.replace(placeholder, value);
        return this;
    }

    @Override
    public FancySegment replace(CompoundReplacer replacer) {
        setRecentChanged();
        this.text = replacer.apply(this.text);
        this.hover = replaceHoverPayload(this.hover, replacer::apply);
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
    public FancySegment hover(String hoverText) {
        setRecentChanged();
        this.hover = legacyHoverOf(hoverText);
        return this;
    }

    @Override
    public FancySegment hover(FancyHover hover) {
        setRecentChanged();
        this.hover = hover;
        return this;
    }

    @Override
    public FancySegment click(ClickActionType actionType) {
        this.setRecentChanged();
        this.clickActionType = actionType;
        return this;
    }

    @Override
    public FancySegment click(String clickActionText, ClickActionType actionType) {
        setRecentChanged();
        this.clickActionText = clickActionText;
        this.clickActionType = actionType;
        return this;
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

        if (this.hover != null) {
            HoverEvent<?> hoverEvent = FancyHoverRegistry.resolve(this.hover);
            if (hoverEvent != null) {
                builder.hoverEvent(hoverEvent);
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
        // Copies the hover value by reference rather than round-tripping it through a legacy string:
        // a custom registry type has no such string form at all, so the round trip would silently
        // drop it (see FancyHover#toLegacyPayload).
        FancySegment clone = new FancySegment(text);
        clone.hover = this.hover;
        clone.clickActionText = this.clickActionText;
        clone.clickActionType = this.clickActionType;
        return clone;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FancySegment fancyText = (FancySegment) o;

        if (!Objects.equals(text, fancyText.text)) return false;
        if (!Objects.equals(hover, fancyText.hover)) return false;
        if (!Objects.equals(clickActionText, fancyText.clickActionText)) return false;

        return clickActionType == fancyText.clickActionType;
    }

    @Override
    public int hashCode() {
        int result = text != null ? text.hashCode() : 0;
        result = 31 * result + (hover != null ? hover.hashCode() : 0);
        result = 31 * result + (clickActionText != null ? clickActionText.hashCode() : 0);
        result = 31 * result + (clickActionType != null ? clickActionType.hashCode() : 0);
        return result;
    }
}
