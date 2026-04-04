package br.com.finalcraft.evernifecore.integration.placeholders;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.NotImplementedException;

public class PAPIIntegration {

    public static boolean isPresent(){
        return false;
    }

    public static String parse(@Nullable FPlayer player, @Nonnull String text){
        return EverNifeCore.getPlatform().parse(player, text);
    }

    public static <P extends IPlayerData> RegexReplacer<P> createPlaceholderIntegration(@Nonnull ECPluginData plugin, @Nonnull String pluginBaseID, @Nonnull Class<P> playerDataType){
        return EverNifeCore.getPlatform().createPlaceholderIntegration(plugin, pluginBaseID, playerDataType);
    }

}
