package br.com.finalcraft.evernifecore.conformance.minecraft;

import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.CommandsSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.EventsSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.GuiSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.HooksSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.ItemsSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.NbtSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.NmsSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.ParticlesSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.PlatformSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.PlayerDataSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.RegionsSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.SchedulerSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.SelectionClickSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.TextMetricsSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.VersionSuite;
import br.com.finalcraft.evernifecore.region.RegionManager;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Every suite this plugin runs, in the order {@code run all} takes them. A new suite is one more entry here. */
public final class Suites {

    private Suites() {
    }

    /**
     * @param regions the plugin's own region manager, for the {@code regions} suite
     * @param loaded  that manager's {@code load()}
     */
    public static List<ConformanceSuite> all(RegionManager regions, CompletableFuture<Void> loaded) {
        return Arrays.asList(
                new ItemsSuite(),
                new NbtSuite(),
                new PlatformSuite(),
                new VersionSuite(),
                new CommandsSuite(),
                new GuiSuite(),
                new SchedulerSuite(),
                new PlayerDataSuite(),
                new EventsSuite(),
                new SelectionClickSuite(),
                new ParticlesSuite(),
                new TextMetricsSuite(),
                new HooksSuite(),
                new NmsSuite(),
                new RegionsSuite(regions, loaded));
    }
}
