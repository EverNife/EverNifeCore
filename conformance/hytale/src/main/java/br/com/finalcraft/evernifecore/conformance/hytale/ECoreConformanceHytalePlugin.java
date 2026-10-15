package br.com.finalcraft.evernifecore.conformance.hytale;

import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.conformance.hytale.suite.HyCostSuite;
import br.com.finalcraft.evernifecore.conformance.hytale.suite.HyPlatformSuite;
import br.com.finalcraft.evernifecore.conformance.hytale.suite.HyRegionsSuite;
import br.com.finalcraft.evernifecore.ecplugin.annotations.ECPlugin;
import br.com.finalcraft.evernifecore.hytale.ecplugin.ECHytalePlugin;
import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import com.hypixel.hytale.common.util.java.ManifestUtil;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.Level;

/**
 * ECoreConformance on Hytale: runs the core's conformance suites inside a real Hytale server - what only the live
 * server answers, such as whether a region's System cancels the event the game fires. Routed into {@code mods/}
 * beside the core for a check-up; never shipped with it.
 */
@ECPlugin
public final class ECoreConformanceHytalePlugin extends ECHytalePlugin {

    /**
     * Markers go to the process's own stdout: the server prefixes both {@code System.out} and its logger, and a marker
     * is read from column 0.
     */
    private static final PrintStream RAW_STDOUT = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);

    private ECStorage regionStorage;

    public ECoreConformanceHytalePlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    public void onECPluginEnable() {
        File dataFolder = getDataDirectory().toFile();
        regionStorage = ECStorage.open(BackendDefinition.groupedFile(new File(dataFolder, "regions").getPath(),
                BackendDefinition.FileFormat.JSON)).join();
        RegionManager regions = RegionManager.of(getPluginData(), regionStorage);
        regions.load();
        FinalCMDManager.registerCommand(getPluginData(), new HyConformanceCommand(
                Arrays.asList(new HyPlatformSuite(this), new HyRegionsSuite(regions), new HyCostSuite(regions)),
                dataFolder, () -> "Hytale " + ManifestUtil.getImplementationVersion(), RAW_STDOUT::println,
                line -> getLogger().at(Level.INFO).log(line), task -> HyWorld.getDefault().execute(task)));
    }

    @Override
    public void onECPluginShutdown() {
        if (regionStorage != null) {   //the core closed the region manager already; the storage is ours
            regionStorage.close().join();
            regionStorage = null;
        }
    }

    @Override
    public void onECPluginReload() {
    }
}
