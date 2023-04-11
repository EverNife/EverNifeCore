package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public class ArgParserWorld extends ArgParser<World> {

    public ArgParserWorld(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public World parserArgument(@NotNull CommandSender sender, @NotNull Argumento argumento) throws ArgParseException {

        World world = argumento.getWorld();

        if (world == null && this.getArgInfo().isRequired()){
            FCMessageUtil.worldNotFound(sender, argumento.toString());
            throw new ArgParseException();
        }

        return world;
    }

    @Override
    public @NotNull List<String> tabComplete(TabContext tabContext) {

        return Bukkit.getWorlds().stream()
                .map(world -> world.getName())
                .filter(s -> StringUtil.startsWithIgnoreCase(s, tabContext.getLastWord()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

    }
}
