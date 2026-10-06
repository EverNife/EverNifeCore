package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import jakarta.annotation.Nullable;

import java.util.Objects;

/** A creature about to spawn. Which spawns are asked about at all (eggs, spawners) is the platform's choice. */
public final class SpawnContext extends ActionContext {

    public enum Kind {
        ANIMAL, MONSTER, OTHER
    }

    private final Kind entityKind;
    private final String entityTypeId;
    private final String reason;

    /** @param reason the platform's own spawn reason, as text */
    public SpawnContext(WorldLocPos position, @Nullable Object nativeEvent, Kind entityKind, String entityTypeId, String reason) {
        super(EventSource.SPAWN, position, null, nativeEvent);
        this.entityKind = Objects.requireNonNull(entityKind, "entityKind");
        this.entityTypeId = Objects.requireNonNull(entityTypeId, "entityTypeId");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Kind getEntityKind() {
        return entityKind;
    }

    public String getEntityTypeId() {
        return entityTypeId;
    }

    public String getReason() {
        return reason;
    }
}
