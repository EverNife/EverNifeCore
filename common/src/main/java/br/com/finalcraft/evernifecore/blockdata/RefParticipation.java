package br.com.finalcraft.evernifecore.blockdata;

/**
 * How a manager takes part in the {@code Ref} graph of the plugin that owns its storage.
 */
public enum RefParticipation {

    /**
     * Registers in the plugin's shared registry, so a {@code Ref} held by one of its PDSections resolves a
     * chunk of this manager. At most one per plugin: the entity type is the same for every manager, and a
     * registry answers one resolver per type - a second SHARED manager is refused when it is built.
     */
    SHARED,

    /**
     * Registers in a child registry, invisible from the outside: it collides with nothing, and no
     * {@code Ref} from elsewhere resolves it. A {@code Ref} inside a block value still resolves the plugin's
     * graph, since a child registry falls back to its parent.
     */
    ISOLATED
}
