package br.com.finalcraft.evernifecore.api.common.providers.platform;

import br.com.finalcraft.evernifecore.fancytext.FancyText;

public interface IPlatformChatAdapter {

    public String alignCenter(String stringToAlign);

    public String alignCenter(String stringToAlign, String borderFill);

    public String straightLineOf(String string);

    public void broadcast(FancyText fancyText);

}
