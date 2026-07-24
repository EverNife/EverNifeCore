package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.fancytext.hover.FancyHover;
import br.com.finalcraft.evernifecore.fancytext.hover.ItemHover;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import jakarta.annotation.Nullable;
import net.kyori.adventure.text.Component;

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

    String getLastTextColor();

    void send(FCommandSender... commandSender);

    default void broadcast() {
        EverNifeCore.getPlatform().getChatAdapter().broadcast(this);
    }

    FancyText clone();

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
}
