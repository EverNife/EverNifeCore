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
import java.util.stream.Collectors;

/**
 * A token that names a PLAYER, handed to the method as one of that player's stored rows.
 *
 * <p>This base answers the naming half - resolve the name, refuse an offline target when the argument
 * declares {@code [online]}, tab-complete over the player base, and read the sender's own name when no
 * token was ceded. The projection half is {@link #project(PlayerData)}: WHICH row of that player the
 * declared parameter type asked for. Both entry points go through it, so {@code /cmd} and
 * {@code /cmd <themselves>} hand the method the very same object.</p>
 *
 * @param <T> the family of rows this parser hands back
 */
public abstract class ArgParserPlayerLookup<T> extends ArgParser<T> {

    //Context Field Extractors
    protected static final ArgContextExtractor<Boolean> CTX_ONLINE = ArgContextExtractor.of("online");

    protected final boolean online;

    public ArgParserPlayerLookup(ArgInfo argInfo) {
        super(argInfo);

        ArgContextResult contextResult = ArgContextResult.parseFrom(argInfo.getArgData().getContext());

        this.online = contextResult.get(CTX_ONLINE).orElse(false);
    }

    /**
     * The row the declared parameter type asked for, read off a player that already exists and already
     * passed the {@code [online]} rule. Resolving it may hit the backend, so it is the one part of the
     * lookup that can block.
     */
    protected abstract @Nonnull ParseResult<T> project(@Nonnull PlayerData playerData);

    @Override
    public @Nonnull ParseResult<T> parse(@Nonnull ParseCall call) {
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

        return project(playerData);
    }

    /**
     * The sender's own data, for an argument nobody typed. Same lookup the contextual sibling does, and
     * the same projection as {@link #parse}.
     */
    @Override
    public @Nonnull ParseResult<T> fromSender(@Nonnull ParseCall call) {
        if (!call.getSender().isPlayer()){
            //The console is nobody, so there is no "own data" to read - and saying so beats an empty
            //argument the method then has to guess about
            return unrecognized(FCMessageUtil.ONLY_A_PLAYER_CAN_DO_THAT);
        }

        PlayerData playerData = PlayerController.getLoaded(call.getSender().getUniqueId());

        if (playerData == null){
            return unrecognized(FCMessageUtil.PLAYER_DATA_NOT_FOUND
                    .addPlaceholder("searched_name", call.getSender().getName()));
        }

        return project(playerData);
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
