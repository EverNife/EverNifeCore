package br.com.finalcraft.evernifecore.placeholder;

import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.ontime.OntimeManager;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.util.FCTimeUtil;
import org.bukkit.OfflinePlayer;

public class FCRegexReplacers {

    public static RegexReplacer<IPlayerData> PLAYER_DATA = new RegexReplacer<IPlayerData>()
            .addParser("player", IPlayerData::getPlayerName)
            .addParser("player_name", IPlayerData::getPlayerName)
            .addParser("player_uuid", IPlayerData::getUniqueId)
            .addParser("player_isonline", IPlayerData::isPlayerOnline)
            .addParser("player_lastseen", iPlayerData -> FCTimeUtil.getFormatted(iPlayerData.getLastSeen()))
            .addParser("player_ontime", iPlayerData -> FCTimeFrame.of(OntimeManager.getProvider().getOntime(iPlayerData)).getFormattedDiscursive())
            ;

    public static RegexReplacer<OfflinePlayer> PLAYER = new RegexReplacer<OfflinePlayer>()
            .addParser("player", OfflinePlayer::getName)
            .addParser("player_name", OfflinePlayer::getName)
            .addParser("player_uuid", OfflinePlayer::getUniqueId)
            .addParser("player_isonline", OfflinePlayer::isOnline)
            ;


}
