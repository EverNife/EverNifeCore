package br.com.finalcraft.evernifecore.conformance.hytale.suite;

import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.GroupCases;
import br.com.finalcraft.evernifecore.conformance.hytale.HyWorld;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;

import java.util.Arrays;
import java.util.List;

/**
 * The group engine on a Hytale server, on the player's world thread: the group's {@code deny-friendly-fire} reaches
 * the synthetic player (the DAMAGE source is served here) and {@code max-members} refuses past its ceiling. Hytale has
 * no glow provider, so {@code glow-members} is not a case here.
 */
public final class HyGroupsSuite implements ConformanceSuite {

    private final ECPluginData plugin;

    public HyGroupsSuite(ECPluginData plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "groups";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                HyRegionsSuite.withPlayer("deny-friendly-fire-reaches-the-member", ctx, player -> HyWorld.on(HyWorld.worldOf(HyWorld.player(player)),
                        () -> GroupCases.friendlyFireReachesTheMember(plugin, player))),
                HyRegionsSuite.withPlayer("max-members-refuses-past-the-ceiling", ctx, player -> HyWorld.on(HyWorld.worldOf(HyWorld.player(player)),
                        () -> GroupCases.maxMembersRefusesPastTheCeiling(plugin, player))));
    }
}
