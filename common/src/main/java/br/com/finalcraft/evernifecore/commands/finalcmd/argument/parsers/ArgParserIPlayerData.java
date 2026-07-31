package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.context.ArgContextExtractor;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.context.ArgContextResult;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import jakarta.annotation.Nonnull;

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
    public ParseResult<IPlayerData> parse(@Nonnull ParseCall call) {
        PlayerData playerData = call.getArgumento().getPlayerData();

        if (playerData == null){
            return unrecognized(FCMessageUtil.PLAYER_DATA_NOT_FOUND
                    .addPlaceholder("searched_name", call.getArgumento().toString()));
        }

        if (this.online && !playerData.isPlayerOnline()){
            //Found them, and refusing anyway: a domain rule, fatal even on an optional argument
            return denied(FCMessageUtil.PLAYER_NOT_ONLINE
                    .addPlaceholder("searched_name", playerData.getName()));
        }

        if (PlayerData.class.equals(argInfo.getArgumentType())){
            return ParseResult.<IPlayerData>of(playerData);
        }

        return ParseResult.<IPlayerData>of(playerData
                .getPDSection((Class<? extends PDSection>) this.argInfo.getArgumentType())
                .join());
    }

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {

        Collection<PlayerData> playerDataList = online
            ? EverNifeCore.getPlatform().getOnlinePlayers().stream()
            .map(PlayerController::getLoaded)
            .collect(Collectors.toList())
            : PlayerController.getAllLoaded();

        return playerDataList.stream()
            .map(playerData -> playerData.getName())
            .filter(s -> FCStringUtil.startsWithIgnoreCase(s, tabContext.getLastWord()))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());
    }
}
