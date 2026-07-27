package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.FCDefaultExecutor;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Headless end-to-end harness for the FinalCMD framework: registration -&gt; dispatch -&gt; parse -&gt;
 * invoke -&gt; message, without a real Bukkit/Hytale server.
 * <p>
 * Every instance owns its own command-capturing platform and a uniquely-named
 * {@link ECPluginData} (backed by the caller-supplied {@code @TempDir}), so tests never leak
 * registered commands or locale state into one another. Create one per test (or per test class, if
 * the tests within it are fine sharing the same fake plugin) and {@link #close()} it in
 * {@code @AfterEach}/{@code @AfterAll} - closing puts the previous platform and plugin extractor
 * back, so the isolation this javadoc promises also holds for the global providers.
 */
public class FinalCmdTestHarness implements AutoCloseable {

    private static final AtomicInteger UNIQUE_SUFFIX = new AtomicInteger();

    public final String pluginName;
    public final TestPlatform platform;
    public final ECPluginData ecPluginData;

    private final ECoreTestWorld world;

    public FinalCmdTestHarness(String namePrefix, Path dataFolder) {
        this.pluginName = namePrefix + "_" + UNIQUE_SUFFIX.incrementAndGet();

        //a platform has to be registered before FinalCMDManager's static block ever runs, no matter
        //which test in the JVM touches it first
        this.world = Platforms.commandCapture().install()
                .withPluginExtractor(Plugins.fake(pluginName, dataFolder.toFile()));
        this.platform = world.platform();

        this.ecPluginData = ECPluginManager.getOrCreateECorePluginData(new Object());

        //FinalCMDManager's static block (first FinalCMD ever touched in this JVM) logs through
        //EverNifeCore's OWN ecPluginData (EverNifeCore.getLog()), which is otherwise only set by the
        //real bootstrap (onLoaderInstantiate); without it, ArgParserManager.addGlobalParser NPEs.
        EverNifeCore.instance.onLoaderInstantiate(ecPluginData);

        //These core classes carry static @FCLocale fields (permission/help/parameter-error messages)
        //that the real bootstrap loads through ConfigManager.initialize(); that method also boots
        //PlayerController/ECSettings, which this command-only harness has no business touching, so
        //only the classes the dispatch/help path actually reads from are loaded here.
        FCLocaleManager.loadLocale(ecPluginData, FCMessageUtil.class, HelpContext.class, FCDefaultExecutor.class);
    }

    /**
     * Registers a FinalCMD executor instance and returns the single {@link FinalCMDPluginCommand}
     * this call produced. Use {@link #registerAll(Object)} when the executor may declare several
     * independent {@code @FinalCMD} methods (matrix A3).
     */
    public FinalCMDPluginCommand register(Object executor) {
        List<FinalCMDPluginCommand> registered = registerAll(executor);
        return registered.isEmpty() ? null : registered.get(registered.size() - 1);
    }

    /** Same as {@link #register(Object)} but instantiates {@code cmdClass} through its no-arg constructor first. */
    public FinalCMDPluginCommand register(Class<?> cmdClass) {
        List<FinalCMDPluginCommand> registered = FinalCMDManager.registerCommand(ecPluginData, cmdClass);
        return registered.isEmpty() ? null : registered.get(registered.size() - 1);
    }

    /** @return every {@link FinalCMDPluginCommand} produced by this registration call, in registration order. */
    public List<FinalCMDPluginCommand> registerAll(Object executor) {
        return FinalCMDManager.registerCommand(ecPluginData, executor);
    }

    /** @return false if the registration itself failed (e.g. no {@code @FinalCMD} found at all). */
    public boolean registerExpectingFailure(Object executor) {
        return !FinalCMDManager.registerCommand(ecPluginData, executor).isEmpty();
    }

    /** Splits {@code argsLine} on spaces (empty string -&gt; zero args) and dispatches it through the real {@code FCDefaultExecutor}. */
    public void dispatch(FinalCMDPluginCommand cmd, FCommandSender sender, String argsLine) {
        String[] args = argsLine.isEmpty() ? new String[0] : argsLine.split(" ");
        cmd.getExecutor().onCommand(sender, cmd.getPrimaryLabel(), args);
    }

    public List<String> tab(FinalCMDPluginCommand cmd, FCommandSender sender, String... args) {
        return cmd.tabComplete(sender, cmd.getPrimaryLabel(), args);
    }

    @Override
    public void close() {
        ECPluginManager.removePluginData(pluginName);
        world.close();
    }
}
