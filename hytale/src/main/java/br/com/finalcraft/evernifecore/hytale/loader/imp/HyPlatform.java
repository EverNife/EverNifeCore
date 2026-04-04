package br.com.finalcraft.evernifecore.hytale.loader.imp;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.api.hytale.HytaleFPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.IPlatformCMD;
import br.com.finalcraft.evernifecore.hytale.commands.finalcmd.implementation.HyFinalCMDPluginCommand;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import java.util.UUID;

public class HyPlatform implements IPlatform {

    @Override
    public FPlayer getPlayer(String playerName) {

        PlayerRef player = Universe.get().getPlayer(playerName, NameMatching.EXACT_IGNORE_CASE);

        if (player == null) {
            return null;
        }

        return HytaleFPlayer.of(player);
    }

    @Override
    public FPlayer getPlayer(UUID playerUuid) {
        PlayerRef player = Universe.get().getPlayer(playerUuid);

        if (player == null) {
            return null;
        }

        return HytaleFPlayer.of(player);
    }

    @Override
    public boolean isPluginLoaded(String pluginName) {
        return false;
    }

    @Override
    public boolean makeConsoleExecuteCommand(String command) {
        return false;
    }

    @Override
    public boolean makePlayerExecuteCommand(FCommandSender sender, String command) {
        return false;
    }

    @Override
    public boolean registerCommand(FinalCMDPluginCommand command) {
        IPlatformCMD iPlatformCMD = new HyFinalCMDPluginCommand(command);

        command.setPlatformCommand(iPlatformCMD);

        return iPlatformCMD.registerCommand();
    }

}
