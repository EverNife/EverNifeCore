package br.com.finalcraft.evernifecore.api.platoverride.entity;

import java.util.UUID;

public class NativeEntity {

    private final UUID uniqueId;

    public NativeEntity() {
        this(UUID.randomUUID());
    }

    public NativeEntity(UUID uniqueId) {
        this.uniqueId = uniqueId;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public void close() {
    }

}
