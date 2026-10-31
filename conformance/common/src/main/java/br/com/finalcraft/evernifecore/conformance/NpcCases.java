package br.com.finalcraft.evernifecore.conformance;

import br.com.finalcraft.evernifecore.action.ActionSupport;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.npc.NpcProvider;
import br.com.finalcraft.evernifecore.npc.NpcRef;
import br.com.finalcraft.evernifecore.npc.Npcs;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** What the NPC registry answers on a live server, the same on every platform; each platform says what it expects. */
public final class NpcCases {

    private NpcCases() {
    }

    /** The providers the core registered at boot are exactly {@code expectedIds}, in that order. */
    public static Verdict providersRegistered(List<String> expectedIds) {
        List<String> ids = new ArrayList<>();
        for (NpcProvider provider : Npcs.getProviders()) {
            ids.add(provider.getId());
        }
        return Verdict.of(ids.equals(expectedIds), "providers=" + ids + " expected=" + expectedIds);
    }

    /** Each provider is available exactly when {@code expectAvailable} says - its plugin installed, or the platform's own. */
    public static Verdict availability(Predicate<NpcProvider> expectAvailable) {
        List<String> wrong = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (NpcProvider provider : Npcs.getProviders()) {
            boolean available = provider.isAvailable();
            seen.add(provider.getId() + "=" + available);
            if (available != expectAvailable.test(provider)) {
                wrong.add(provider.getId());
            }
        }
        return Verdict.of(wrong.isEmpty() && !seen.isEmpty(), "available " + seen + (wrong.isEmpty() ? "" : " wrong=" + wrong));
    }

    /** {@code provider:id} reads back lowercased and round-trips. */
    public static Verdict refRoundTrip() {
        NpcRef ref = NpcRef.parse("ZNPCsPlus:Merchant");
        return Verdict.of("znpcsplus:merchant".equals(ref.toString()) && ref.equals(NpcRef.parse(ref.toString())), "ref=" + ref);
    }

    /** An NPC that does not exist is found nowhere, without an exception, through every lookup. */
    public static Verdict unknownNpcIsNowhere(NpcRef missing) {
        boolean nowhere = !Npcs.find(missing).isPresent() && !Npcs.getLocation(missing).isPresent() && !Npcs.getName(missing).isPresent();
        return Verdict.of(nowhere, "find/getLocation/getName of " + missing + " empty=" + nowhere);
    }

    /** This platform raises {@code interact-npc}, with its cancellable Pre. */
    public static Verdict interactNpcServed() {
        boolean served = ActionSupport.isServed(EventSource.INTERACT_NPC);
        boolean pre = ActionSupport.hasPre(EventSource.INTERACT_NPC);
        return Verdict.of(served && pre, "served=" + served + " pre=" + pre + " platform=" + ActionSupport.getPlatform());
    }
}
