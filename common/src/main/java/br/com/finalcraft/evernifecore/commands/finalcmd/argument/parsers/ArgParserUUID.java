package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.context.ArgContextExtractor;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.context.ArgContextResult;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ArgParserUUID extends ArgParser<UUID> {

    //Context Field Extractors
    protected static final ArgContextExtractor<Boolean> CTX_ONLINE = ArgContextExtractor.of("online");

    protected final boolean online;

    public ArgParserUUID(ArgInfo argInfo) {
        super(argInfo);

        ArgContextResult contextResult = ArgContextResult.parseFrom(argInfo.getArgData().getContext());

        this.online = contextResult.get(CTX_ONLINE).orElse(false);
    }

    @Override
    public ParseResult<UUID> parse(@Nonnull ParseCall call) {
        UUID uuid = call.getArgumento().getUUID();

        if (uuid == null){
            return unrecognized(FCMessageUtil.NEEDS_TO_BE_UUID.addPlaceholder("argumento", call.getArgumento().toString()));
        }

        if (PlayerController.getLoaded(uuid) == null){
            //A well-formed UUID nobody here has ever been: converted fine, refused anyway
            return denied(FCMessageUtil.PLAYER_DATA_NOT_FOUND.addPlaceholder("searched_name", call.getArgumento().toString()));
        }

        return ParseResult.of(uuid);
    }

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {
        Collection<PlayerData> playerDataList = online
                ? EverNifeCore.getPlatform().getOnlinePlayers().stream()
                .map(PlayerController::getLoaded)
                .collect(Collectors.toList())
                : PlayerController.getAllLoaded();


        return playerDataList.stream()
                .map(playerData -> playerData.getUniqueId().toString())
                .filter(s -> FCStringUtil.startsWithIgnoreCase(s, tabContext.getLastWord()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

    }
}
