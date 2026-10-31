package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.NpcCases;
import br.com.finalcraft.evernifecore.npc.NpcRef;
import org.bukkit.Bukkit;

import java.util.Arrays;
import java.util.List;

/**
 * The NPC registry on a Bukkit server: the three reflected providers are registered whether or not their plugin is
 * here, each available exactly when its plugin is enabled, lookups of an absent NPC answer empty, and
 * {@code interact-npc} is served. The boot's linkage errors are the driver's to read.
 */
public final class NpcsSuite implements ConformanceSuite {

    @Override
    public String getId() {
        return "npcs";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                ConformanceCase.of("providers-registered", () -> NpcCases.providersRegistered(Arrays.asList("citizens", "fancynpcs", "znpcsplus"))),
                ConformanceCase.of("available-only-with-the-plugin", () -> NpcCases.availability(provider ->
                        Bukkit.getPluginManager().isPluginEnabled(pluginOf(provider.getId())))),
                ConformanceCase.of("ref-round-trip", NpcCases::refRoundTrip),
                ConformanceCase.of("unknown-npc-is-nowhere", () -> NpcCases.unknownNpcIsNowhere(NpcRef.parse("citizens:2147483647"))),
                ConformanceCase.of("interact-npc-served", NpcCases::interactNpcServed));
    }

    private static String pluginOf(String providerId) {
        switch (providerId) {
            case "citizens":
                return "Citizens";
            case "fancynpcs":
                return "FancyNpcs";
            default:
                return "ZNPCsPlus";
        }
    }
}
