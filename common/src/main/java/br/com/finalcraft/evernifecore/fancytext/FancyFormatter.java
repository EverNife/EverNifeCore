package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.fancytext.hover.FancyHover;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.placeholder.base.PlaceholderProvider;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * An ordered chain of {@link FancySegment} pieces, rendered as a single Adventure component.
 *
 * <p><b>A formatter never contains another formatter.</b> Appending one splices its pieces in, and
 * every piece that enters the chain is copied, so the chain owns what it holds outright: two
 * formatters can never share a piece, {@link #copy()} is structurally identical to its original, and
 * {@code copy().equals(original)} always holds - which is what stops the locale files from being
 * rewritten on every reload just because two equal messages compared unequal.</p>
 */
public class FancyFormatter implements FancyText {

    protected List<FancyText> fancyTextList = new ArrayList<>();
    protected transient MessagePlaceholders placeholders = null;

    public FancyFormatter append(FancyText... fancyTexts) {
        for (FancyText fancyText : fancyTexts) {
            append(fancyText);
        }
        return this;
    }

    /**
     * Appends {@code fancyText} to the end of this chain. A chain never contains another chain: a
     * formatter is spliced in piece by piece. Every piece is COPIED on the way in, so a caller that
     * goes on mutating the value it appended never reshapes what is already in here. To decorate what
     * was just appended, chain on the return value ({@code formatter.append(leaf).setHover(...)}),
     * which acts on the copy that is actually in the chain.
     */
    @Override
    public FancyFormatter append(FancyText fancyText) {
        if (fancyText instanceof FancyFormatter other) {
            for (FancyText fancyTextInner : other.fancyTextList) {
                FancyText piece = fancyTextInner.copy();
                // The chain being spliced in stops existing as a level, so what it declared has to
                // travel with its pieces - or the values would be lost the moment it is appended.
                if (other.placeholders != null && piece instanceof FancySegment) {
                    ((FancySegment) piece).messagePlaceholders().inheritMissing(other.placeholders);
                }
                this.fancyTextList.add(piece);
            }
        } else {
            this.fancyTextList.add(fancyText.copy());
        }
        return this;
    }

    @Override
    public FancyFormatter append(String text) {
        return append(new FancySegment(text));
    }

    @Override
    public FancyFormatter append(String text, String hoverText) {
        return append(new FancySegment(text, hoverText));
    }

    @Override
    public FancyFormatter append(String text, String hoverText, String runCommand) {
        return append(new FancySegment(text, hoverText, runCommand));
    }

    // A placeholder declared on the chain is visible to every piece in it; a piece that declares the
    // same key shadows it, the same way an inner scope shadows an outer one.
    @Override
    public FancyFormatter addPlaceholder(String key, Object value) {
        return addParser(key, context -> value);
    }

    @Override
    public FancyFormatter addPlaceholder(String key, Supplier<?> value) {
        return addParser(key, context -> value.get());
    }

    @Override
    public FancyFormatter addPlaceholder(String key, Function<PlayerData, ?> value) {
        return addParser(key, context -> context.getPlayerData() == null
                ? null                              // no PlayerData: the token is left as written
                : value.apply(context.getPlayerData()));
    }

    @Override
    public FancyFormatter addPlaceholders(Map<String, ?> values) {
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            FancySegment.declareByValueKind(this, entry.getKey(), entry.getValue());
        }
        return this;
    }

    @Override
    public FancyFormatter addParser(String key, Function<RenderContext, ?> parser) {
        return addParser(key, "", parser);
    }

    @Override
    public FancyFormatter addParser(String key, String description, Function<RenderContext, ?> parser) {
        messagePlaceholders().declare(key, description, parser::apply);
        return this;
    }

    @Override
    public FancyFormatter addReplacer(CompoundReplacer replacer) {
        messagePlaceholders().addReplacer(replacer);
        return this;
    }

    @Override
    public PlaceholderProvider<RenderContext> getPlaceholderProvider() {
        return messagePlaceholders().getProvider();
    }

    private MessagePlaceholders messagePlaceholders() {
        if (placeholders == null) {
            placeholders = new MessagePlaceholders();
        }
        return placeholders;
    }

    public List<FancyText> getFancyTextList() {
        return fancyTextList;
    }

    @Override
    public FancyFormatter replace(String placeholder, String value) {
        return bake(payload -> payload.replace(placeholder, value));
    }

    @Override
    public FancyFormatter bake(UnaryOperator<String> transform) {
        for (int i = 0; i < this.fancyTextList.size(); i++) {
            this.fancyTextList.set(i, this.fancyTextList.get(i).bake(transform));
        }
        return this;
    }

    @Override
    public RenderedText render(String startingColor, RenderContext context) {
        RenderContext pieceContext = placeholders == null || placeholders.isEmpty()
                ? context
                : context.inheriting(placeholders);

        TextComponent.Builder builder = Component.text();
        // Legacy chat lets a colour bleed into the following text; Adventure siblings do not inherit
        // one another's colour, so carry each piece's trailing colour into the next as its start.
        String previousColor = startingColor;
        for (FancyText fancyText : fancyTextList) {
            RenderedText rendered = fancyText.render(previousColor, pieceContext);
            builder.append(rendered.getComponent());
            previousColor = rendered.getTrailingColor();
        }
        return new RenderedText(builder.build(), previousColor);
    }

    @Override
    public FancyFormatter copy() {
        FancyFormatter copy = new FancyFormatter();
        //Rebuilt straight onto the list rather than through append(), so that copying stays a
        //structural operation and does not inherit whatever policy append happens to have.
        for (FancyText fancyText : this.fancyTextList) {
            copy.fancyTextList.add(fancyText.copy());
        }
        if (this.placeholders != null) {
            copy.placeholders = this.placeholders.copy();
        }
        return copy;
    }

    // Getters/setters target the last appended segment; an empty formatter has none, so degrade
    // gracefully (null / NONE / no-op) instead of throwing IndexOutOfBounds.
    private FancyText lastOrNull() {
        return fancyTextList.isEmpty() ? null : fancyTextList.get(fancyTextList.size() - 1);
    }

    /**
     * The last piece of this chain, or {@code null} when there is none. The single-attribute setters
     * already act on it implicitly; this is for the caller who would rather say so out loud than rely
     * on that.
     */
    public FancyText last() {
        return lastOrNull();
    }

    @Override
    public boolean isEmpty() {
        for (FancyText fancyText : fancyTextList) {
            if (!fancyText.isEmpty()) {
                return false;
            }
        }
        return true;
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
    public FancyFormatter setText(String text) {
        FancyText last = lastOrNull();
        if (last != null) last.setText(text);
        return this;
    }

    @Override
    public FancyFormatter setHover(String hoverText) {
        FancyText last = lastOrNull();
        if (last != null) last.setHover(hoverText);
        return this;
    }

    @Override
    public FancyFormatter setHover(FancyHover hover) {
        FancyText last = lastOrNull();
        if (last != null) last.setHover(hover);
        return this;
    }

    @Override
    public FancyFormatter setClickType(ClickActionType actionType) {
        FancyText last = lastOrNull();
        if (last != null) last.setClickType(actionType);
        return this;
    }

    @Override
    public FancyFormatter setClick(String actionText, ClickActionType actionType) {
        FancyText last = lastOrNull();
        if (last != null) last.setClick(actionText, actionType);
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
