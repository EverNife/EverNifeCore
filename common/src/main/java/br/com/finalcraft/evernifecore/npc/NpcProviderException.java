package br.com.finalcraft.evernifecore.npc;

/** An {@link NpcRef} that names a provider no plugin registered on this server. */
public final class NpcProviderException extends IllegalArgumentException {

    public NpcProviderException(String message) {
        super(message);
    }
}
