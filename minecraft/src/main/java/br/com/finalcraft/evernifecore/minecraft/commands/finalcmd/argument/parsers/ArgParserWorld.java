package br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import jakarta.annotation.Nonnull;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.util.StringUtil;

import java.util.List;
import java.util.stream.Collectors;

public class ArgParserWorld extends ArgParser<World> {

    protected ArgParserWorld(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public ParseResult<World> parse(@Nonnull ParseCall call) {
        World world = call.getArgumento().adapter().getWorld();

        return world == null
                ? unrecognized(FCMessageUtil.WORLD_NOT_FOUND.addPlaceholder("searched_name", call.getArgumento().toString()))
                : ParseResult.of(world);
    }

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {

        return Bukkit.getWorlds().stream()
                .map(world -> world.getName())
                .filter(s -> StringUtil.startsWithIgnoreCase(s, tabContext.getLastWord()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

    }
}
