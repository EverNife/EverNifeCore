package br.com.finalcraft.evernifecore.api.common.providers.platform;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.logger.ILogAdapter;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.UUID;

public interface IPlatform {

    public List<FPlayer> getOnlinePlayers();

    public FPlayer getPlayer(String playerName);

    public FPlayer getPlayer(UUID playerUuid);

    public boolean isPluginLoaded(String pluginName);

    public boolean makeConsoleExecuteCommand(String command);

    public boolean makePlayerExecuteCommand(FCommandSender sender, String command);

    public boolean registerCommand(FinalCMDPluginCommand finalCMDPluginCommand);

    public void unregisterCommand(String commandName, ECPluginData notifyPlugin);

    public void registerECListener(ECPluginData ecPluginData, ECListener listener);

    public void unregisterECListener(ECListener listener);

    public boolean isPAPIPresent();

    public String parse(@Nullable FPlayer player, @Nonnull String text);

    public <P extends IPlayerData> RegexReplacer<P> createPlaceholderIntegration(@Nonnull ECPluginData plugin, @Nonnull String pluginBaseID, @Nonnull Class<P> playerDataType);

    public ILogAdapter createLogAdapterFor(ECPluginData ecPluginData);

    public void sendActionBarMessage(FPlayer player, FancyText fancyText);
}
