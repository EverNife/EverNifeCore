package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * Root of the rich-text model. A {@link FancySegment} is one styled piece (text + hover + click);
 * a {@link FancyFormatter} is an ordered chain of pieces. Both render to an Adventure {@link Component}.
 */
public interface FancyText {

    String getText();

    String getHoverText();

    String getClickActionText();

    ClickActionType getClickActionType();

    FancyText setText(String text);

    FancyText replace(String placeholder, String value);

    FancyText replace(CompoundReplacer replacer);

    FancyFormatter append(String text);

    FancyFormatter append(String text, String hoverText);

    FancyFormatter append(String text, String hoverText, String runCommand);

    FancyFormatter append(FancyText fancyText);

    FancyText setHoverText(String hoverText);

    default FancyText setHoverText(List<String> hoverText) {
        return setHoverText(String.join("\n", hoverText));
    }

    default FancyText setHoverItem(String serializedItem) {
        return setHoverText("$show_item$" + serializedItem);
    }

    FancyText setClickAction(ClickActionType actionType);

    FancyText setClickAction(String clickActionText, ClickActionType actionType);

    FancyText setRunCommandAction(String runCommandAction);

    FancyText setSuggestCommandAction(String suggestCommandAction);

    FancyText setOpenLinkAction(String linkToOpen);

    Component toComponent();

    Component toComponent(String startingColor);

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
