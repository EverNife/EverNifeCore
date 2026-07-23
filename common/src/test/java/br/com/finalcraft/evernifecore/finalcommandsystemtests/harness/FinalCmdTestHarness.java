package br.com.finalcraft.evernifecore.finalcommandsystemtests.harness;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.ecplugin.IPluginMetaInfo;
import br.com.finalcraft.evernifecore.testutil.TestPlatformFixture;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Headless end-to-end harness for the FinalCMD framework: registration -&gt; dispatch -&gt; parse -&gt;
 * invoke -&gt; message, without a real Bukkit/Hytale server.
 * <p>
 * Every instance owns its own {@link CommandCapturePlatform} and a uniquely-named
 * {@link ECPluginData} (backed by the caller-supplied {@code @TempDir}), so tests never leak
 * registered commands or locale state into one another. Create one per test (or per test class, if
 * the tests within it are fine sharing the same fake plugin) and {@link #close()} it in
 * {@code @AfterEach}/{@code @AfterAll}.
 */
public class FinalCmdTestHarness implements AutoCloseable {

    private static final AtomicInteger UNIQUE_SUFFIX = new AtomicInteger();

    public final String pluginName;
    public final CommandCapturePlatform platform;
    public final ECPluginData ecPluginData;

    public FinalCmdTestHarness(String namePrefix, Path dataFolder) {
        //guarantees SOME platform is registered before FinalCMDManager's static block ever runs,
        //no matter which test in the JVM touches it first
        TestPlatformFixture.ensureInstalled();

        this.pluginName = namePrefix + "_" + UNIQUE_SUFFIX.incrementAndGet();
        this.platform = new CommandCapturePlatform();

        EverNifeCore.getProviders().getBaseProvider().register(IPlatform.class, platform);
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                new FakePluginExtractor(pluginName, dataFolder.toFile()));

        this.ecPluginData = ECPluginManager.getOrCreateECorePluginData(new Object());

        //FinalCMDManager's static block (first FinalCMD ever touched in this JVM) logs through
        //EverNifeCore's OWN ecPluginData (EverNifeCore.getLog()), which is otherwise only set by the
        //real bootstrap (onLoaderInstantiate); without it, ArgParserManager.addGlobalParser NPEs.
        EverNifeCore.instance.onLoaderInstantiate(ecPluginData);
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
        int before = platform.registrationOrder().size();
        boolean ok = FinalCMDManager.registerCommand(ecPluginData, cmdClass);
        return extractLast(before, ok);
    }

    /** @return every {@link FinalCMDPluginCommand} produced by this registration call, in registration order. */
    public List<FinalCMDPluginCommand> registerAll(Object executor) {
        int before = platform.registrationOrder().size();
        boolean ok = FinalCMDManager.registerCommand(ecPluginData, executor);
        if (!ok) return new ArrayList<>();
        return new ArrayList<>(platform.registrationOrder().subList(before, platform.registrationOrder().size()));
    }

    /** @return false if the registration itself failed (e.g. no {@code @FinalCMD} found at all). */
    public boolean registerExpectingFailure(Object executor) {
        return FinalCMDManager.registerCommand(ecPluginData, executor);
    }

    private FinalCMDPluginCommand extractLast(int before, boolean ok) {
        if (!ok) return null;
        List<FinalCMDPluginCommand> newOnes = platform.registrationOrder().subList(before, platform.registrationOrder().size());
        return newOnes.isEmpty() ? null : newOnes.get(newOnes.size() - 1);
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
    }

    // ------------------------------------------------------------------
    // Fake plugin plumbing (same shape as ECPluginDataReloadTest's)
    // ------------------------------------------------------------------

    private static final class FakePluginExtractor implements IECPluginExtractor {
        private final String pluginName;
        private final File dataFolder;

        FakePluginExtractor(String pluginName, File dataFolder) {
            this.pluginName = pluginName;
            this.dataFolder = dataFolder;
        }

        @Override
        public String getPluginName(Object javaPlugin) {
            return pluginName;
        }

        @Override
        public boolean isJavaPlugin(Object plugin) {
            return true;
        }

        @Override
        public Object getProvidingPlugin(Class<?> clazz) {
            return null;
        }

        @Override
        public IPluginMetaInfo getPluginMetaInfo(Object javaPlugin) {
            return new FakeMetaInfo(javaPlugin, pluginName, dataFolder);
        }
    }

    private static final class FakeMetaInfo implements IPluginMetaInfo {
        private final Object plugin;
        private final String pluginName;
        private final File dataFolder;

        FakeMetaInfo(Object plugin, String pluginName, File dataFolder) {
            this.plugin = plugin;
            this.pluginName = pluginName;
            this.dataFolder = dataFolder;
        }

        @Override
        public String getName() {
            return pluginName;
        }

        @Override
        public String getVersion() {
            return "1.0.0";
        }

        @Override
        public String getAuthor() {
            return "Petrus";
        }

        @Override
        public String getGroup() {
            return "br.com.finalcraft";
        }

        @Override
        public File getDataFolder() {
            return dataFolder;
        }

        @Override
        public Object getDelegate() {
            return plugin;
        }
    }
}
