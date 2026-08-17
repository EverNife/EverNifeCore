package br.com.finalcraft.evernifecore.integration.placeholders;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class PAPIIntegration {

    public static boolean isPresent(){
        return EverNifeCore.getPlatform().isPAPIPresent();
    }

    public static String parse(@Nullable FPlayer player, @Nonnull String text){
        return EverNifeCore.getPlatform().parse(player, text);
    }

    public static <P extends IPlayerData> RegexReplacer<P> createPlaceholderIntegration(@Nonnull ECPluginData plugin, @Nonnull String pluginBaseID, @Nonnull Class<P> playerDataType){
        return EverNifeCore.getPlatform().createPlaceholderIntegration(plugin, pluginBaseID, playerDataType);
    }

}
