package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.stream.Collectors;

public class ArgParserFPlayer extends ArgParser<FPlayer> {

    public ArgParserFPlayer(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public ParseResult<FPlayer> parse(@Nonnull ParseCall call) {
        FPlayer player = call.getArgumento().getPlayer();

        return player == null
                ? unrecognized(FCMessageUtil.PLAYER_NOT_ONLINE.addPlaceholder("searched_name", call.getArgumento().toString()))
                : ParseResult.of(player);
    }

    /** Whoever typed the command, for an argument they left out. */
    @Override
    public @Nonnull ParseResult<FPlayer> fromSender(@Nonnull ParseCall call) {
        if (!call.getSender().isPlayer()){
            //The console is nobody, so there is no player to infer from it
            return unrecognized(FCMessageUtil.ONLY_A_PLAYER_CAN_DO_THAT);
        }

        return ParseResult.of(call.getSender().asFPlayer());
    }

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {

        return EverNifeCore.getPlatform().getOnlinePlayers().stream()
                .map(player -> player.getName())
                .filter(s -> FCStringUtil.startsWithIgnoreCase(s, tabContext.getLastWord()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

    }

}
