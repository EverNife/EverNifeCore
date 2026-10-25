package br.com.finalcraft.evernifecore.ranking;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The ranked lists reachable by id ({@code <owner>:<name>}). Nobody registers by hand: building a
 * {@code RankedList.of(...)} is the registration. Any thread.
 */
public final class RankedLists {

    private static final Map<String, RankedList<?>> LISTS = new ConcurrentHashMap<>();

    private RankedLists() {
    }

    static void register(RankedList<?> list) {
        //overwriting is what a plugin reload looks like: the list built last is the one old links must reach
        LISTS.put(list.getId(), list);
    }

    /** The list named {@code id}, case-insensitive, or {@code null}. */
    public static @Nullable RankedList<?> find(String id) {
        return id == null ? null : LISTS.get(id.toLowerCase(Locale.ROOT));
    }

    /** Every list {@code owner} built and has not closed. */
    public static Collection<RankedList<?>> ownedBy(ECPluginData owner) {
        List<RankedList<?>> owned = new ArrayList<>();
        for (RankedList<?> list : LISTS.values()) {
            if (list.getOwner() == owner) {
                owned.add(list);
            }
        }
        return owned;
    }

    /** Drops every list of {@code owner} - its shutdown. */
    public static void closeOwnedBy(ECPluginData owner) {
        LISTS.values().removeIf(list -> list.getOwner() == owner);
    }

    /** Drops every list, so one test's lists are never another's. */
    public static void clear() {
        LISTS.clear();
    }
}
