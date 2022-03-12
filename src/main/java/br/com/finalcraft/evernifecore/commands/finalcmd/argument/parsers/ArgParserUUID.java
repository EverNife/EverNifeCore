package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class ArgParserUUID extends ArgParser<UUID> {

    private final boolean online;

    public ArgParserUUID(ArgInfo argInfo) {
        super(argInfo);

        this.online = argInfo.getArgData().context().toLowerCase().contains("online");
    }

    @Override
    public UUID parserArgument(@NotNull CommandSender sender, @NotNull Argumento argumento) throws ArgParseException {
        UUID uuid = argumento.getUUID();

        if (uuid == null){
            if (this.getArgInfo().isRequired()){
                FCMessageUtil.needsToBeUUID(sender, argumento.toString());
                throw new ArgParseException();
            }
            return null;
        }

        PlayerData playerData = PlayerController.getPlayerData(uuid);

        if (playerData == null){
            FCMessageUtil.playerDataNotFound(sender, argumento.toString());
            throw new ArgParseException();
        }

        return uuid;
    }

    @Override
    public @NotNull List<String> tabComplete(Context context) {
        Collection<PlayerData> playerDataList = online ? Bukkit.getOnlinePlayers().stream().map(PlayerController::getPlayerData).collect(Collectors.toList()) : PlayerController.getAllPlayerData();

        List<String> matchedPlayers = new ArrayList<>();
        for (PlayerData playerData : playerDataList) {
            String uuid = playerData.getUniqueId().toString();
            if (StringUtil.startsWithIgnoreCase(uuid, context.getLastWord())) {
                matchedPlayers.add(uuid);
            }
        }

        Collections.sort(matchedPlayers, String.CASE_INSENSITIVE_ORDER);
        return matchedPlayers;
    }
}
