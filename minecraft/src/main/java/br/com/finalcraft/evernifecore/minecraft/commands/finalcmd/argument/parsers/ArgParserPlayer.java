package br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserCommandContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
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
    public Player parserArgument(@Nonnull ArgParserCommandContext argContext, @Nonnull FCommandSender sender, @Nonnull Argumento argumento) throws ArgParseException {
        FPlayer player = argumento.getPlayer();

        if (argInfo.isRequired() && player == null){
            FCMessageUtil.playerNotOnline(sender, argumento.toString());
            throw new ArgParseException();
        }

        return ((MinecraftFPlayer) player).getPlayer();
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
