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
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.SchedulerSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.SelectionClickSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.TextMetricsSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.VersionSuite;

import java.util.Arrays;
import java.util.List;

/** Every suite this plugin runs, in the order {@code run all} takes them. A new suite is one more entry here. */
public final class Suites {

    private Suites() {
    }

    public static List<ConformanceSuite> all() {
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
                new NmsSuite());
    }
}
