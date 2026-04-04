package br.com.finalcraft.evernifecore.placeholder;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.ontime.OntimeManager;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.util.FCTimeUtil;

public class FCRegexReplacers {

    public static RegexReplacer<IPlayerData> PLAYER_DATA = new RegexReplacer<IPlayerData>()
            .addParser("player", IPlayerData::getName)
            .addParser("player_name", IPlayerData::getName)
            .addParser("player_uuid", IPlayerData::getUniqueId)
            .addParser("player_is_online", IPlayerData::isPlayerOnline)
            .addParser("player_ontime", iPlayerData -> FCTimeFrame.of(OntimeManager.getProvider().getOntime(iPlayerData)).getFormattedDiscursive())
            .addParser("player_last_seen", playerData -> FCTimeUtil.getFormatted(playerData.getLastSeen()))
            .addParser("player_last_seen_millis", playerData -> playerData.getLastSeen())
            .addParser("player_first_seen", playerData -> FCTimeUtil.getFormatted(playerData.getFirstSeen()))
            .addParser("player_first_seen_millis", playerData -> playerData.getFirstSeen())
            ;

    public static RegexReplacer<FPlayer> PLAYER = new RegexReplacer<FPlayer>()
            .addParser("player", FPlayer::getName)
            .addParser("player_name", FPlayer::getName)
            .addParser("player_uuid", FPlayer::getUniqueId)
            .addParser("player_isonline", FPlayer::isOnline)
            ;

}
