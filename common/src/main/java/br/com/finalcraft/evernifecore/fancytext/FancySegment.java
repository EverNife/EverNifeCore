package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.fancytext.hover.FancyHover;
import br.com.finalcraft.evernifecore.fancytext.hover.FancyHoverRegistry;
import br.com.finalcraft.evernifecore.fancytext.hover.ItemHover;
import br.com.finalcraft.evernifecore.fancytext.hover.TextHover;
import br.com.finalcraft.evernifecore.placeholder.base.PlaceholderProvider;
import br.com.finalcraft.evernifecore.placeholder.replacer.Closures;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.version.FCPlatformType;
import jakarta.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
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
    protected transient MessagePlaceholders placeholders = null;

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

    private static FancyHover replaceHoverPayload(FancyHover hover, UnaryOperator<String> transform) {
        return hover == null ? null : hover.replacePayload(transform);
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
    public boolean isEmpty() {
        return text == null || text.isEmpty();
    }

    @Override
    public FancySegment setText(String text) {
        this.text = text;
        return this;
    }

    @Override
    public FancySegment replace(String placeholder, String value) {
        return bake(payload -> payload.replace(placeholder, value));
    }

    @Override
    public FancySegment bake(UnaryOperator<String> transform) {
        this.text = transform.apply(this.text);
        this.hover = replaceHoverPayload(this.hover, transform);
        if (this.clickActionText != null) this.clickActionText = transform.apply(this.clickActionText);
        return this;
    }

    @Override
    public FancySegment addPlaceholder(String key, Object value) {
        return addParser(key, context -> value);
    }

    @Override
    public FancySegment addPlaceholder(String key, Supplier<?> value) {
        return addParser(key, context -> value.get());
    }

    @Override
    public FancySegment addPlaceholder(String key, Function<PlayerData, ?> value) {
        return addParser(key, context -> context.getPlayerData() == null
                ? null                              // no PlayerData: the token is left as written
                : value.apply(context.getPlayerData()));
    }

    @Override
    public FancySegment addPlaceholders(Map<String, ?> values) {
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            declareByValueKind(this, entry.getKey(), entry.getValue());
        }
        return this;
    }

    // A map is untyped by nature, so the kind of value decides which overload it would have picked.
    @SuppressWarnings("unchecked")
    static void declareByValueKind(FancyText fancyText, String key, Object value) {
        if (value instanceof Supplier) {
            fancyText.addPlaceholder(key, (Supplier<?>) value);
        } else if (value instanceof Function) {
            fancyText.addPlaceholder(key, (Function<PlayerData, ?>) value);
        } else {
            fancyText.addPlaceholder(key, value);
        }
    }

    @Override
    public FancySegment addParser(String key, Function<RenderContext, ?> parser) {
        return addParser(key, "", parser);
    }

    @Override
    public FancySegment addParser(String key, String description, Function<RenderContext, ?> parser) {
        messagePlaceholders().declare(key, description, parser::apply);
        return this;
    }

    @Override
    public FancySegment addReplacer(CompoundReplacer replacer) {
        messagePlaceholders().addReplacer(replacer);
        return this;
    }

    @Override
    public PlaceholderProvider<RenderContext> getPlaceholderProvider() {
        return messagePlaceholders().getProvider();
    }

    MessagePlaceholders messagePlaceholders() {
        if (placeholders == null) {
            placeholders = new MessagePlaceholders();
        }
        return placeholders;
    }

    // Appending never mutates this leaf: it comes back as a brand-new formatter holding a COPY of
    // this leaf plus the new content, so a shared leaf reference never changes shape out from under
    // whoever else is still holding it - and neither does the chain, if this leaf is mutated later.
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
    public FancySegment setHover(String hoverText) {
        this.hover = legacyHoverOf(hoverText);
        return this;
    }

    @Override
    public FancySegment setHover(FancyHover hover) {
        this.hover = hover;
        return this;
    }

    @Override
    public FancySegment setClickType(ClickActionType actionType) {
        this.clickActionType = actionType;
        return this;
    }

    @Override
    public FancySegment setClick(String clickActionText, ClickActionType actionType) {
        this.clickActionText = clickActionText;
        this.clickActionType = actionType;
        return this;
    }

    @Override
    public RenderedText render(String startingColor, RenderContext context) {
        // The resolved copy is what actually renders, so the colour it ends on is the one that
        // carries over - and it travels out as a value instead of being written back onto a field
        // that another thread's render is reading at the same time.
        FancySegment rendered = needsResolving(context) ? resolvedCopy(context) : this;
        return rendered.renderLiteral(startingColor);
    }

    // No placeholder left to resolve: everything here reads this leaf and writes nothing.
    private RenderedText renderLiteral(String startingColor) {
        String fixedText = startingColor + (
                FCPlatformType.isHytale()
                        ? this.text.replace("●", "•").replace("▶", "•")
                        : this.text
        );

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

        return new RenderedText(builder.build(), FCColorUtil.getLastColors(fixedText));
    }

    // What forces a per-recipient render is the TEXT citing a key, not the message owning one, so
    // the gate is a cheap search for the closure head. A replacer speaks its own delimiters and is
    // opaque here, so its mere presence is enough to give up on the cached component.
    private boolean needsResolving(RenderContext context) {
        if (citesClosure(text) || citesClosure(clickActionText) || citesClosure(hoverPayload())) {
            return true;
        }
        if (placeholders != null && placeholders.hasReplacer()) {
            return true;
        }
        for (MessagePlaceholders outer : context.getInherited()) {
            if (outer.hasReplacer()) {
                return true;
            }
        }
        return false;
    }

    private static boolean citesClosure(@Nullable String text) {
        return text != null && text.contains(Closures.DOLLAR_CURLY.getHead());
    }

    private @Nullable String hoverPayload() {
        if (hover instanceof TextHover) return ((TextHover) hover).text();
        if (hover instanceof ItemHover) return ((ItemHover) hover).rawItem();
        return null;
    }

    private FancySegment resolvedCopy(RenderContext context) {
        FancySegment copy = new FancySegment(resolve(this.text, context));
        copy.hover = replaceHoverPayload(this.hover, payload -> resolve(payload, context));
        copy.clickActionText = resolve(this.clickActionText, context);
        copy.clickActionType = this.clickActionType;
        copy.placeholders = this.placeholders;
        return copy;
    }

    // Own declarations first, then the levels containing this piece, and the framework-wide keys
    // last: whatever a nearer level resolved is no longer a token by the time the next one runs,
    // which is exactly what shadowing means here.
    private @Nullable String resolve(@Nullable String value, RenderContext context) {
        if (value == null) {
            return null;
        }
        String resolved = placeholders == null ? value : placeholders.apply(value, context);
        for (MessagePlaceholders outer : context.getInherited()) {
            resolved = outer.apply(resolved, context);
        }
        return CoreFancyMessageParsers.INSTANCE.apply(resolved, context);
    }

    @Override
    public void send(FCommandSender... commandSender) {
        FancyTextManager.send(this, commandSender);
    }

    @Override
    public FancySegment copy() {
        FancySegment copy = new FancySegment(text);
        copy.hover = this.hover == null ? null : this.hover.copy();
        copy.clickActionText = this.clickActionText;
        copy.clickActionType = this.clickActionType;
        if (this.placeholders != null) {
            copy.placeholders = this.placeholders.copy();
        }
        return copy;
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
