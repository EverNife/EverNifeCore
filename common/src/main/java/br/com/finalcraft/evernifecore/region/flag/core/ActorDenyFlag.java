package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A deny flag that refines another for some actors only: it applies to the actions of {@code parent} done by the
 * actors its entries name (the words and keys {@link DenySettings} reads), so where it is defined it decides for
 * them, and the parent keeps deciding for everyone else. Its own {@code actors} setting can only narrow that set.
 */
public final class ActorDenyFlag extends DenyFlag {

    private final List<String> actorEntries;
    private final String description;

    /**
     * @param parent       the flag refined; this one hears the same event
     * @param description  the line for a server admin, see {@link #getDescription()}
     * @param actorEntries the actors this flag is about, e.g. {@code "animal", "monster"}
     */
    public ActorDenyFlag(FlagKey key, DenyFlag parent, String description, String... actorEntries) {
        super(key, parent.getNeeds().iterator().next(), parent);
        if (actorEntries.length == 0) {
            throw new IllegalArgumentException("ActorDenyFlag " + key + " names no actor: pass the words or keys it is about, like \"monster\".");
        }
        this.actorEntries = Collections.unmodifiableList(Arrays.asList(actorEntries));
        this.description = description;
    }

    /** The actors this flag is about, fixed at construction. */
    public List<String> getActorEntries() {
        return actorEntries;
    }

    @Override
    public boolean applies(ActionContext context) {
        return DenySettings.anyMatches(actorEntries, context.getActor()) && getParent().applies(context);
    }

    @Override
    public String getDescription() {
        return description;
    }
}
