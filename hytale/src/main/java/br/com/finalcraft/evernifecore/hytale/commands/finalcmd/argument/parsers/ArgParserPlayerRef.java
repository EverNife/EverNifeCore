package br.com.finalcraft.evernifecore.hytale.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;
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
    public ParseResult<PlayerRef> parse(@Nonnull ParseCall call) {
        FPlayer player = call.getArgumento().getPlayer();

        return player == null
                ? unrecognized(FCMessageUtil.PLAYER_NOT_ONLINE.addPlaceholder("searched_name", call.getArgumento().toString()))
                : ParseResult.of(((HytaleFPlayer) player).getPlayerRef());
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
