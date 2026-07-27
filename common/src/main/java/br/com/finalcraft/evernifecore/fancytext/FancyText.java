package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.fancytext.hover.FancyHover;
import br.com.finalcraft.evernifecore.fancytext.hover.ItemHover;
import br.com.finalcraft.evernifecore.placeholder.base.PlaceholderProvider;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import jakarta.annotation.Nullable;
import net.kyori.adventure.text.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Root of the rich-text model. A {@link FancySegment} is one styled piece (text + hover + click);
 * a {@link FancyFormatter} is an ordered chain of pieces. Both render to an Adventure {@link Component}.
 */
public interface FancyText {

    String getText();

    String getHoverText();

    /** The structured hover value attached to this piece, or {@code null} if none. */
    @Nullable
    FancyHover getHover();

    String getClickActionText();

    ClickActionType getClickActionType();

    /**
     * Whether this would render to nothing at all: a leaf with no text, or a chain with no pieces or
     * whose every piece is itself empty. Decoration does not count - a hover on an empty run of text
     * has nothing to hover over.
     */
    boolean isEmpty();

    FancyText setText(String text);

    /**
     * Bakes {@code value} into this message's text, hover payload and click value, right now and
     * literally ({@link String#replace}). This is NOT the placeholder engine - it mutates the object -
     * and it exists for the derived copy: a message that has to carry an already-resolved value with
     * it, such as a per-instance command alias or a cached page line.
     */
    FancyText replace(String placeholder, String value);

    /**
     * Declares the value of {@code ${key}} (case-insensitive) on this message. Nothing is computed
     * here: a key the rendered text never cites is never resolved, and a key cited twice is resolved
     * once. The key is taken exactly as written, so it must be the bare name - {@code "saldo"}, never
     * {@code "%saldo%"} or {@code "${saldo}"}.
     */
    FancyText addPlaceholder(String key, Object value);

    /** Same as {@link #addPlaceholder(String, Object)}, computing the value only if the text cites it. */
    FancyText addPlaceholder(String key, Supplier<?> value);

    /**
     * Same as {@link #addPlaceholder(String, Object)}, resolved against the recipient's PlayerData.
     * A recipient with no PlayerData (the console, for one) leaves the token as written.
     */
    FancyText addPlaceholder(String key, Function<PlayerData, ?> value);

    /**
     * Declares several keys at once. A {@link Supplier} or {@link Function} value behaves as if it
     * had been passed to the matching {@code addPlaceholder} overload.
     */
    FancyText addPlaceholders(Map<String, ?> values);

    /** Same as {@link #addParser(String, String, Function)} with no description. */
    FancyText addParser(String key, Function<RenderContext, ?> parser);

    /**
     * Declares a key whose value is computed from the whole render context, with a description that
     * an integrating plugin can list back to the user through {@link #getPlaceholderProvider()}.
     */
    FancyText addParser(String key, String description, Function<RenderContext, ?> parser);

    /** Attaches a replacer applied to this message on top of its own {@code ${key}} declarations. */
    FancyText addReplacer(CompoundReplacer replacer);

    /** The keys this message answers for, for listing them - see {@code PlaceholderProvider#describeAll}. */
    PlaceholderProvider<RenderContext> getPlaceholderProvider();

    FancyFormatter append(String text);

    FancyFormatter append(String text, String hoverText);

    FancyFormatter append(String text, String hoverText, String runCommand);

    FancyFormatter append(FancyText fancyText);

    /** Appends {@code text} on its own line (a leading newline), so a caller stops spelling the {@code "\n"}. */
    default FancyFormatter appendLine(String text) {
        return append("\n" + text);
    }

    /** {@link #appendLine(String)} of {@code String.format(format, args)}. */
    default FancyFormatter appendLine(String format, Object... args) {
        return appendLine(String.format(format, args));
    }

    /**
     * Replaces whatever hover this piece had with a plain tooltip. A message carries at most one
     * hover, so this never accumulates - calling it twice leaves only the second value.
     */
    FancyText setHover(String hoverText);

    /**
     * Replaces the hover with an arbitrary registry-backed value - see
     * {@link FancyHover}/{@code FancyHoverRegistry}.
     */
    FancyText setHover(FancyHover hover);

    /** {@link #setHover(String)} of the lines joined by a newline. */
    default FancyText setHover(List<String> hoverText) {
        return setHover(String.join("\n", hoverText));
    }

    /** {@link #setHover(String)} of the lines joined by a newline. */
    default FancyText setHover(String... hoverText) {
        return setHover(String.join("\n", hoverText));
    }

    /** {@link #setHover(FancyHover)} of the item form, from an item id or a serialized item. */
    default FancyText setHoverItem(String serializedItem) {
        return setHover(new ItemHover(serializedItem));
    }

    /** Replaces the click type, keeping whatever value the click already carried. */
    FancyText setClickType(ClickActionType actionType);

    /** Replaces both the click value and its type. A message carries at most one click. */
    FancyText setClick(String clickActionText, ClickActionType actionType);

    default FancyText setClickCommand(String command) {
        return setClick(command, ClickActionType.RUN_COMMAND);
    }

    default FancyText setClickSuggest(String suggestion) {
        return setClick(suggestion, ClickActionType.SUGGEST_COMMAND);
    }

    default FancyText setClickLink(String url) {
        return setClick(url, ClickActionType.OPEN_URL);
    }

    Component toComponent();

    Component toComponent(String startingColor);

    /**
     * Renders for one recipient, resolving the {@code ${key}} placeholders declared on this text.
     * The plain {@link #toComponent()} overloads resolve nothing, which is why a message with
     * placeholders has to be rendered per recipient.
     */
    default Component toComponent(RenderContext context) {
        return toComponent("", context);
    }

    Component toComponent(String startingColor, RenderContext context);

    /** The rendered legacy ({@code §}-formatted) string, without sending it. */
    default String toLegacyString() {
        return FCColorUtil.componentToString(toComponent());
    }

    /** The rendered legacy string with {@code ${key}} placeholders resolved for this render, without sending it. */
    default String toLegacyString(RenderContext context) {
        return FCColorUtil.componentToString(toComponent(context));
    }

    /** The rendered text with the colour codes stripped, without sending it. */
    default String toPlainText() {
        return FCColorUtil.stripColor(toLegacyString());
    }

    /** The rendered, placeholder-resolved text with the colour codes stripped, without sending it. */
    default String toPlainText(RenderContext context) {
        return FCColorUtil.stripColor(toLegacyString(context));
    }

    String getLastTextColor();

    void send(FCommandSender... commandSender);

    /** Sends to every recipient in {@code commandSenders}, mirroring {@code ILocaleMessageBase.send(List)}. */
    default void send(List<FCommandSender> commandSenders) {
        send(commandSenders.toArray(new FCommandSender[0]));
    }

    default void broadcast() {
        EverNifeCore.getPlatform().getChatAdapter().broadcast(this);
    }

    FancyText copy();

    static FancyText of() {
        return new FancySegment();
    }

    static FancyText of(String text) {
        return new FancySegment(text);
    }

    static FancyText of(String text, String hoverText) {
        return new FancySegment(text, hoverText);
    }

    static FancyText of(String text, String hoverText, String runCommand) {
        return new FancySegment(text, hoverText, runCommand);
    }

    static FancyText of(String text, String hoverText, String clickActionText, ClickActionType clickActionType) {
        return new FancySegment(text, hoverText, clickActionText, clickActionType);
    }

    /** A chain with one piece per line, so a multi-line block stops being one giant string. */
    static FancyText of(List<String> lines) {
        FancyFormatter formatter = new FancyFormatter();
        for (int i = 0; i < lines.size(); i++) {
            formatter.append(i == 0 ? lines.get(i) : "\n" + lines.get(i));
        }
        return formatter;
    }

    /**
     * One piece per item, separated by {@code separator}: the shape of every "comma-separated list of
     * clickable things" a command output builds. Each piece keeps its own hover and click, which is
     * exactly what joining the rendered strings instead would throw away.
     */
    static <T> FancyFormatter join(String separator, Collection<T> items, Function<T, FancyText> mapper) {
        FancyFormatter formatter = new FancyFormatter();
        boolean first = true;
        for (T item : items) {
            if (!first) {
                formatter.append(separator);
            }
            formatter.append(mapper.apply(item));
            first = false;
        }
        return formatter;
    }
}
