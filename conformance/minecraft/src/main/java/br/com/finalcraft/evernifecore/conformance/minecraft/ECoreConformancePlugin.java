package br.com.finalcraft.evernifecore.conformance.minecraft;

import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.HooksSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.PlayerDataSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.SelectionClickSuite;
import br.com.finalcraft.evernifecore.ecplugin.annotations.ECPlugin;
import br.com.finalcraft.evernifecore.minecraft.ecplugin.ECBukkitPlugin;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import org.bukkit.Bukkit;

/**
 * ECoreConformance: runs the core's conformance suites inside a real server, so what only a live server can
 * answer - an item copied into an inventory, a version branch, a GUI opened for a player - is checked where it
 * happens. Routed beside the core for a check-up; never shipped with it.
 */
@ECPlugin
public final class ECoreConformancePlugin extends ECBukkitPlugin {

    @Override
    public void onECPluginEnable() {
        PlayerController.registerPDSectionCfg(getPluginData(), PlayerDataSuite.Section.class, PlayerDataSuite.SECTION_ID);
        getServer().getPluginManager().registerEvents(new SelectionClickSuite.Recorder(), this);
        HooksSuite.registerPlaceholders(getPluginData());
        FinalCMDManager.registerCommand(getPluginData(), new ConformanceCommand(Suites.all(), getDataFolder(),
                () -> MCDetailedVersion.getCurrent() + " " + Bukkit.getVersion(), getLogger()::info,
                task -> Bukkit.getScheduler().runTask(this, task)));
    }

    @Override
    public void onECPluginShutdown() {
    }

    @Override
    public void onECPluginReload() {
    }
}
