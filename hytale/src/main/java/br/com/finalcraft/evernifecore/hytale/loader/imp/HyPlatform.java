package br.com.finalcraft.evernifecore.hytale.loader.imp;
import br.com.finalcraft.evernifecore.util.ExecuteOnce;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformChatAdapter;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformVecAdapter;
import br.com.finalcraft.evernifecore.api.eventhandler.ECEventHandler;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;
import br.com.finalcraft.evernifecore.hytale.commands.finalcmd.HytaleArgParsers;
import br.com.finalcraft.evernifecore.hytale.commands.finalcmd.implementation.HyFinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.hytale.integration.placeholders.HyPAPIIntegration;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.logger.ILogAdapter;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.scheduler.FCScheduler;
import br.com.finalcraft.everylibs.reflection.MethodInvoker;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.event.ICancellable;
import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.ShutdownReason;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class HyPlatform implements IPlatform {

    public static Map<ECListener, List<EventRegistration>> MAP_OF_ECLISTENERS = new ConcurrentHashMap<>();

    private HyPlatformChatAdapter CHAT_ADAPTER = new HyPlatformChatAdapter();

    private HyPlatformVecAdapter VEC_ADAPTER = new HyPlatformVecAdapter();

    //runOnMainThread tasks submitted before the start phase (every plugin set up, all worlds loaded)
    //are buffered on a gate here; flushPendingMainThreadTasks() opens them once, then tasks run at once.
    private static final CompletableFuture<Void> READY_GATE = CompletableFuture.completedFuture(null);
    private final Object startGateLock = new Object();
    private boolean serverStarted = false;
    private final List<CompletableFuture<Void>> pendingStartGates = new ArrayList<>();

    @Override
    public String getPlatformProviderId() {
        return "hytale";
    }

    @Override
    public List<FPlayer> getOnlinePlayers() {
        return Universe.get().getPlayers().stream()
                .map(HytaleFPlayer::of)
                .collect(Collectors.toList());
    }

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
        HyFinalCMDPluginCommand iPlatformCMD = new HyFinalCMDPluginCommand(command);

        command.setPlatformCommand(iPlatformCMD);

        return iPlatformCMD.getJavaPlugin().getCommandRegistry()
                .registerCommand(iPlatformCMD)
                .isRegistered();
    }

    @Override
    public void unregisterCommand(String commandName, ECPluginData notifyPlugin) {
        try {
            for (AbstractCommand existingCommand : CommandManager.get().getCommandRegistration().values()) {
                if (existingCommand.getName().equalsIgnoreCase(commandName)){
                    CommandManager.get().getCommandRegistration().remove(commandName);
                    notifyPlugin.getLog().warning("Removing existent command [" + commandName + "] from " + existingCommand.getOwner().getName() + "!");
                    return;
                }

                if (existingCommand.getAliases().contains(commandName)){
                    existingCommand.getAliases().remove(commandName);
                    notifyPlugin.getLog().warning("Removing existent alias (" + commandName + ") from " + existingCommand.getOwner().getName() + "!");
                }
            }
        }catch (Exception e){
            EverNifeCore.getLog().warning("Failed to UNREGISTER command [" +  commandName + "]");
            e.printStackTrace();
        }
    }

    @Override
    public void registerECListener(ECPluginData ecPluginData, ECListener listener) {

        List<MethodInvoker> annotatedMethods = FCReflectionUtil.getMethods().getMethods(listener.getClass(), method -> {
            ECEventHandler annotation = method.getAnnotation(ECEventHandler.class);
            return annotation != null;
        }).collect(Collectors.toList());

        boolean foundAnyError = false;

        List<EventRegistration> registrations = new ArrayList<>();

        for (MethodInvoker methodListener : annotatedMethods) {
            Class<?>[] parameterTypes = methodListener.getMethod().getParameterTypes();

            if (parameterTypes.length == 0) {
                ecPluginData.getLog().severe(String.format("[ECListener] @ECEventHandler(%s#%s) | No parameter found on this listener.. ", listener.getClass().getSimpleName(), methodListener.getMethod().getName()));
                foundAnyError = true;
                continue;
            }

            if (parameterTypes.length > 1) {
                ecPluginData.getLog().severe(String.format("[ECListener] @ECEventHandler(%s#%s) | More than one parameter found on this listener.. ", listener.getClass().getSimpleName(), methodListener.getMethod().getName()));
                foundAnyError = true;
                continue;
            }

            if (!IEvent.class.isAssignableFrom(parameterTypes[0])) {
                ecPluginData.getLog().severe(String.format("[ECListener] @ECEventHandler(%s#%s) | The parameter %s is not assignable to IEvent", listener.getClass().getSimpleName(), methodListener.getMethod().getName(), parameterTypes[0].getSimpleName()));
                foundAnyError = true;
                continue;
            }

            ECEventHandler annotation = methodListener.getMethod().getAnnotation(ECEventHandler.class);

            Class<IEvent> eventClass = (Class<IEvent>) parameterTypes[0];

            Consumer consumer = event -> {
                if (annotation.ignoreCancelled() && event instanceof ICancellable cancellable){
                    if (cancellable.isCancelled()){
                        return;
                    }
                }

                methodListener.invoke(listener, event);
            };

            JavaPlugin javaPlugin = (JavaPlugin) ecPluginData.getPlugin();

            EventRegistration registration = javaPlugin.getEventRegistry().registerGlobal(
                    annotation.priority().getValue(),
                    eventClass,
                    consumer
            );

            registrations.add(registration);
        }

        if (foundAnyError){
            ecPluginData.getLog().severe("Found errors while registering the ECListener: " + listener.getClass().getName());
            for (EventRegistration registration : registrations) {
                registration.unregister();
            }
            throw new RuntimeException("Found errors while registering the ECListener: " + listener.getClass().getName() + " read the previous messages...");
        }

        MAP_OF_ECLISTENERS.put(listener, registrations);
    }

    @Override
    public void unregisterECListener(ECListener listener) {
        MAP_OF_ECLISTENERS.getOrDefault(listener, new ArrayList<>())
                .forEach(EventRegistration::unregister);
        MAP_OF_ECLISTENERS.remove(listener);
    }

    @Override
    public boolean isPAPIPresent() {
        return HyPAPIIntegration.isPresent();
    }

    @Override
    public String parse(@Nullable FPlayer player, @Nonnull String text) {
        return HyPAPIIntegration.parse(player, text);
    }

    @Override
    public <P extends IPlayerData> RegexReplacer<P> createPlaceholderIntegration(@Nonnull ECPluginData plugin, @Nonnull String pluginBaseID, @Nonnull Class<P> playerDataType) {
       return HyPAPIIntegration.createPlaceholderIntegration(plugin, pluginBaseID, playerDataType);
    }

    @Override
    public ILogAdapter createLogAdapterFor(ECPluginData ecPluginData) {

        JavaPlugin javaPlugin = (JavaPlugin) ecPluginData.getPlugin();

        return new ILogAdapter(){
            @Override
            public void info(String string) {
                javaPlugin.getLogger().atInfo().log(string);
            }

            @Override
            public void warning(String string) {
                javaPlugin.getLogger().atWarning().log(string);
            }

            @Override
            public void severe(String string) {
                javaPlugin.getLogger().atSevere().log(string);
            }

            @Override
            public void log(Level level, String string) {
                javaPlugin.getLogger().at(level).log(string);
            }
        };
    }

    @Override
    public void sendActionBarMessage(FPlayer player, FancyText fancyText) {
        // Hytale does not have action-bar messages yet
    }

    @Override
    public boolean serverSupportsActionBar() {
        return false; // Hytale does not have action-bar messages yet
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
        //Hytale has no single main thread (schedulers are per-world). Before the start phase we buffer
        //a gate the flush opens once every plugin has set up and all worlds are loaded; after it, the
        //gate is already open. Either way the task then runs on a background thread.
        CompletableFuture<Void> gate;
        synchronized (startGateLock) {
            if (serverStarted) {
                gate = READY_GATE;
            } else {
                gate = new CompletableFuture<>();
                pendingStartGates.add(gate);
            }
        }
        return gate.thenCompose(ignored -> FCScheduler.supplyAsyncFuture(task));
    }

    /**
     * Releases every task {@link #runOnMainThread(Runnable)} buffered before the start phase - called
     * from {@code ECHytalePlugin.start()}, which the server runs once every plugin has finished
     * {@code setup()} and all worlds are loaded. Idempotent: repeated (or per-plugin) calls find
     * nothing left to release.
     */
    public void flushPendingMainThreadTasks() {
        List<CompletableFuture<Void>> gates;
        synchronized (startGateLock) {
            if (serverStarted) {
                return;
            }
            serverStarted = true;
            gates = new ArrayList<>(pendingStartGates);
            pendingStartGates.clear();
        }
        gates.forEach(gate -> gate.complete(null));
    }

    private final ExecuteOnce registerConfigTypesOnce = ExecuteOnce.of(HyConfigTypes::register);
    @Override
    public void registerConfigTypes() {
        registerConfigTypesOnce.run();
    }

    private final ExecuteOnce registerArgParsersOnce = ExecuteOnce.of(HytaleArgParsers::initialize);
    @Override
    public void registerArgParsers() {
        registerArgParsersOnce.run();
    }

    @Override
    public void shutdown(String reason) {
        EverNifeCore.getLog().severe("Shutting the server down: " + reason);
        //exit code 1 so a wrapper/panel can tell this apart from a clean stop
        HytaleServer.get().shutdownServer(new ShutdownReason(1, "STORAGE_UNAVAILABLE"));
    }

}
