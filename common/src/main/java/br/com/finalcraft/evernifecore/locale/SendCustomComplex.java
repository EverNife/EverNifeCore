package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.fancytext.FancyFormatter;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SendCustomComplex extends SendCustom {

    private List<SendCustom> concatList = new ArrayList<>();

    protected SendCustomComplex(LocaleMessage localeMessage, SendCustom previous) {
        super(localeMessage);
        if (previous instanceof SendCustomComplex){
            concatList = ((SendCustomComplex)previous).concatList;
        }else {
            concatList.add(previous);
        }
        concatList.add(this);
    }

    public SendCustomComplex(SendCustom sendCustom, SendCustom previous) {
        super(sendCustom.localeMessage);
        this.declaredPlaceholders = sendCustom.declaredPlaceholders;
        this.hover = sendCustom.hover;
        this.action = sendCustom.action;
        this.suggest = sendCustom.suggest;
        this.link = sendCustom.link;
        this.compoundReplacer = sendCustom.compoundReplacer;

        if (previous instanceof SendCustomComplex){
            concatList = ((SendCustomComplex)previous).concatList;
        }else {
            concatList.add(previous);
        }
        concatList.add(this);
    }

    // The whole chain, not just the piece this instance happens to carry: send() delivers exactly
    // this, because it is the inherited send() and there is only one place the text is built.
    @Override
    public FancyText getFancyText(@Nullable FCommandSender sender){
        FancyFormatter formatter = new FancyFormatter();
        for (SendCustom sendCustom : concatList) {
            formatter.append(sendCustom.renderFor(sender));
        }
        return formatter;
    }
}
