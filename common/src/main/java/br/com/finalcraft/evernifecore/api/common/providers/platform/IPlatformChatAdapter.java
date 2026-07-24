package br.com.finalcraft.evernifecore.api.common.providers.platform;

import br.com.finalcraft.evernifecore.fancytext.FancyText;

public interface IPlatformChatAdapter {

    public String alignCenter(String stringToAlign);

    public String alignCenter(String stringToAlign, String borderFill);

    public String straightLineOf(String string);

    public void broadcast(FancyText fancyText);

    /**
     * Whether this platform can actually show a hover of the given
     * {@link br.com.finalcraft.evernifecore.fancytext.hover.FancyHover#typeId()}. The render
     * pipeline asks this before attaching a hover event; {@code false} makes it degrade (see
     * {@link br.com.finalcraft.evernifecore.fancytext.hover.FancyHoverType}) instead of sending a
     * payload the platform cannot interpret.
     */
    public boolean supportsHover(String typeId);

}
