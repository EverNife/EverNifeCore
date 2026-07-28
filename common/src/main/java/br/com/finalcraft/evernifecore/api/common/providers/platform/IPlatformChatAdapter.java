package br.com.finalcraft.evernifecore.api.common.providers.platform;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.text.ITextMetrics;
import br.com.finalcraft.evernifecore.util.FCTextUtil;

import java.util.List;

public interface IPlatformChatAdapter {

    /**
     * How this platform measures chat text. The line layout below is shared across platforms and
     * reads everything it needs from here, so a platform only has to answer this to get centring
     * and filled rules that match its own font.
     */
    public ITextMetrics getTextMetrics();

    default String alignCenter(String stringToAlign) {
        return FCTextUtil.alignCenter(getTextMetrics(), stringToAlign);
    }

    default String alignCenter(String stringToAlign, String borderFill) {
        return FCTextUtil.alignCenter(getTextMetrics(), stringToAlign, borderFill);
    }

    default String straightLineOf(String string) {
        return FCTextUtil.straightLineOf(getTextMetrics(), string);
    }

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
