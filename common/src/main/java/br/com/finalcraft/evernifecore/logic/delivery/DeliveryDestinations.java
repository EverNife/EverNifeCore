package br.com.finalcraft.evernifecore.logic.delivery;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import br.com.finalcraft.evernifecore.types.TypeRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The registered {@link DeliveryDestination}s, by {@code owner:name} id or a short name only one has
 * ({@code inventory}, {@code pending}). Any thread.
 */
public final class DeliveryDestinations {

    private static final TypeRegistry<DeliveryDestination> REGISTRY = TypeRegistry.of(DeliveryDestination.class, "destination");
    private static final Map<String, DeliveryDestination> BY_KEY = new ConcurrentHashMap<>();

    /** Why an item effect or cost cannot run while no platform registered {@value InventoryDestination#ID}. */
    public static final String NO_INVENTORY = "No delivery destination '" + InventoryDestination.ID + "' is registered: the platform"
            + " registers it when EverNifeCore enables (a test registers one with Platforms.withInventory(slots)). Until then an item"
            + " effect or cost has nowhere to reach the player's items.";

    private DeliveryDestinations() {
    }

    /** The registry behind ids; one type per destination, without parameters. */
    public static TypeRegistry<DeliveryDestination> registry() {
        return REGISTRY;
    }

    /** The core's own destinations ({@code evernifecore:pending}), registered under {@code core} on enable. */
    public static void registerCore(ECPluginData core) {
        if (!REGISTRY.find(PendingDestination.ID).isPresent()) {
            register(core, PendingDestination.INSTANCE);
        }
    }

    /**
     * Registers {@code destination} under {@code owner}, the plugin its id names.
     *
     * @throws IllegalArgumentException when the id names another plugin
     * @throws IllegalStateException when the id is already registered
     */
    public static void register(ECPluginData owner, DeliveryDestination destination) {
        REGISTRY.register(owner, TypeDescriptor.<DeliveryDestination>of(destination.getId())
                .describe("Delivers to " + destination.getId() + ".")
                .factory(params -> destination)
                .build());
        BY_KEY.put(REGISTRY.find(destination.getId()).get().getKey(), destination);
    }

    /** Takes out every destination {@code owner} registered. */
    public static void unregisterAll(ECPluginData owner) {
        List<String> keys = new ArrayList<>();
        for (TypeDescriptor<DeliveryDestination> descriptor : REGISTRY.getAll()) {
            if (REGISTRY.getOwner(descriptor).orElse(null) == owner) {
                keys.add(descriptor.getKey());
            }
        }
        for (String key : keys) {
            REGISTRY.unregister(owner, key);
            BY_KEY.remove(key);
        }
    }

    /** Takes out the one destination {@code id} that {@code owner} registered; false when it had none under that id. */
    public static boolean unregister(ECPluginData owner, String id) {
        boolean removed = REGISTRY.unregister(owner, id);
        if (removed) {
            BY_KEY.remove(id);
        }
        return removed;
    }

    public static Optional<DeliveryDestination> find(String idOrName) {
        return REGISTRY.find(idOrName).map(descriptor -> BY_KEY.get(descriptor.getKey()));
    }

    /** The platform's {@value InventoryDestination#ID}, when one is registered. */
    public static Optional<InventoryDestination> inventory() {
        return find(InventoryDestination.ID).filter(InventoryDestination.class::isInstance).map(InventoryDestination.class::cast);
    }

    /**
     * @throws IllegalStateException naming the inventory destination and who registers it
     */
    public static InventoryDestination requireInventory() {
        return inventory().orElseThrow(() -> new IllegalStateException(NO_INVENTORY));
    }
}
