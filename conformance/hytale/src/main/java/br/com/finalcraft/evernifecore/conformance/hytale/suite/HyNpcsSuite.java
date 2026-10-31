package br.com.finalcraft.evernifecore.conformance.hytale.suite;

import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.NpcCases;
import br.com.finalcraft.evernifecore.conformance.hytale.HyWorld;
import br.com.finalcraft.evernifecore.npc.NpcRef;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The NPC registry on a Hytale server: the platform's own provider is registered and always available, an unknown
 * uuid is found nowhere - looked up on the world thread, where Hytale finds an entity -, and {@code interact-npc} is
 * served by the use System.
 */
public final class HyNpcsSuite implements ConformanceSuite {

    @Override
    public String getId() {
        return "npcs";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                ConformanceCase.of("providers-registered", () -> NpcCases.providersRegistered(Collections.singletonList("hytale"))),
                ConformanceCase.of("hytale-always-available", () -> NpcCases.availability(provider -> true)),
                ConformanceCase.of("ref-round-trip", NpcCases::refRoundTrip),
                ConformanceCase.later("unknown-npc-is-nowhere", () -> HyWorld.on(HyWorld.getDefault(),
                        () -> NpcCases.unknownNpcIsNowhere(NpcRef.of("hytale", UUID.randomUUID().toString())))),
                ConformanceCase.of("interact-npc-served", NpcCases::interactNpcServed));
    }
}
