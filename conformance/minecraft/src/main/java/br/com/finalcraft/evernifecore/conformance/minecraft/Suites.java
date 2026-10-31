package br.com.finalcraft.evernifecore.conformance.minecraft;

import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.ActionsSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.CommandsSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.DeliveriesSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.EventsSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.GroupsSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.ScreenSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.HooksSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.HudSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.ItemsSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.LogicSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.NbtSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.NmsSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.NpcsSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.ParticlesSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.PlatformSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.PlayerDataSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.RegionsSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.SchedulerSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.SelectionClickSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.TextMetricsSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.VersionSuite;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.RegionManager;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Every suite this plugin runs, in the order {@code run all} takes them. A new suite is one more entry here. */
public final class Suites {

    private Suites() {
    }

    /**
     * @param plugin  the plugin itself, the caller of the {@code logic} and {@code deliveries} suites and the owner of the
     *                {@code groups} suite's parties
     * @param regions the plugin's own region manager, for the {@code regions} suite
     * @param loaded  that manager's {@code load()}
     */
    public static List<ConformanceSuite> all(ECPluginData plugin, RegionManager regions, CompletableFuture<Void> loaded) {
        return Arrays.asList(
                new ItemsSuite(),
                new NbtSuite(),
                new PlatformSuite(),
                new VersionSuite(),
                new CommandsSuite(),
                new ScreenSuite(),
                new SchedulerSuite(),
                new PlayerDataSuite(),
                new EventsSuite(),
                new SelectionClickSuite(),
                new ParticlesSuite(),
                new TextMetricsSuite(),
                new HooksSuite(regions, loaded),
                new NmsSuite(),
                new RegionsSuite(regions, loaded),
                new LogicSuite(plugin),
                new ActionsSuite(),
                new DeliveriesSuite(plugin),
                new HudSuite(),
                new GroupsSuite(plugin),
                new NpcsSuite());
    }
}
