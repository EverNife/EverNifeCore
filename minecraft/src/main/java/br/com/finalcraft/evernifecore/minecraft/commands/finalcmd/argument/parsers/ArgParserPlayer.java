package br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFPlayer;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import jakarta.annotation.Nonnull;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class ArgParserPlayer extends ArgParser<Player> {

    public ArgParserPlayer(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public ParseResult<Player> parse(@Nonnull ParseCall call) {
        FPlayer player = call.getArgumento().getPlayer();

        if (player == null){
            return unrecognized(FCMessageUtil.PLAYER_NOT_ONLINE.addPlaceholder("searched_name", call.getArgumento().toString()));
        }

        if (!(player instanceof MinecraftFPlayer)){
            //A player resolved by the platform that this platform cannot hand over is a wiring bug,
            //not something whoever typed the name can do anything about
            return ParseResult.internalError(new IllegalStateException(
                    "The resolved player is not a MinecraftFPlayer: " + player.getClass().getName()));
        }

        return ParseResult.of(((MinecraftFPlayer) player).getPlayer());
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
