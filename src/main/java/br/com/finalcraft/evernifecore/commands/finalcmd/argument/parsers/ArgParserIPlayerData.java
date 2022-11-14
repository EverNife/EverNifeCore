package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.config.playerdata.PDSection;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ArgParserIPlayerData extends ArgParser<IPlayerData> {

    private final boolean online;

    public ArgParserIPlayerData(ArgInfo argInfo) {
        super(argInfo);

        this.online = argInfo.getArgData().context().toLowerCase().contains("online");
    }

    @Override
    public IPlayerData parserArgument(@NotNull CommandSender sender, @NotNull Argumento argumento) throws ArgParseException {
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
    public @NotNull List<String> tabComplete(Context context) {
        Collection<PlayerData> playerDataList = online ? Bukkit.getOnlinePlayers().stream().map(PlayerController::getPlayerData).collect(Collectors.toList()) : PlayerController.getAllPlayerData();

        List<String> matchedPlayers = new ArrayList<>();
        for (PlayerData playerData : playerDataList) {
            String name = playerData.getPlayerName();
            if (StringUtil.startsWithIgnoreCase(name, context.getLastWord())) {
                matchedPlayers.add(name);
            }
        }

        Collections.sort(matchedPlayers, String.CASE_INSENSITIVE_ORDER);
        return matchedPlayers;
    }
}
