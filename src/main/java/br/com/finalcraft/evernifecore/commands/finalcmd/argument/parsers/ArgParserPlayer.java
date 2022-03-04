package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArgParserPlayer extends ArgParser<Player> {

    public ArgParserPlayer(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public Player parserArgument(@NotNull CommandSender sender, @NotNull Argumento argumento) throws ArgParseException {
        Player player = argumento.getPlayer();

        if (argInfo.isRequired() && player == null){
            FCMessageUtil.playerNotOnline(sender, argumento.toString());
            throw new ArgParseException();
        }

        return player;
    }


    @Override
    public @NotNull List<String> tabComplete(Context context) {
        Player sender = context.getPlayer();
        ArrayList<String> matchedPlayers = new ArrayList<String>();
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            String name = player.getName();
            if ((sender == null || sender.canSee(player)) && StringUtil.startsWithIgnoreCase(name, context.getLastWord())) {
                matchedPlayers.add(name);
            }
        }

        Collections.sort(matchedPlayers, String.CASE_INSENSITIVE_ORDER);
        return matchedPlayers;
    }

}
