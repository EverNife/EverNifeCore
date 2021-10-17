package br.com.finalcraft.evernifecore.ontime;

import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;

public class OntimeManager {

    private static IOntimeProvider ontimeProvider = new IOntimeProvider() {
        @Override
        public long getOntime(PlayerData playerData) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerData.getUniqueId());

            if (offlinePlayer.isOnline()){
                return offlinePlayer.getPlayer().getStatistic(Statistic.PLAY_ONE_TICK) * 50;
            }

            return 0;
        }
    };

    public static void setOntimeProvider(IOntimeProvider provider){
        ontimeProvider = provider;
    }

    public static IOntimeProvider getProvider() {
        return ontimeProvider;
    }

}
