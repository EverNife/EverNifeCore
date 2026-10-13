package br.com.finalcraft.evernifecore.conformance.minecraft;

import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.HooksSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.PlayerDataSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.SelectionClickSuite;
import br.com.finalcraft.evernifecore.ecplugin.annotations.ECPlugin;
import br.com.finalcraft.evernifecore.minecraft.ecplugin.ECBukkitPlugin;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import org.bukkit.Bukkit;

import java.io.File;
import java.util.concurrent.CompletableFuture;

/**
 * ECoreConformance: runs the core's conformance suites inside a real server, so what only a live server can
 * answer - an item copied into an inventory, a version branch, a GUI opened for a player - is checked where it
 * happens. Routed beside the core for a check-up; never shipped with it.
 */
@ECPlugin
public final class ECoreConformancePlugin extends ECBukkitPlugin {

    private ECStorage regionStorage;

    @Override
    public void onECPluginEnable() {
        //the regions suite's own manager: its read half needs the regions loaded by the enable after a restart
        String regionFolder = new File(getDataFolder(), "regions").getPath();
        regionStorage = ECStorage.open(BackendDefinition.groupedFile(regionFolder, BackendDefinition.FileFormat.JSON)).join();
        RegionManager regions = RegionManager.of(getPluginData(), regionStorage);
        CompletableFuture<Void> loaded = regions.load();
        PlayerController.registerPDSectionCfg(getPluginData(), PlayerDataSuite.Section.class, PlayerDataSuite.SECTION_ID);
        getServer().getPluginManager().registerEvents(new SelectionClickSuite.Recorder(), this);
        HooksSuite.registerPlaceholders(getPluginData());
        FinalCMDManager.registerCommand(getPluginData(), new ConformanceCommand(Suites.all(regions, loaded), getDataFolder(),
                () -> MCDetailedVersion.getCurrent() + " " + Bukkit.getVersion(), getLogger()::info,
                task -> Bukkit.getScheduler().runTask(this, task)));
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
