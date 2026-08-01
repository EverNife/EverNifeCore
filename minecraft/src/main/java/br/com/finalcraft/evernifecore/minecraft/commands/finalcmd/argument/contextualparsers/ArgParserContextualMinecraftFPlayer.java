package br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.argument.contextualparsers;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFPlayer;
import jakarta.annotation.Nonnull;

public class ArgParserContextualMinecraftFPlayer extends ArgParserContextual<MinecraftFPlayer> {

    public ArgParserContextualMinecraftFPlayer(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public ParseResult<MinecraftFPlayer> parse(@Nonnull ContextualParseCall call) {
        return ParseResult.of((MinecraftFPlayer) call.getSender());
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return true;
    }

}
