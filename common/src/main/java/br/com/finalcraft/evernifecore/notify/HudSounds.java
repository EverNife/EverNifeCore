package br.com.finalcraft.evernifecore.notify;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Portable sound ids for {@link IHudAccess#playSound}: each platform maps them to a sound of its own era, so a plugin
 * names the intent instead of a version's key.
 */
public final class HudSounds {

    public static final String LEVEL_UP = "evernifecore:level-up";
    public static final String CLICK = "evernifecore:click";
    public static final String PLING = "evernifecore:pling";
    public static final String DENIED = "evernifecore:denied";

    /** Every alias above. */
    public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(LEVEL_UP, CLICK, PLING, DENIED));

    private HudSounds() {
    }

    /** Whether {@code soundId} is one of the aliases, not a platform id. */
    public static boolean isAlias(String soundId) {
        return ALL.contains(soundId);
    }
}
