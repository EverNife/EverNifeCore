package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Every {@link OverlapProvider}, by the plugin that registered it; a plugin's providers leave when it shuts down. */
public final class OverlapProviders {

    private static final Map<ECPluginData, List<OverlapProvider>> PROVIDERS = new ConcurrentHashMap<>();

    private OverlapProviders() {
    }

    public static void register(ECPluginData owner, OverlapProvider provider) {
        PROVIDERS.computeIfAbsent(owner, key -> new CopyOnWriteArrayList<>()).add(provider);
    }

    public static void unregisterAll(ECPluginData owner) {
        PROVIDERS.remove(owner);
    }

    public static List<OverlapProvider> all() {
        List<OverlapProvider> all = new ArrayList<>();
        PROVIDERS.values().forEach(all::addAll);
        return Collections.unmodifiableList(all);
    }
}
