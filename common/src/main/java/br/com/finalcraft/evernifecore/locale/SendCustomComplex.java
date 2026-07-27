package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.fancytext.FancyFormatter;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A chain of appended pieces. The chain is flat - appending to a chain extends it instead of
 * nesting - and this instance is always its last piece, so the decoration setters keep acting on
 * what was just appended.
 */
public class SendCustomComplex extends SendCustom {

    private List<ChainPiece> chain = new ArrayList<>();

    protected SendCustomComplex(ChainPiece source, SendCustom previous) {
        super(source);
        if (previous instanceof SendCustomComplex){
            chain = ((SendCustomComplex) previous).chain;
        }else {
            chain.add(previous::renderFor);
        }
        chain.add(this::renderFor);
    }

    // The whole chain, not just the piece this instance happens to carry: send() delivers exactly
    // this, because it is the inherited send() and there is only one place the text is built.
    @Override
    public FancyText getFancyText(@Nullable FCommandSender sender){
        FancyFormatter formatter = new FancyFormatter();
        for (ChainPiece piece : chain) {
            formatter.append(piece.renderFor(sender));
        }
        return formatter;
    }
}
