package br.com.finalcraft.evernifecore.ontime;

import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.version.MCVersion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;

public class OntimeManager {

    private static final Statistic PLAY_ONE_TICK =
            MCVersion.isCurrentEqualOrHigher(MCVersion.v1_13_R1) ?
                    Statistic.PLAY_ONE_MINUTE :
                    Statistic.valueOf("PLAY_ONE_TICK");

    private static IOntimeProvider ONTIME_PROVIDER = new IOntimeProvider() {
        @Override
        public long getOntime(IPlayerData playerData) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerData.getUniqueId());

            if (offlinePlayer.isOnline()){
                return offlinePlayer.getPlayer().getStatistic(PLAY_ONE_TICK) * 50;
            }

            return 0;
        }
    };

    public static void setOntimeProvider(IOntimeProvider provider){
        ONTIME_PROVIDER = provider;
    }

    public static IOntimeProvider getProvider() {
        return ONTIME_PROVIDER;
    }

}
