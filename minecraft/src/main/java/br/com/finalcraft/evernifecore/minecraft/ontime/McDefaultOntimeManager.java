package br.com.finalcraft.evernifecore.minecraft.ontime;

import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.ontime.IOntimeProvider;
import br.com.finalcraft.evernifecore.ontime.OntimeManager;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;

public class McDefaultOntimeManager {

    public static void initialize(){
        Statistic PLAY_ONE_TICK =
                MCVersion.isHigherEquals(MCVersion.v1_13) ?
                        Statistic.PLAY_ONE_MINUTE :
                        Statistic.valueOf("PLAY_ONE_TICK");

        OntimeManager.setOntimeProvider(new IOntimeProvider() {
            @Override
            public long getOntime(IPlayerData playerData) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerData.getUniqueId());

                if (offlinePlayer.isOnline()){
                    return offlinePlayer.getPlayer().getStatistic(PLAY_ONE_TICK) * 50;
                }

                return 0;
            }
        });
    }

}
