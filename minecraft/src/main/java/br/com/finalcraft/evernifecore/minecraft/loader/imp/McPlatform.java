package br.com.finalcraft.evernifecore.minecraft.loader.imp;
import br.com.finalcraft.evernifecore.util.ExecuteOnce;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformChatAdapter;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformVecAdapter;
import br.com.finalcraft.evernifecore.api.common.providers.platform.PlatformId;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.logger.ECLogLevel;
import br.com.finalcraft.evernifecore.logger.ILogAdapter;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFPlayer;
import br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.MinecraftArgParsers;
import br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.implementation.McFinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.minecraft.eventbus.McECEventHandlers;
import br.com.finalcraft.evernifecore.minecraft.integration.placeholders.McPAPIIntegration;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.minecraft.util.FCMinecraftAdventureUtil;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.scheduler.FCScheduler;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class McPlatform implements IPlatform {

    private final McPlatformChatAdapter CHAT_ADAPTER = new McPlatformChatAdapter();
    private final McPlatformVecAdapter VEC_ADAPTER = new McPlatformVecAdapter();

    @Override
    public String getPlatformProviderId() {
        return PlatformId.MINECRAFT;
    }

    @Override
    public List<FPlayer> getOnlinePlayers() {
        Collection<? extends Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        return onlinePlayers.stream()
                .map(player -> MinecraftFPlayer.of(player))
                .collect(Collectors.toList());
    }

    @Override
    public FPlayer getPlayer(String playerName) {
        Player player = Bukkit.getPlayer(playerName);

        if (player == null) {
            return null;
        }

        return MinecraftFPlayer.of(player);
    }

    @Override
    public FPlayer getPlayer(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);

        if (player == null) {
            return null;
        }

        return MinecraftFPlayer.of(player);
    }

    @Override
    public boolean isPluginLoaded(String pluginName) {
        return Bukkit.getPluginManager().isPluginEnabled(pluginName);
    }

    @Override
    public boolean makeConsoleExecuteCommand(String command) {
        return FCBukkitUtil.makeConsoleExecuteCommand(command);
    }

    @Override
    public boolean makePlayerExecuteCommand(FCommandSender sender, String command) {
        return FCBukkitUtil.makePlayerExecuteCommand(sender.getDelegate(CommandSender.class), command);
    }

    @Override
    public boolean registerCommand(FinalCMDPluginCommand command) {
        McFinalCMDPluginCommand iPlatformCMD = new McFinalCMDPluginCommand(command);

        command.setPlatformCommand(iPlatformCMD);
        iPlatformCMD.setPermission(command.getFinalCMD().getPermission());

        return getCommandMap().register(command.getOwningPlugin().getMetaInfo().getName(), iPlatformCMD);
    }

    @Override
    public void unregisterCommand(String commandName, ECPluginData ecPluginData) {
        Plugin notifyPlugin = (Plugin) ecPluginData.getPlugin();
        try {
            Map<String, Command> mapOfCommands = getCommandMapKnownCommands();
            Command existingCommand = mapOfCommands.get(commandName);
            if (existingCommand == null){
                return; //Command is not registered
            }

            if (MCVersion.isHigherEquals(MCVersion.v1_19)) {
                CommandMap commandMap1 = getCommandMap();
                existingCommand.unregister(commandMap1);
            }

            mapOfCommands.remove(commandName);

            Set<String> extrasMinecraftAliases = new HashSet<>();
            for (Map.Entry<String, Command> entry : mapOfCommands.entrySet()) {
                if (entry.getValue().equals(existingCommand)){
                    if (entry.getKey().startsWith("minecraft:")){
                        extrasMinecraftAliases.add(entry.getKey());
                    }
                }
            }

            for (String extrasMinecraftAlias : extrasMinecraftAliases) {
                //Needed to remove forge/minecraft commands like 'minecraft:some_command'
                mapOfCommands.remove(extrasMinecraftAlias);
            }

            String originalPlugin = "BUKKIT/MINECRAFT";
            if (existingCommand instanceof PluginIdentifiableCommand){
                Plugin plugin = ((PluginIdentifiableCommand) existingCommand).getPlugin();
                if (plugin != null){
                    originalPlugin = "Plugin: " + plugin.getName();
                }
            }

            if (commandName.equals(existingCommand.getName())){
                notifyPlugin.getLogger().warning("Removing existent command [" + existingCommand.getName() + "] from " + originalPlugin + "!");
            }else {
                notifyPlugin.getLogger().warning("Removing existent alias (" + commandName + ") for [" + existingCommand.getName() + "] from " + originalPlugin + "!");
            }

            for (String extrasMinecraftAlias : extrasMinecraftAliases) {
                notifyPlugin.getLogger().warning("Removing extra related Minecraft command: " + extrasMinecraftAlias);
            }

            existingCommand.unregister(getCommandMap());

        }catch (Exception e){
            EverNifeCore.getLog().warning("Failed to UNREGISTER command [" +  commandName + "]");
            e.printStackTrace();
        }
    }

    @Override
    public void registerECListener(ECPluginData ecPluginData, ECListener listener) {
        Plugin pluginInstance = (Plugin) ecPluginData.getPlugin();
        //Bukkit's own scan, which is what an @EventHandler method has always been registered by
        Bukkit.getServer().getPluginManager().registerEvents(listener, pluginInstance);
        //and then the @ECEventHandler methods naming a Bukkit event, which that scan cannot see
        McECEventHandlers.register(ecPluginData, listener);
    }

    @Override
    public void unregisterECListener(ECListener listener) {
        HandlerList.unregisterAll(listener);
    }

    @Override
    public boolean isPAPIPresent() {
        return McPAPIIntegration.isPresent();
    }

    @Override
    public String parse(@Nullable FPlayer player, @Nonnull String text) {
        return McPAPIIntegration.parse(player, text);
    }

    @Override
    public <P extends IPlayerData> RegexReplacer<P> createPlaceholderIntegration(@Nonnull ECPluginData plugin, @Nonnull String pluginBaseID, @Nonnull Class<P> playerDataType) {
       return McPAPIIntegration.createPlaceholderIntegration(plugin, pluginBaseID, playerDataType);
    }

    @Override
    public ILogAdapter createLogAdapterFor(ECPluginData ecPluginData) {

        JavaPlugin javaPlugin = (JavaPlugin) ecPluginData.getPlugin();

        return new ILogAdapter(){
            @Override
            public void log(ECLogLevel level, String message) {
                javaPlugin.getLogger().log(julLevelOf(level), message);
            }
        };
    }

    /**
     * DEBUG lands on INFO: the Bukkit console filters everything below it, so mapping debug to FINE
     * would silently swallow the lines the operator turned DebugMode on to see.
     */
    private static Level julLevelOf(ECLogLevel level) {
        switch (level) {
            case SEVERE:  return Level.SEVERE;
            case WARNING: return Level.WARNING;
            default:      return Level.INFO;
        }
    }

    @Override
    public void sendActionBarMessage(FPlayer player, FancyText fancyText) {
        FCMinecraftAdventureUtil.sendActionBar(player.getDelegate(Player.class), fancyText.toComponent());
    }

    private static final boolean THIS_SERVER_SUPPORTS_ACTIONBAR; static {
        THIS_SERVER_SUPPORTS_ACTIONBAR = MCVersion.isHigher(MCVersion.v1_7_10) || FCBukkitUtil.isModLoaded("necrotempus");
    }

    @Override
    public boolean serverSupportsActionBar() {
        return THIS_SERVER_SUPPORTS_ACTIONBAR;
    }

    private static CommandMap commandMap = null;
    public static CommandMap getCommandMap(){
        if (commandMap == null){
            try {
                Field field = SimplePluginManager.class.getDeclaredField("commandMap");
                field.setAccessible(true);
                commandMap = (CommandMap)(field.get(Bukkit.getServer().getPluginManager()));
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        return commandMap;
    }

    public static Map<String, Command> getCommandMapKnownCommands(){
        CommandMap commandMap = getCommandMap();
        try {
            Field field = SimpleCommandMap.class.getDeclaredField("knownCommands");
            field.setAccessible(true);
            Map<String, Command> knowCommands = (Map<String, Command>) field.get(commandMap);
            return knowCommands;
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public IPlatformVecAdapter getVecAdapter() {
        return VEC_ADAPTER;
    }

    @Override
    public IPlatformChatAdapter getChatAdapter() {
        return CHAT_ADAPTER;
    }

    @Override
    public CompletableFuture<Void> runOnMainThread(Runnable task) {
        return runOnMainThread(() -> {
            task.run();
            return null;
        });
    }

    @Override
    public <T> CompletableFuture<T> runOnMainThread(Supplier<T> task) {
        if (!FCBukkitUtil.isMainThread()) {
            return runOnMainThreadNextTick(task);
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            future.complete(task.get());
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            future.completeExceptionally(throwable);
        }
        return future;
    }

    @Override
    public CompletableFuture<Void> runOnMainThreadNextTick(Runnable task) {
        return runOnMainThreadNextTick(() -> {
            task.run();
            return null;
        });
    }

    @Override
    public <T> CompletableFuture<T> runOnMainThreadNextTick(Supplier<T> task) {
        //Always the next tick, even when already on the main thread: a task scheduled during enable
        //fires on the first tick (after every plugin has enabled). It runs ON that thread and holds
        //the tick until it finishes - the first-boot legacy import relies on that to freeze the
        //server while it migrates.
        CompletableFuture<T> future = new CompletableFuture<>();
        FCScheduler.getMinecraftScheduler().runSync(() -> {
            try {
                future.complete(task.get());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    private final ExecuteOnce registerConfigTypesOnce = ExecuteOnce.of(McConfigTypes::register);
    @Override
    public void registerConfigTypes() {
        registerConfigTypesOnce.run();
    }

    private final ExecuteOnce registerArgParsersOnce = ExecuteOnce.of(MinecraftArgParsers::initialize);
    @Override
    public void registerArgParsers() {
        registerArgParsersOnce.run();
    }

    @Override
    public void shutdown(String reason) {
        EverNifeCore.getLog().severe("Shutting the server down: " + reason);
        //direct, no scheduler hop: we are on the main thread during enable, and a plugin that is about
        //to be disabled has its scheduled tasks cancelled - a deferred task would never fire
        Bukkit.getServer().shutdown();
    }

}
