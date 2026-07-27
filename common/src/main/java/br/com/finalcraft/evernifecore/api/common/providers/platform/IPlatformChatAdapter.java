package br.com.finalcraft.evernifecore.api.common.providers.platform;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.fancytext.FancyText;

import java.util.List;

public interface IPlatformChatAdapter {

    public String alignCenter(String stringToAlign);

    public String alignCenter(String stringToAlign, String borderFill);

    public String straightLineOf(String string);

    /**
     * Every recipient a broadcast reaches: the online players AND the console. This is the single
     * point of truth for that audience - a message broadcast as a {@link FancyText} and the same
     * message broadcast through a locale must not reach different people.
     */
    public List<FCommandSender> getBroadcastAudience();

    default void broadcast(FancyText fancyText) {
        fancyText.send(getBroadcastAudience());
    }

    /**
     * Whether this platform can actually show a hover of the given
     * {@link br.com.finalcraft.evernifecore.fancytext.hover.FancyHover#typeId()}. The render
     * pipeline asks this before attaching a hover event; {@code false} makes it degrade (see
     * {@link br.com.finalcraft.evernifecore.fancytext.hover.FancyHoverType}) instead of sending a
     * payload the platform cannot interpret.
     */
    public boolean supportsHover(String typeId);

}
