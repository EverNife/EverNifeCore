package br.com.finalcraft.evernifecore.logic.delivery;

import br.com.finalcraft.evernifecore.playerdata.PDSection;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * A player's pending {@link Delivery}s, oldest first, kept by the core (section {@value #SECTION_ID}). Changed only
 * through {@link Deliveries}; every change marks it dirty. Thread-safe.
 */
public class DeliveriesSection extends PDSection {

    /** The section id the core registers this under. */
    public static final String SECTION_ID = "deliveries";

    private List<Delivery> deliveries = new ArrayList<>();

    public DeliveriesSection() {
        //Jackson no-arg constructor - the framework attaches the PlayerData afterwards
    }

    /** A copy, oldest first. */
    public synchronized List<Delivery> getDeliveries() {
        return Collections.unmodifiableList(new ArrayList<>(deliveries));
    }

    public synchronized @Nullable Delivery get(UUID id) {
        for (Delivery delivery : deliveries) {
            if (delivery.getId().equals(id)) {
                return delivery;
            }
        }
        return null;
    }

    synchronized void add(Delivery delivery) {
        deliveries.add(delivery);
        markDirty();
    }

    /** Puts {@code updated} where the delivery of the same id is; false when it is gone. */
    synchronized boolean replace(Delivery updated) {
        for (int i = 0; i < deliveries.size(); i++) {
            if (deliveries.get(i).getId().equals(updated.getId())) {
                deliveries.set(i, updated);
                markDirty();
                return true;
            }
        }
        return false;
    }

    /** Removes and returns every delivery {@code filter} accepts. */
    synchronized List<Delivery> removeIf(Predicate<Delivery> filter) {
        List<Delivery> removed = new ArrayList<>();
        for (Delivery delivery : deliveries) {
            if (filter.test(delivery)) {
                removed.add(delivery);
            }
        }
        if (!removed.isEmpty()) {
            deliveries.removeAll(removed);
            markDirty();
        }
        return removed;
    }
}
