package br.com.finalcraft.evernifecore.flag;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Where a plugin registers the {@link FlagScope#ACTOR} holders a player belongs to (a party, a guild), so their values
 * join the resolution wherever the player stands, and their raw values relink when a flag registers.
 */
public final class ActorHolders {

    /** What one plugin knows about its holders. */
    public interface Provider {
        /** The ACTOR holders {@code player} belongs to now; any thread, no I/O. Empty when none. */
        Collection<? extends FlagHolder> holdersOf(FPlayer player);

        /** Every holder this provider knows, for relinking raw values when a flag registers. */
        Collection<? extends FlagHolder> all();
    }

    private static final class Entry {
        final ECPluginData owner;
        final Provider provider;

        Entry(ECPluginData owner, Provider provider) {
            this.owner = owner;
            this.provider = provider;
        }
    }

    private static final List<Entry> PROVIDERS = new CopyOnWriteArrayList<>();

    private ActorHolders() {
    }

    public static void register(ECPluginData owner, Provider provider) {
        PROVIDERS.add(new Entry(Objects.requireNonNull(owner, "owner"), Objects.requireNonNull(provider, "provider")));
    }

    /** Drops every provider {@code owner} registered - its shutdown. */
    public static void unregisterAll(ECPluginData owner) {
        PROVIDERS.removeIf(entry -> entry.owner == owner);
    }

    /**
     * The holders of {@code player}, provider by provider in registration order, each provider's by
     * {@link FlagHolder#getPriority()} (highest first) - the order the resolution ranks them in.
     */
    public static List<FlagHolder> holdersOf(FPlayer player) {
        if (PROVIDERS.isEmpty()) {
            return Collections.emptyList();
        }
        List<FlagHolder> holders = new ArrayList<>();
        for (Entry entry : PROVIDERS) {
            List<FlagHolder> theirs = new ArrayList<>(entry.provider.holdersOf(player));
            theirs.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
            holders.addAll(theirs);
        }
        return holders;
    }

    public static boolean hasAny(FPlayer player) {
        for (Entry entry : PROVIDERS) {
            if (!entry.provider.holdersOf(player).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Every holder of every provider. */
    public static List<FlagHolder> all() {
        List<FlagHolder> holders = new ArrayList<>();
        for (Entry entry : PROVIDERS) {
            holders.addAll(entry.provider.all());
        }
        return holders;
    }
}
