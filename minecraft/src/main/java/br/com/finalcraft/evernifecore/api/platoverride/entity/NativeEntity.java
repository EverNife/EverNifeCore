package br.com.finalcraft.evernifecore.api.platoverride.entity;

import jakarta.annotation.Nullable;
import org.bukkit.entity.Entity;

import java.util.Objects;
import java.util.UUID;

/**
 * A Bukkit {@link Entity} handed to platform-agnostic code for the length of one event. Once the event's dispatch ends
 * the handle is closed and reading the entity through it throws: keep {@link #getUniqueId()}, not the handle.
 */
public final class NativeEntity {

    private final Entity entity;
    private final UUID uniqueId;
    private volatile boolean closed;

    public NativeEntity(Entity entity) {
        this.entity = Objects.requireNonNull(entity, "'entity' cannot be null: a NativeEntity wraps a real entity!");
        this.uniqueId = entity.getUniqueId();
    }

    /** Readable after the handle closed: the way to find the entity again later. */
    public UUID getUniqueId() {
        return uniqueId;
    }

    /** @throws IllegalStateException once the handle is closed */
    public Entity getEntity() {
        requireOpen();
        return entity;
    }

    /**
     * The entity as {@code type}; null when it is another kind of entity.
     *
     * @throws IllegalStateException once the handle is closed
     */
    public <E extends Entity> @Nullable E getEntity(Class<E> type) {
        requireOpen();
        return type.isInstance(entity) ? type.cast(entity) : null;
    }

    public boolean isClosed() {
        return closed;
    }

    /** Internal: the event's dispatch ended. */
    public void close() {
        closed = true;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("The native entity " + uniqueId + " was used after its event was decided: read what you need"
                    + " during the event, and keep the UUID (" + uniqueId + "), not the handle.");
        }
    }
}
