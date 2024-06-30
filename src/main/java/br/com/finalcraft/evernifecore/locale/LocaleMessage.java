package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.fancytext.FancyText;

public interface LocaleMessage extends ILocaleMessageBase{

    public SendCustom custom();

    public FancyText getFancyText(String localeName);

    public FancyText getDefaultFancyText();

}
