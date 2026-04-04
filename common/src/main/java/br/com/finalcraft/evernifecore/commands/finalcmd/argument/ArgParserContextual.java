package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import jakarta.annotation.Nonnull;

public abstract class ArgParserContextual<T extends Object> {

    protected final ArgContextualInfo argContextualInfo;

    public ArgParserContextual(ArgContextualInfo argContextualInfo) {
        this.argContextualInfo = argContextualInfo;
    }

    public ArgContextualInfo getArgInfo() {
        return argContextualInfo;
    }

    //TODO Renomear isso
    public abstract T parserArgument(@Nonnull ArgParserCommandContext argContext, @Nonnull FCommandSender sender) throws ArgParseException;

    public abstract boolean requiresToBeAPlayer();

    public int getPriority(){
        return 0; //TODO mover isso para manager
    }
}
