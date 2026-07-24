package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.fancytext.FancyText;

public interface LocaleMessage extends ILocaleMessageBase{

    SendCustom custom();

    FancyText getFancyText(String localeName);

    FancyText getDefaultFancyText();

}
