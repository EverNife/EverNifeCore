package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.stream.Collectors;

public class ArgParserWorld extends ArgParser<World> {

    protected ArgParserWorld(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public World parserArgument(@Nonnull FCommandSender sender, @Nonnull Argumento argumento) throws ArgParseException {

        World world = argumento.getWorld();

        if (world == null && this.getArgInfo().isRequired()){
            FCMessageUtil.worldNotFound(sender, argumento.toString());
            throw new ArgParseException();
        }

        return world;
    }

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {

        return Universe.get().getWorlds().keySet().stream()
                .filter(s -> FCStringUtil.startsWithIgnoreCase(s, tabContext.getLastWord()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

    }
}
