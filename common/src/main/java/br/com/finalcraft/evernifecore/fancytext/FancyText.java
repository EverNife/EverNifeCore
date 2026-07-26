package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.fancytext.hover.FancyHover;
import br.com.finalcraft.evernifecore.fancytext.hover.ItemHover;
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

    FancyText replace(String placeholder, String value);

    FancyText replace(CompoundReplacer replacer);

    /**
     * Declares the value of {@code ${key}} (case-insensitive). Nothing is computed here: a key the
     * rendered text never cites is never resolved, and a key cited twice is resolved once.
     */
    FancyText placeholder(String key, Object value);

    /** Same as {@link #placeholder(String, Object)}, computing the value only if the text cites it. */
    FancyText placeholder(String key, Supplier<?> value);

    /**
     * Same as {@link #placeholder(String, Object)}, resolved against the recipient's PlayerData.
     * A recipient with no PlayerData (the console, for one) leaves the token as written.
     */
    FancyText placeholder(String key, Function<PlayerData, ?> value);

    /**
     * Declares several keys at once. A {@link Supplier} or {@link Function} value behaves as if it
     * had been passed to the matching {@code placeholder} overload.
     */
    FancyText placeholders(Map<String, ?> values);

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

    FancyText hover(String hoverText);

    /** Attaches an arbitrary registry-backed hover value - see {@link FancyHover}/{@code FancyHoverRegistry}. */
    FancyText hover(FancyHover hover);

    default FancyText hover(List<String> hoverText) {
        return hover(String.join("\n", hoverText));
    }

    default FancyText hoverItem(String serializedItem) {
        return hover(new ItemHover(serializedItem));
    }

    FancyText click(ClickActionType actionType);

    FancyText click(String clickActionText, ClickActionType actionType);

    default FancyText clickCommand(String command) {
        return click(command, ClickActionType.RUN_COMMAND);
    }

    default FancyText clickSuggest(String suggestion) {
        return click(suggestion, ClickActionType.SUGGEST_COMMAND);
    }

    default FancyText clickLink(String url) {
        return click(url, ClickActionType.OPEN_URL);
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
