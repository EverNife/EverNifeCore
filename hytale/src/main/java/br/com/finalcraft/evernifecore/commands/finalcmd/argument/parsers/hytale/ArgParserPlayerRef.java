package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.hytale;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.hytale.HytaleFPlayer;
import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.stream.Collectors;

public class ArgParserPlayerRef extends ArgParser<PlayerRef> {

    public ArgParserPlayerRef(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public PlayerRef parserArgument(@Nonnull FCommandSender sender, @Nonnull Argumento argumento) throws ArgParseException {
        FPlayer player = argumento.getPlayer();

        if (argInfo.isRequired() && player == null){
            FCMessageUtil.playerNotOnline(sender, argumento.toString());
            throw new ArgParseException();
        }

        return ((HytaleFPlayer) player).getPlayerRef();
    }


    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {

        return Universe.get().getPlayers().stream()
                .map(player -> player.getUsername())
                .filter(s -> FCStringUtil.startsWithIgnoreCase(s, tabContext.getLastWord()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

    }

}
