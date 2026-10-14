package br.com.finalcraft.evernifecore.region.actor;

import java.util.Locale;

/** What role an entity plays, across platforms. */
public enum ECEntityType {
    PLAYER,
    /** An entity that implements a player without being one: a mod machine acting under a name and a UUID. */
    FAKE_PLAYER,
    ANIMAL,
    MONSTER,
    /** A character with a role - a Citizens NPC, a Hytale NPC with a role. */
    NPC,
    OTHER;

    private final String word = name().toLowerCase(Locale.ROOT).replace('_', '-');

    /** How settings write this type: {@code player}, {@code fake-player}, {@code animal}... */
    public String getWord() {
        return word;
    }
}
