package br.com.finalcraft.evernifecore.hytale.commands.finalcmd.argument.contextualparsers;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import jakarta.annotation.Nonnull;

public class ArgParserContextualCommandSender extends ArgParserContextual<CommandSender> {

    public ArgParserContextualCommandSender(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public ParseResult<CommandSender> parse(@Nonnull ContextualParseCall call) {
        return ParseResult.of(call.getSender().getDelegate(CommandSender.class));
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return false;
    }

}
