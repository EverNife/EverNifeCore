package br.com.finalcraft.evernifecore.minecraft.npc;

import br.com.finalcraft.evernifecore.api.platoverride.entity.NativeEntity;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.npc.NpcRef;
import br.com.finalcraft.evernifecore.npc.Npcs;
import jakarta.annotation.Nullable;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/** The Bukkit NPC providers: Citizens, FancyNpcs and ZNPCsPlus, each registered always and available only with its plugin. */
public final class McNpcProviders {

    private McNpcProviders() {
    }

    /** Registers the three under {@code owner}; {@code plugin} is the Bukkit plugin their click listeners are registered for. */
    public static void registerAll(ECPluginData owner, Plugin plugin) {
        Npcs.register(owner, new CitizensNpcProvider(plugin));
        Npcs.register(owner, new FancyNpcsNpcProvider(plugin));
        Npcs.register(owner, new ZNpcsPlusNpcProvider(plugin));
    }

    /** Which NPC {@code entity} is, by the registered providers; null for one none knows - at once while none is registered. */
    public static @Nullable NpcRef resolve(Entity entity) {
        return Npcs.getProviders().isEmpty() ? null : Npcs.resolve(new NativeEntity(entity)).orElse(null);
    }
}
