package br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.argument.contextualparsers;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFCommandSender;
import jakarta.annotation.Nonnull;

public class ArgParserContextualMinecraftFCommandSender extends ArgParserContextual<MinecraftFCommandSender> {

    public ArgParserContextualMinecraftFCommandSender(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public ParseResult<MinecraftFCommandSender> parse(@Nonnull ContextualParseCall call) {
        return ParseResult.of((MinecraftFCommandSender) call.getSender());
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return false;
    }

}
