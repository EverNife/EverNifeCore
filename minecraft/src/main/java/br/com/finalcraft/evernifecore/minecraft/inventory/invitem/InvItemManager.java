package br.com.finalcraft.evernifecore.minecraft.inventory.invitem;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Material;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The items that carry an inventory of their own (a mod's backpack item, a shulker-like chest), by material and by id,
 * each registered by the plugin that supports it and released when that plugin leaves.
 */
public class InvItemManager {

    private static final class Owned {
        final ECPluginData owner;
        final InvItem invItem;

        Owned(ECPluginData owner, InvItem invItem) {
            this.owner = owner;
            this.invItem = invItem;
        }
    }

    private static final Map<Material, Owned> BY_MATERIAL = new ConcurrentHashMap<>();
    private static final Map<String, Owned> BY_ID = new ConcurrentHashMap<>();

    /**
     * Registers {@code invItem} under its material and its id (case-insensitive) for {@code owner}. The same owner
     * registering again (a reload) replaces its own.
     *
     * @throws IllegalStateException when another plugin already registered that id or that material
     */
    public static synchronized void register(@Nonnull ECPluginData owner, @Nonnull InvItem invItem) {
        Objects.requireNonNull(owner, "'owner' cannot be null when registering an InvItem!");
        String id = invItem.getId().toLowerCase(Locale.ROOT);
        for (Owned taken : new Owned[]{BY_ID.get(id), BY_MATERIAL.get(invItem.getMaterial())}) {
            if (taken != null && taken.owner != owner) {
                throw new IllegalStateException("The InvItem '" + id + "' (" + invItem.getMaterial() + ") is already registered by "
                        + taken.owner.getMetaInfo().getName() + " as '" + taken.invItem.getId() + "', so "
                        + owner.getMetaInfo().getName() + " cannot register it too."
                        + " Give it another id and material, or drop one of the two plugins' support for that item.");
            }
        }
        Owned owned = new Owned(owner, invItem);
        BY_MATERIAL.put(invItem.getMaterial(), owned);
        BY_ID.put(id, owned);
    }

    /** Drops every InvItem {@code owner} registered; the core calls it as that plugin leaves. */
    public static synchronized void unregisterAll(@Nonnull ECPluginData owner) {
        BY_MATERIAL.values().removeIf(owned -> owned.owner == owner);
        BY_ID.values().removeIf(owned -> owned.owner == owner);
    }

    public static @Nullable InvItem of(Material material){
        Owned owned = BY_MATERIAL.get(material);
        return owned == null ? null : owned.invItem;
    }

    public static @Nullable InvItem of(String invItemId){
        Owned owned = BY_ID.get(invItemId.toLowerCase(Locale.ROOT));
        return owned == null ? null : owned.invItem;
    }

}
