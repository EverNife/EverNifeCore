package br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.argument.contextualparsers;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import jakarta.annotation.Nonnull;
import org.bukkit.command.CommandSender;

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
