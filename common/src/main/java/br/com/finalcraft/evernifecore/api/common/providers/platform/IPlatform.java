package br.com.finalcraft.evernifecore.api.common.providers.platform;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public interface IPlatform {

    public default List<FPlayer> getOnlinePlayers() {
        if (true){
            throw  new UnsupportedOperationException("Not supported yet.");
        }
        return new ArrayList<>();
    }

    public FPlayer getPlayer(String playerName);

    public FPlayer getPlayer(UUID playerUuid);

    public boolean isPluginLoaded(String pluginName);

    public boolean makeConsoleExecuteCommand(String command);

    public boolean makePlayerExecuteCommand(FCommandSender sender, String command);

    public boolean registerCommand(FinalCMDPluginCommand finalCMDPluginCommand);

}
