package br.com.finalcraft.evernifecore.region.playerstate;

import br.com.finalcraft.evernifecore.playerdata.PDSection;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a player had before the session flags that restore {@code persistent} changed it, slot by slot, as
 * {@link HeldPlayerState} encodes it - kept so a player inside a region when the server stopped gets it back on the
 * next join. Empty while no such flag holds anything.
 */
public class PlayerStateBackup extends PDSection {

    /** The section id the core registers this under. */
    public static final String SECTION_ID = "region_state";

    private Map<String, String> slots = new LinkedHashMap<>();

    public PlayerStateBackup() {
        //Jackson no-arg constructor - the framework attaches the PlayerData afterwards
    }

    public @Nullable String get(String slot) {
        return slots.get(slot);
    }

    public void put(String slot, String before) {
        slots.put(slot, before);
        markDirty();
    }

    public void remove(String slot) {
        if (slots.remove(slot) != null) {
            markDirty();
        }
    }

    /** A copy, in the order the slots were saved. */
    public Map<String, String> getSlots() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(slots));
    }
}
