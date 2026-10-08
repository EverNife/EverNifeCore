package br.com.finalcraft.evernifecore.conformance.minecraft;

import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.ItemsSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.NbtSuite;

import java.util.Arrays;
import java.util.List;

/** Every suite this plugin runs, in the order {@code run all} takes them. A new suite is one more entry here. */
public final class Suites {

    private Suites() {
    }

    public static List<ConformanceSuite> all() {
        return Arrays.asList(
                new ItemsSuite(),
                new NbtSuite());
    }
}
