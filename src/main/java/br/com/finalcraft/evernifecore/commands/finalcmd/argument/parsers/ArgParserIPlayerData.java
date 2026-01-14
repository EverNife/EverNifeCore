package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.context.ArgContextExtractor;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.context.ArgContextResult;
import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.config.playerdata.PDSection;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ArgParserIPlayerData extends ArgParser<IPlayerData> {

    //Context Field Extractors
    protected static final ArgContextExtractor<Boolean> CTX_ONLINE = ArgContextExtractor.of("online");

    protected final boolean online;

    public ArgParserIPlayerData(ArgInfo argInfo) {
        super(argInfo);

        ArgContextResult contextResult = ArgContextResult.parseFrom(argInfo.getArgData().getContext());

        this.online = contextResult.get(CTX_ONLINE).orElse(false);
    }

    @Override
    public IPlayerData parserArgument(@Nonnull CommandSender sender, @Nonnull Argumento argumento) throws ArgParseException {
        PlayerData playerData = argumento.getPlayerData();

        if (playerData == null){
            if (!argInfo.isRequired()){
                return null;
            }
            FCMessageUtil.playerDataNotFound(sender, argumento.toString());
            throw new ArgParseException();
        }

        if (this.online && !playerData.isPlayerOnline()){
            FCMessageUtil.playerNotOnline(sender, playerData.getPlayerName());
            throw new ArgParseException();
        }

        if (PlayerData.class.equals(argInfo.getArgumentType())){
            return playerData;
        }

        return playerData.getPDSection((Class<? extends PDSection>) this.argInfo.getArgumentType());
    }

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {

        Collection<PlayerData> playerDataList = online
                ? Bukkit.getOnlinePlayers().stream().map(PlayerController::getPlayerData).collect(Collectors.toList())
                : PlayerController.getAllPlayerData();

        return playerDataList.stream()
                .map(playerData -> playerData.getPlayerName())
                .filter(s -> StringUtil.startsWithIgnoreCase(s, tabContext.getLastWord()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

    }
}
