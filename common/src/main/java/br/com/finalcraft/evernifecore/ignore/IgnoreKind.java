package br.com.finalcraft.evernifecore.ignore;

public enum IgnoreKind {

    /** Every chat line from the ignored player: public channels and private messages alike. */
    CHAT(null),
    /** Private messages only (/msg, /tell, /r). Ignoring {@link #CHAT} covers these too. */
    PRIVATE_MESSAGE(CHAT),
    /** Teleport requests (/tpa, /tpahere) the ignored player sends. */
    TELEPORT_REQUEST(null);

    private final IgnoreKind broader;

    IgnoreKind(IgnoreKind broader) {
        this.broader = broader;
    }

    /** The kind that also covers this one, or null. Asking about this kind asks about that one too. */
    public IgnoreKind getBroader() {
        return broader;
    }
}
