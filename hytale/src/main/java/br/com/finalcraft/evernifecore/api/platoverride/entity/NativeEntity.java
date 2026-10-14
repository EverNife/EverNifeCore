package br.com.finalcraft.evernifecore.api.platoverride.entity;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import jakarta.annotation.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * A Hytale entity reference handed to platform-agnostic code for the length of one event. Reading it needs the
 * reference still valid and the thread of its store; once the event's dispatch ends the handle is closed and every
 * read throws: keep {@link #getUniqueId()}, not the handle.
 */
public final class NativeEntity {

    private final Ref<EntityStore> ref;
    private final UUID uniqueId;
    private volatile boolean closed;

    public NativeEntity(Ref<EntityStore> ref, UUID uniqueId) {
        this.ref = Objects.requireNonNull(ref, "'ref' cannot be null: a NativeEntity wraps a real entity!");
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
    }

    /** Readable after the handle closed: the way to find the entity again later. */
    public UUID getUniqueId() {
        return uniqueId;
    }

    /** @throws IllegalStateException once closed, when the entity is gone, or off the thread of its store */
    public Ref<EntityStore> getRef() {
        if (closed) {
            throw new IllegalStateException("The native entity " + uniqueId + " was used after its event was decided: read what you need"
                    + " during the event, and keep the UUID (" + uniqueId + "), not the handle.");
        }
        if (!ref.isValid()) {
            throw new IllegalStateException("The native entity " + uniqueId + " is no longer in its world: its reference was invalidated.");
        }
        if (!ref.getStore().isInThread()) {
            throw new IllegalStateException("The native entity " + uniqueId + " was read on " + Thread.currentThread().getName()
                    + ": read it on the thread of its world, where its event runs.");
        }
        return ref;
    }

    /**
     * A component of the entity; null when it has none of {@code type}.
     *
     * @throws IllegalStateException as {@link #getRef()}
     */
    public <T extends Component<EntityStore>> @Nullable T getComponent(ComponentType<EntityStore, T> type) {
        Ref<EntityStore> live = getRef();
        return live.getStore().getComponent(live, type);
    }

    public boolean isClosed() {
        return closed;
    }

    /** Internal: the event's dispatch ended. */
    public void close() {
        closed = true;
    }
}
