package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import jakarta.annotation.Nonnull;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.List;
import java.util.stream.Collectors;

public class ArgParserPlayer extends ArgParser<Player> {

    public ArgParserPlayer(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public Player parserArgument(@Nonnull CommandSender sender, @Nonnull Argumento argumento) throws ArgParseException {
        Player player = argumento.getPlayer();

        if (argInfo.isRequired() && player == null){
            FCMessageUtil.playerNotOnline(sender, argumento.toString());
            throw new ArgParseException();
        }

        return player;
    }


    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {

        Player sender = tabContext.getPlayer();

        return Bukkit.getServer().getOnlinePlayers().stream()
                .filter(player -> sender == null || sender.canSee(player))
                .map(player -> player.getName())
                .filter(s -> StringUtil.startsWithIgnoreCase(s, tabContext.getLastWord()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

    }

}
